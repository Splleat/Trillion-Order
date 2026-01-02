package com.nhnacademy.order.ordersaga.cancellation.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.client.coupon.service.CouponService;
import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.exception.OrderCancelFailureException;
import com.nhnacademy.order.order.service.OrderFinalizerCancelService;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.ordersaga.cancellation.domain.CancelSagaStep;
import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCancelOrchestratorTest {

    @Mock
    private SagaUpdateService sagaUpdateService;
    @Mock
    private MemberService memberService;
    @Mock
    private CouponService couponService;
    @Mock
    private BookService bookService;
    @Mock
    private PaymentFlowService paymentFlowService;
    @Mock
    private OrderFinalizerCancelService orderFinalizerCancelService;

    @InjectMocks
    private OrderCancelOrchestrator orderCancelOrchestrator;

    @DisplayName("결제 미완료 주문 취소 시도 시 예외 발생")
    @Test
    void processCancelOrder_Fail_PaymentNotCompleted() {
        // given
        Order order = createOrder(OrderStatus.PENDING);

        // when & then
        assertThatThrownBy(() -> orderCancelOrchestrator.processCancelOrder(1L, order))
                .isInstanceOf(OrderCancelFailureException.class)
                .hasMessageContaining("결제가 완료되지 않은 주문");
    }

    @DisplayName("주문 취소 성공 - 모든 단계 정상 처리")
    @Test
    void processCancelOrder_Success() {
        // given
        Long memberId = 1L;
        Order order = createOrder(OrderStatus.COMPLETED);
        
        // OrderItem 추가 (재고 복구 테스트용)
        OrderItem orderItem = createOrderItem(order);
        order.addOrderItem(orderItem);

        // OrderCoupon 추가 (쿠폰 복구 테스트용)
        OrderCoupon orderCoupon = createOrderCoupon(order, 100L);
        order.addOrderCoupon(orderCoupon);

        // Saga 생성
        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), order.getOrderId());

        given(orderFinalizerCancelService.cancelStart(order)).willReturn(saga);

        // when
        orderCancelOrchestrator.processCancelOrder(memberId, order);

        // then
        // 1. 결제 취소 호출 검증
        verify(paymentFlowService).cancelPaymentByMember(eq(order.getOrderNumber()), any(), eq(null), any());
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.PAYMENT_CANCELED);

        // 2. 포인트 환불 호출 검증 (pointUsage > 0 이므로)
        verify(memberService).increasePoint(any(), eq(memberId), eq(order.getOrderId()), eq(1000));
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.POINT_REFUNDED);

        // 3. 쿠폰 반환 호출 검증
        verify(couponService).withdrawCoupon(100L, memberId);
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.COUPON_RESTORED);

        // 4. 재고 증가 호출 검증
        verify(bookService).increaseStocks(any(), anyMap());
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.STOCK_INCREASED);

        // 5. 완료 처리 검증
        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.COMPLETED);
        verify(orderFinalizerCancelService).cancelOrder(order, saga);
    }

    @DisplayName("주문 취소 실패 - 예외 발생 시 사가 실패 처리")
    @Test
    void processCancelOrder_Fail_Exception() {
        // given
        Long memberId = 1L;
        Order order = createOrder(OrderStatus.COMPLETED);
        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), order.getOrderId());

        given(orderFinalizerCancelService.cancelStart(order)).willReturn(saga);
        // 결제 취소 중 예외 발생
        doThrow(new RuntimeException("Payment Error")).when(paymentFlowService).cancelPaymentByMember(any(), any(), any(), any());

        // when & then
        assertThatThrownBy(() -> orderCancelOrchestrator.processCancelOrder(memberId, order))
                .isInstanceOf(OrderCancelFailureException.class);
        
        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.FAILED);
    }
    
    @DisplayName("재시도 - 이미 완료된 단계는 건너뛰고 실행")
    @Test
    void retry_Success_SkipCompletedSteps() {
        // given
        Long memberId = 1L;
        Order order = createOrder(OrderStatus.CANCELING);
        
        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), order.getOrderId());
        // 결제 취소, 포인트 환불까지 완료된 상태로 설정
        saga.setLastCompletedStep(CancelSagaStep.POINT_REFUNDED);

        // OrderCoupon 추가 (쿠폰 복구 필요)
        OrderCoupon orderCoupon = createOrderCoupon(order, 100L);
        order.addOrderCoupon(orderCoupon);

        // when
        orderCancelOrchestrator.retry(saga, order);

        // then
        // 이미 완료된 단계는 호출되지 않아야 함
        verify(paymentFlowService, never()).cancelPaymentByMember(any(), any(), any(), any());
        verify(memberService, never()).increasePoint(any(), anyLong(), anyLong(), anyInt());

        // 남은 단계 실행 검증 (쿠폰, 재고)
        verify(couponService).withdrawCoupon(100L, memberId);
        verify(bookService).increaseStocks(any(), anyMap());
        
        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.COMPLETED);
    }

    @DisplayName("이미 결제 취소된 주문 - 예외 무시하고 진행")
    @Test
    void processCancelOrder_AlreadyPaymentCanceled_Proceeds() {
        // given
        Long memberId = 1L;
        Order order = createOrder(OrderStatus.COMPLETED);
        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), order.getOrderId());

        given(orderFinalizerCancelService.cancelStart(order)).willReturn(saga);
        
        // 결제 취소 시 "이미 전액 취소된 결제" 메시지를 가진 예외 발생
        doThrow(new RuntimeException("이미 전액 취소된 결제")).when(paymentFlowService).cancelPaymentByMember(any(), any(), any(), any());

        // when
        orderCancelOrchestrator.processCancelOrder(memberId, order);

        // then
        // 예외가 발생했어도 다음 단계(포인트, 쿠폰, 재고)가 진행되어야 함
        verify(sagaUpdateService).updateCancelSagaStep(saga, CancelSagaStep.PAYMENT_CANCELED);
        verify(bookService).increaseStocks(any(), anyMap());
        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.COMPLETED);
    }

    @DisplayName("포인트/쿠폰 미사용 주문 - 해당 단계 건너뜀")
    @Test
    void processCancelOrder_NoPointNoCoupon_SkipsSteps() {
        // given
        Long memberId = 1L;
        // 포인트 사용 0인 주문 생성
        OrderDetails details = OrderDetails.createInitial("12345", LocalDateTime.now(), 0);
        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderId", 1L);
        ReflectionTestUtils.setField(order, "orderNumber", "ORD-1234");
        ReflectionTestUtils.setField(order, "memberId", memberId);
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "orderDetails", details);
        ReflectionTestUtils.setField(order, "orderItems", new HashSet<>()); // 아이템 없음 (재고 증가는 호출되지만 맵이 비어있음)
        ReflectionTestUtils.setField(order, "orderCoupons", new HashSet<>());

        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), order.getOrderId());
        given(orderFinalizerCancelService.cancelStart(order)).willReturn(saga);

        // when
        orderCancelOrchestrator.processCancelOrder(memberId, order);

        // then
        verify(memberService, never()).increasePoint(any(), anyLong(), anyLong(), anyInt());
        verify(couponService, never()).withdrawCoupon(anyLong(), anyLong());
        
        // 재고 증가는 항상 호출됨 (빈 맵이라도)
        verify(bookService).increaseStocks(any(), anyMap());
        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.COMPLETED);
    }

    @DisplayName("비회원 주문 - 회원 관련 단계 건너뜀")
    @Test
    void processCancelOrder_GuestOrder_SkipsMemberSteps() {
        // given
        Long memberId = null;
        Order order = createOrder(OrderStatus.COMPLETED); // 포인트 사용 1000이지만 비회원이면 무시되어야 함
        
        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), order.getOrderId());
        given(orderFinalizerCancelService.cancelStart(order)).willReturn(saga);

        // when
        orderCancelOrchestrator.processCancelOrder(memberId, order);

        // then
        verify(memberService, never()).increasePoint(any(), any(), any(), anyInt());
        verify(couponService, never()).withdrawCoupon(anyLong(), any());
        
        verify(sagaUpdateService).updateCancelSagaStatus(saga, SagaStatus.COMPLETED);
    }

    // --- Helper Methods ---
    private Order createOrder(OrderStatus status) {
        OrderDetails details = OrderDetails.createInitial("12345", LocalDateTime.now(), 1000); // pointUsage=1000
        
        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderId", 1L);
        ReflectionTestUtils.setField(order, "orderNumber", "ORD-1234");
        ReflectionTestUtils.setField(order, "memberId", 1L);
        ReflectionTestUtils.setField(order, "orderStatus", status);
        ReflectionTestUtils.setField(order, "orderDetails", details);
        ReflectionTestUtils.setField(order, "orderItems", new HashSet<>());
        ReflectionTestUtils.setField(order, "orderCoupons", new HashSet<>());
        
        return order;
    }
    
    private OrderItem createOrderItem(Order order) {
        // OrderItem 생성자가 private이므로 Reflection 사용하거나 빌더가 있다면 빌더 사용
        // 여기서는 빌더가 없으므로 Reflection으로 설정하거나, 테스트용 팩토리 메서드 활용
        // 도메인 코드에 @AllArgsConstructor가 있으므로 빌더 패턴이 있을 가능성이 높음 (Lombok @Builder 확인 못했으나 보통 같이 씀)
        // 하지만 코드에 @Builder가 안 보였으므로 리플렉션 사용
        
        OrderItem item = new OrderItem();
        ReflectionTestUtils.setField(item, "bookId", 10L);
        ReflectionTestUtils.setField(item, "quantity", 2);
        ReflectionTestUtils.setField(item, "order", order);
        return item;
    }
    
    private OrderCoupon createOrderCoupon(Order order, Long couponId) {
        // OrderCoupon 생성자가 private
        try {
            Constructor<OrderCoupon> constructor = OrderCoupon.class.getDeclaredConstructor(Order.class, Long.class, int.class, Long.class);
            constructor.setAccessible(true);
            return constructor.newInstance(order, couponId, 1000, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}