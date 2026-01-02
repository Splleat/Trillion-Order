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

    @Test
    @DisplayName("processItemRefund: 성공적인 환불 처리 (파손, 배송비 차감 없음)")
    void processItemRefund_Success_Damaged() {
        // given
        long memberId = 1L;
        long orderId = 100L;
        
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(orderId);
        
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_DAMAGED);
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
        // 파손이므로 배송비 차감 없음 (15000원 환불)
        verify(memberService).increasePoint(eq(capturedSaga.getSagaId()), eq(memberId), eq(orderId), eq(15000));
        
        verify(sagaUpdateService).updateItemRefundSagaStatus(eq(capturedSaga), eq(SagaStatus.COMPLETED));
        verify(orderItem).setRefundPrice(15000);
    }

    @Test
    @DisplayName("retry: 포인트 환불 완료 후 중단된 사가 재개 성공")
    void retry_Success_ResumeFromPointRefunded() {
        // given
        long memberId = 1L;
        long orderId = 100L;
        
        Order order = mock(Order.class);
        when(order.getMemberId()).thenReturn(memberId);
        
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        when(orderItem.getQuantity()).thenReturn(1);
        when(orderItem.getPrice()).thenReturn(15000);
        when(orderItem.getCouponDiscountAmount()).thenReturn(0);
        when(orderItem.getBookId()).thenReturn(101L);
        
        // 배송 정책 설정
        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 20000);
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(deliveryPolicy));
        
        // 사가 상태 설정 (포인트 환불까지 완료됨)
        java.util.UUID sagaId = java.util.UUID.randomUUID();
        OrderItemRefundSaga saga = OrderItemRefundSaga.create(sagaId, orderId, 200L);
        saga.setLastCompletedStep(ItemRefundSagaStep.POINT_REFUNDED);

        // when
        orderItemRefundOrchestrator.retry(saga, order, orderItem);

        // then
        // 포인트 환불은 이미 완료되었으므로 다시 호출되지 않아야 함
        verify(memberService, never()).increasePoint(any(), anyLong(), anyLong(), anyInt());
        
        // 재고 증가는 실행되어야 함
        verify(bookService).increaseStocks(eq(sagaId), anyMap());
        verify(sagaUpdateService).updateItemRefundSagaStep(eq(saga), eq(ItemRefundSagaStep.STOCK_INCREASED));
        
        verify(sagaUpdateService).updateItemRefundSagaStatus(eq(saga), eq(SagaStatus.COMPLETED));
        verify(orderItemRefundService).completeOrderItem(eq(orderItem), eq(saga));
    }

    @Test
    @DisplayName("retry: 재시도 중 예외 발생 시 사가 실패 처리")
    void retry_Failure() {
        // given
        long memberId = 1L;
        long orderId = 100L;
        
        Order order = mock(Order.class);
        when(order.getMemberId()).thenReturn(memberId);
        when(order.getOrderId()).thenReturn(orderId); // required for increasePoint
        
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        
        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 20000);
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(deliveryPolicy));
        
        java.util.UUID sagaId = java.util.UUID.randomUUID();
        OrderItemRefundSaga saga = OrderItemRefundSaga.create(sagaId, orderId, 200L);
        // 초기 상태
        
        // 포인트 환불 중 예외 발생
        doThrow(new RuntimeException("Error")).when(memberService).increasePoint(any(), anyLong(), anyLong(), anyInt());

        // when
        orderItemRefundOrchestrator.retry(saga, order, orderItem);

        // then
        verify(sagaUpdateService).updateItemRefundSagaStatus(eq(saga), eq(SagaStatus.FAILED));
    }

    @Test
    @DisplayName("retry: 이미 완료된 사가는 아무 작업도 하지 않음")
    void retry_AlreadyCompleted() {
        // given
        OrderItemRefundSaga saga = OrderItemRefundSaga.create(java.util.UUID.randomUUID(), 100L, 200L);
        // Reflection을 사용하여 overallStatus를 COMPLETED로 설정 (setter가 없다면)
        // OrderItemRefundSaga 코드를 보지 못했으나, 보통 updateStatus 메서드를 통해 변경됨.
        // 여기서는 mock을 쓰거나 reflection 사용.
        // 하지만 OrderItemRefundSaga.create는 PENDING 상태일 것임.
        // SagaStatus enum은 COMPLETED 값을 가짐.
        // 만약 setter가 없다면 reflection 사용.
        org.springframework.test.util.ReflectionTestUtils.setField(saga, "overallStatus", SagaStatus.COMPLETED);

        // when
        orderItemRefundOrchestrator.retry(saga, null, null);

        // then
        verifyNoInteractions(memberService);
        verifyNoInteractions(bookService);
        verifyNoInteractions(sagaUpdateService);
    }
}
