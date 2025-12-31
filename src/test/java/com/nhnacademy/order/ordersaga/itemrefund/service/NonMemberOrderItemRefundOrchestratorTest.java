package com.nhnacademy.order.ordersaga.itemrefund.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.service.OrderItemRefundService;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberOrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberRefundSagaStep;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NonMemberOrderItemRefundOrchestratorTest {

    @Mock
    private SagaUpdateService sagaUpdateService;
    @Mock
    private BookService bookService;
    @Mock
    private PaymentFlowService paymentFlowService;
    @Mock
    private DeliveryPolicyRepository deliveryPolicyRepository;
    @Mock
    private OrderItemRefundService orderItemRefundService;

    @InjectMocks
    private NonMemberOrderItemRefundOrchestrator orchestrator;

    @DisplayName("비회원 반품 처리 성공")
    @Test
    void processNonMemberItemRefund_Success() {
        // given
        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber", "ORD-1234");
        ReflectionTestUtils.setField(order, "orderId", 1L);

        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "orderItemId", 1L);
        ReflectionTestUtils.setField(orderItem, "bookId", 10L);
        ReflectionTestUtils.setField(orderItem, "quantity", 1);
        ReflectionTestUtils.setField(orderItem, "price", 10000);
        ReflectionTestUtils.setField(orderItem, "couponDiscountAmount", Integer.valueOf(0)); 
        ReflectionTestUtils.setField(orderItem, "orderItemStatus", OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        ReflectionTestUtils.setField(orderItem, "order", order);

        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 50000);
        given(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).willReturn(Optional.of(deliveryPolicy));

        // when
        orchestrator.processNonMemberItemRefund(order, orderItem);

        // then
        // 1. 결제 부분 취소 검증 (단순 변심이므로 배송비 차감: 10000 - 3000 = 7000)
        verify(paymentFlowService).cancelPaymentByMember(eq("ORD-1234"), anyString(), eq(7000), any());
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStep(any(), eq(NonMemberRefundSagaStep.PAYMENT_REFUNDED));

        // 2. 재고 증가 검증
        verify(bookService).increaseStocks(any(), anyMap());
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStep(any(), eq(NonMemberRefundSagaStep.STOCK_INCREASED));

        // 3. 완료 검증
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStatus(any(), eq(SagaStatus.COMPLETED));
        verify(orderItemRefundService).completeNonMemberOrderItem(eq(orderItem), any());
    }

    @DisplayName("반품 요청 상태가 아닌 경우 예외 발생")
    @Test
    void processNonMemberItemRefund_Fail_InvalidStatus() {
        // given
        Order order = new Order();
        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "orderItemId", 1L);
        ReflectionTestUtils.setField(orderItem, "orderItemStatus", OrderItemStatus.DELIVERED); // 반품 요청 상태 아님

        // when & then
        assertThatThrownBy(() -> orchestrator.processNonMemberItemRefund(order, orderItem))
                .isInstanceOf(OrderStatusTransitionException.class);
    }

    @DisplayName("재시도 - 결제 취소 후 실패한 경우 재고 증가부터 재시도")
    @Test
    void retry_Success_SkipPaymentRefund() {
        // given
        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber", "ORD-1234");
        ReflectionTestUtils.setField(order, "orderId", 1L);

        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "orderItemId", 1L);
        ReflectionTestUtils.setField(orderItem, "bookId", 10L);
        ReflectionTestUtils.setField(orderItem, "quantity", 1);
        ReflectionTestUtils.setField(orderItem, "price", 10000);
        ReflectionTestUtils.setField(orderItem, "couponDiscountAmount", Integer.valueOf(0));
        ReflectionTestUtils.setField(orderItem, "orderItemStatus", OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        ReflectionTestUtils.setField(orderItem, "order", order);

        // Saga 생성 및 상태 설정 (PAYMENT_REFUNDED까지 완료)
        NonMemberOrderItemRefundSaga saga = NonMemberOrderItemRefundSaga.create(UUID.randomUUID(), 1L, 1L);
        saga.setLastCompletedStep(NonMemberRefundSagaStep.PAYMENT_REFUNDED);

        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 50000);
        given(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).willReturn(Optional.of(deliveryPolicy));

        // when
        orchestrator.retry(saga, orderItem);

        // then
        // 결제 취소는 호출되지 않아야 함
        verify(paymentFlowService, never()).cancelPaymentByMember(any(), any(), anyInt(), any());

        // 재고 증가는 호출되어야 함
        verify(bookService).increaseStocks(any(), anyMap());
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.STOCK_INCREASED);

        // 완료 처리
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStatus(saga, SagaStatus.COMPLETED);
        verify(orderItemRefundService).completeNonMemberOrderItem(orderItem, saga);
    }

    @DisplayName("재시도 - 실패 시 사가 상태 FAILED 처리")
    @Test
    void retry_Fail_Exception() {
        // given
        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber", "ORD-1234");
        ReflectionTestUtils.setField(order, "orderId", 1L);

        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "orderItemId", 1L);
        ReflectionTestUtils.setField(orderItem, "bookId", 10L);
        ReflectionTestUtils.setField(orderItem, "quantity", 1);
        ReflectionTestUtils.setField(orderItem, "price", 10000);
        ReflectionTestUtils.setField(orderItem, "couponDiscountAmount", Integer.valueOf(0));
        ReflectionTestUtils.setField(orderItem, "orderItemStatus", OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        ReflectionTestUtils.setField(orderItem, "order", order);

        NonMemberOrderItemRefundSaga saga = NonMemberOrderItemRefundSaga.create(UUID.randomUUID(), 1L, 1L);
        // 초기 상태

        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 50000);
        given(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).willReturn(Optional.of(deliveryPolicy));

        // 결제 취소 중 에러 발생 유도
        doThrow(new RuntimeException("Payment Error")).when(paymentFlowService).cancelPaymentByMember(any(), any(), anyInt(), any());

        // when
        orchestrator.retry(saga, orderItem);

        // then
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStatus(saga, SagaStatus.FAILED);
    }
}