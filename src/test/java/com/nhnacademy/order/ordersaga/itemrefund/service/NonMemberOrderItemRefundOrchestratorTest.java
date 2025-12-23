package com.nhnacademy.order.ordersaga.itemrefund.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.exception.OrderItemRefundFailureException;
import com.nhnacademy.order.orderitem.service.OrderItemRefundService;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberOrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberRefundSagaStep;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NonMemberOrderItemRefundOrchestratorTest {

    @InjectMocks
    private NonMemberOrderItemRefundOrchestrator nonMemberOrderItemRefundOrchestrator;

    @Mock private SagaUpdateService sagaUpdateService;
    @Mock private BookService bookService;
    @Mock private DeliveryPolicyRepository deliveryPolicyRepository;
    @Mock private OrderItemRefundService orderItemRefundService;

    private Order order;
    private OrderItem orderItem;
    private UUID sagaId;

    @BeforeEach
    void setUp() {
        sagaId = UUID.randomUUID();

        // Mock Order
        order = mock(Order.class);

        orderItem = mock(OrderItem.class);

    }

    @Test
    @DisplayName("processNonMemberItemRefund: 비회원 주문 상품 환불 성공 (단순 변심)")
    void processNonMemberItemRefund_Success_ChangeOfMindNonMember() {
        DeliveryPolicy deliveryPolicy = mock(DeliveryPolicy.class);

        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(deliveryPolicy));

        // when
        nonMemberOrderItemRefundOrchestrator.processNonMemberItemRefund(order, orderItem);

        // then
        // Saga 생성 확인 (sagaUpdateService.updateNonMemberItemRefundSagaStep 호출 시 첫 번째 인자로 캡처)
        ArgumentCaptor<NonMemberOrderItemRefundSaga> sagaCaptor = ArgumentCaptor.forClass(NonMemberOrderItemRefundSaga.class);
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStep(sagaCaptor.capture(), eq(NonMemberRefundSagaStep.STARTED));
        NonMemberOrderItemRefundSaga capturedSaga = sagaCaptor.getValue();

        // 결제 환불 스텝 확인 (PaymentService는 TODO이므로 SagaUpdateService 호출만 확인)
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStep(capturedSaga, NonMemberRefundSagaStep.PAYMENT_REFUNDED);

        // 재고 증가
        verify(bookService).increaseStocks(eq(capturedSaga.getSagaId()), anyMap());
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStep(capturedSaga, NonMemberRefundSagaStep.STOCK_INCREASED);

        // 사가 완료 및 도메인 연결 확인
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStatus(capturedSaga, SagaStatus.COMPLETED);
        verify(orderItemRefundService).completeNonMemberOrderItem(orderItem, capturedSaga);
    }

    @Test
    @DisplayName("processNonMemberItemRefund: 잘못된 OrderItem 상태일 때 OrderStatusTransitionException 발생")
    void processNonMemberItemRefund_ThrowsOrderStatusTransitionException_WhenInvalidStatus() {
        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING); // 잘못된 상태

        // when & then
        assertThatThrownBy(() -> nonMemberOrderItemRefundOrchestrator.processNonMemberItemRefund(order, orderItem))
            .isInstanceOf(OrderStatusTransitionException.class)
            .hasMessageContaining("반품 요청 상태가 아닌 상품: " + orderItem.getOrderItemId());

        // 예외 발생 시 다른 서비스 호출 안됨
        verify(sagaUpdateService, never()).updateNonMemberItemRefundSagaStep(any(), any());
        verify(bookService, never()).increaseStocks(any(), anyMap());
        verify(sagaUpdateService, never()).updateNonMemberItemRefundSagaStatus(any(), any());
        verify(orderItemRefundService, never()).completeNonMemberOrderItem(any(), any());
    }

    @Test
    @DisplayName("processNonMemberItemRefund: 도서 서비스 실패 시 OrderItemRefundFailureException 발생 및 사가 FAILED")
    void processNonMemberItemRefund_ThrowsFailureException_WhenBookServiceFails() {
        DeliveryPolicy deliveryPolicy = mock(DeliveryPolicy.class);

        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_DAMAGED); // 파손으로 인한 반품
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(deliveryPolicy));
        doThrow(new RuntimeException("재고 증가 실패")).when(bookService).increaseStocks(any(), anyMap());

        // when & then
        assertThatThrownBy(() -> nonMemberOrderItemRefundOrchestrator.processNonMemberItemRefund(order, orderItem))
            .isInstanceOf(OrderItemRefundFailureException.class)
            .hasMessageContaining("주문 상품 환불 실패: " + orderItem.getOrderItemId());

        // Saga 생성 확인
        ArgumentCaptor<NonMemberOrderItemRefundSaga> sagaCaptor = ArgumentCaptor.forClass(NonMemberOrderItemRefundSaga.class);
        verify(sagaUpdateService).updateNonMemberItemRefundSagaStep(sagaCaptor.capture(), eq(NonMemberRefundSagaStep.STARTED));
        NonMemberOrderItemRefundSaga capturedSaga = sagaCaptor.getValue();

        verify(sagaUpdateService).updateNonMemberItemRefundSagaStatus(capturedSaga, SagaStatus.FAILED); // 사가 실패로 업데이트
        verify(orderItemRefundService, never()).completeNonMemberOrderItem(any(), any()); // 최종 완료 처리 호출 안됨
    }
}
