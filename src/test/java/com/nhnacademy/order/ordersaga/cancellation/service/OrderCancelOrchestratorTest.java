package com.nhnacademy.order.ordersaga.cancellation.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.client.coupon.service.CouponService;
import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.exception.OrderCancelFailureException;
import com.nhnacademy.order.order.service.OrderFinalizerCancelService;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.ordersaga.cancellation.domain.CancelSagaStep;
import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancelOrchestratorTest {

    @InjectMocks
    private OrderCancelOrchestrator orderCancelOrchestrator;

    @Mock private SagaUpdateService sagaUpdateService;
    @Mock private MemberService memberService;
    @Mock private CouponService couponService;
    @Mock private BookService bookService;
    @Mock private PaymentFlowService paymentFlowService;
    @Mock private OrderFinalizerCancelService orderFinalizerCancelService;

    private Long memberId;
    private Order order;
    private OrderCancelSaga saga;
    private UUID sagaId;

    @BeforeEach
    void setUp() {
        memberId = 1L;
        sagaId = UUID.randomUUID();

        // Order mock
        order = mock(Order.class);
        when(order.getOrderId()).thenReturn(100L);
        when(order.getOrderNumber()).thenReturn("ORD-100");

        OrderDetails orderDetails = new OrderDetails(
            LocalDateTime.now(), "12345", LocalDateTime.now().plusDays(3),
            0, 1000, 0, 0, 0
        ); // 포인트 1000 사용
        when(order.getOrderDetails()).thenReturn(orderDetails);

        Set<OrderItem> orderItems = new HashSet<>();
        OrderItem item1 = mock(OrderItem.class);
        when(item1.getBookId()).thenReturn(101L);
        when(item1.getQuantity()).thenReturn(2);
        orderItems.add(item1);
        when(order.getOrderItems()).thenReturn(orderItems);

        Set<OrderCoupon> orderCoupons = new HashSet<>();
        OrderCoupon coupon1 = mock(OrderCoupon.class);
        when(coupon1.getCouponId()).thenReturn(201L);
        orderCoupons.add(coupon1);
        when(order.getOrderCoupons()).thenReturn(orderCoupons);

        // Saga mock
        saga = mock(OrderCancelSaga.class);
        when(saga.getSagaId()).thenReturn(sagaId);

        // orderFinalizerCancelService mock
        when(orderFinalizerCancelService.cancelStart(any(Order.class))).thenReturn(saga);
    }

    @Test
    @DisplayName("processCancelOrder: 성공적인 주문 취소 (회원, 포인트/쿠폰 사용)")
    void processCancelOrder_Success_MemberWithPointsAndCoupons() {
        // given
        // setUp에서 mock 설정 완료

        // when
        orderCancelOrchestrator.processCancelOrder(memberId, order);

        // then
        verify(orderFinalizerCancelService).cancelStart(order); // 사가 시작

        // 결제 취소 확인 (시스템 권한으로 호출되었는지)
        ArgumentCaptor<PaymentUser> userCaptor = ArgumentCaptor.forClass(PaymentUser.class);
        verify(paymentFlowService).cancelPaymentByMember(eq("ORD-100"), anyString(), isNull(), userCaptor.capture());
        PaymentUser capturedUser = userCaptor.getValue();
        assertThat(capturedUser.role()).isEqualTo("ROLE_ADMIN");
        assertThat(capturedUser.memberId()).isEqualTo(memberId);

        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.PAYMENT_CANCELED);

        // 포인트 환불 확인
        verify(memberService).increasePoint(sagaId, memberId, order.getOrderId(), order.getOrderDetails().pointUsage());
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.POINT_REFUNDED);

        // 쿠폰 반환 확인
        verify(couponService).withdrawCoupon(sagaId, memberId, order.getOrderCoupons().iterator().next().getCouponId());
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.COUPON_RESTORED);

        // 재고 증가 확인
        verify(bookService).increaseStocks(eq(sagaId), anyMap());
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.STOCK_INCREASED);

        // 사가 완료 확인
        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.COMPLETED);
        verify(orderFinalizerCancelService).cancelOrder(order, saga); // 최종 주문 취소 처리
    }

    @Test
    @DisplayName("processCancelOrder: OrderCancelFailureException 발생 시 사가 상태 FAILED로 변경")
    void processCancelOrder_ShouldSetSagaStatusToFailed_OnException() {
        // given
        // bookService.increaseStocks 호출 시 예외 발생하도록 설정
        doThrow(new RuntimeException("재고 증가 실패")).when(bookService).increaseStocks(eq(sagaId), anyMap());

        // when & then
        assertThatThrownBy(() -> orderCancelOrchestrator.processCancelOrder(memberId, order))
            .isInstanceOf(OrderCancelFailureException.class)
            .hasMessageContaining("지연이 발생했습니다");

        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.FAILED);
        // 실패 시 finalize는 호출되지 않아야 함
        verify(orderFinalizerCancelService, never()).cancelOrder(any(Order.class), any(OrderCancelSaga.class));
    }
}
