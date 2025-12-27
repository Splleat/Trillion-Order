package com.nhnacademy.order.ordersaga.itemrefund.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.exception.OrderItemRefundFailureException;
import com.nhnacademy.order.orderitem.service.OrderItemRefundService;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.ItemRefundSagaStep;
import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderItemRefundOrchestratorTest {

    @InjectMocks
    private OrderItemRefundOrchestrator orderItemRefundOrchestrator;

    @Mock private SagaUpdateService sagaUpdateService;
    @Mock private MemberService memberService;
    @Mock private BookService bookService;
    @Mock private DeliveryPolicyRepository deliveryPolicyRepository;
    @Mock private OrderItemRefundService orderItemRefundService;

    @Test
    @DisplayName("processItemRefund: 성공적인 환불 처리 (단순 변심, 회원)")
    void processItemRefund_Success_ChangeOfMindMember() {
        // given
        long memberId = 1L;
        long orderId = 100L;
        
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(orderId);
        
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        when(orderItem.getQuantity()).thenReturn(1);
        when(orderItem.getPrice()).thenReturn(15000);
        when(orderItem.getCouponDiscountAmount()).thenReturn(0);
        when(orderItem.getBookId()).thenReturn(101L);
        
        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 20000);
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(deliveryPolicy));

        // when
        orderItemRefundOrchestrator.processItemRefund(memberId, order, orderItem);

        // then
        ArgumentCaptor<OrderItemRefundSaga> sagaCaptor = ArgumentCaptor.forClass(OrderItemRefundSaga.class);
        verify(sagaUpdateService).updateItemRefundSagaStep(sagaCaptor.capture(), eq(ItemRefundSagaStep.STARTED));
        
        OrderItemRefundSaga capturedSaga = sagaCaptor.getValue();
        verify(memberService).increasePoint(eq(capturedSaga.getSagaId()), eq(memberId), eq(orderId), eq(12000));
        verify(sagaUpdateService).updateItemRefundSagaStep(eq(capturedSaga), eq(ItemRefundSagaStep.POINT_REFUNDED));
        
        verify(bookService).increaseStocks(eq(capturedSaga.getSagaId()), anyMap());
        verify(sagaUpdateService).updateItemRefundSagaStep(eq(capturedSaga), eq(ItemRefundSagaStep.STOCK_INCREASED));
        
        verify(sagaUpdateService).updateItemRefundSagaStatus(eq(capturedSaga), eq(SagaStatus.COMPLETED));
        verify(orderItem).setRefundPrice(12000);
        verify(orderItemRefundService).completeOrderItem(eq(orderItem), eq(capturedSaga));
    }

    @Test
    @DisplayName("processItemRefund: 잘못된 OrderItem 상태일 때 OrderStatusTransitionException 발생")
    void processItemRefund_ThrowsOrderStatusTransitionException_WhenInvalidStatus() {
        // given
        long memberId = 1L;
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemId()).thenReturn(200L);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);

        // when & then
        assertThatThrownBy(() -> orderItemRefundOrchestrator.processItemRefund(memberId, order, orderItem))
            .isInstanceOf(OrderStatusTransitionException.class);
    }

    @Test
    @DisplayName("processItemRefund: 멤버 서비스 실패 시 OrderItemRefundFailureException 발생 및 사가 FAILED")
    void processItemRefund_ThrowsFailureException_WhenMemberServiceFails() {
        // given
        long memberId = 1L;
        long orderId = 100L;
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(orderId);

        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemId()).thenReturn(200L);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_DAMAGED);

        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 20000);
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(deliveryPolicy));
        
        doThrow(new RuntimeException("포인트 환불 실패")).when(memberService).increasePoint(any(), eq(memberId), eq(orderId), anyInt());

        // when & then
        assertThatThrownBy(() -> orderItemRefundOrchestrator.processItemRefund(memberId, order, orderItem))
            .isInstanceOf(OrderItemRefundFailureException.class);

        ArgumentCaptor<OrderItemRefundSaga> sagaCaptor = ArgumentCaptor.forClass(OrderItemRefundSaga.class);
        verify(sagaUpdateService).updateItemRefundSagaStep(sagaCaptor.capture(), eq(ItemRefundSagaStep.STARTED));
        verify(sagaUpdateService).updateItemRefundSagaStatus(sagaCaptor.capture(), eq(SagaStatus.FAILED));
    }
}
