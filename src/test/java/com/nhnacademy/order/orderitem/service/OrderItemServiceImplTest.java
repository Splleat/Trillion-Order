package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.ordersaga.itemrefund.service.NonMemberOrderItemRefundOrchestrator;
import com.nhnacademy.order.ordersaga.itemrefund.service.OrderItemRefundOrchestrator;
import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.service.PointAccumulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderItemServiceImplTest {

    @InjectMocks
    private OrderItemServiceImpl orderItemServiceImpl;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderItemRefundOrchestrator orderItemRefundOrchestrator;

    @Mock
    private NonMemberOrderItemRefundOrchestrator nonMemberOrderItemRefundOrchestrator;

    @Mock
    private OrderItemUpdateService orderItemUpdateService;

    @Mock
    private OrderItemConfirmService orderItemConfirmService;

    @Mock
    private PointAccumulationService pointAccumulationService;

    @Test
    @DisplayName("상태 변경(CONFIRMED) - 회원: 구매 확정 서비스 호출 및 포인트 적립 시도")
    void updateOrderItemStatus_Confirmed_ForMember() {
        // given
        UserInfo memberInfo = new UserInfo(1L, null, "MEMBER");
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.CONFIRMED;
        
        // confirmOrderItem이 이벤트를 반환하도록 설정
        PointAccumulationEvent mockEvent = mock(PointAccumulationEvent.class);
        when(orderItemConfirmService.confirmOrderItem(orderItem)).thenReturn(mockEvent);

        // when
        orderItemServiceImpl.updateOrderItemStatus(memberInfo, order, orderItem, status);

        // then
        // 1. OrderItemConfirmService가 호출되어야 함
        verify(orderItemConfirmService, times(1)).confirmOrderItem(orderItem);
        // 2. 이벤트가 반환되었으므로 PointAccumulationService.processEvent가 호출되어야 함
        verify(pointAccumulationService, times(1)).processEvent(mockEvent);
        
        // 3. 일반 UpdateService나 환불 로직은 호출되지 않아야 함
        verify(orderItemUpdateService, never()).updateOrderItemStatus(any(), any());
        verify(orderItemRefundOrchestrator, never()).processItemRefund(any(), any(), any());
    }
    
    @Test
    @DisplayName("상태 변경(CONFIRMED) - 회원: 포인트 적립 동기 호출 실패 시 예외를 삼키고 성공 처리")
    void updateOrderItemStatus_Confirmed_ProcessEventFailure() {
        // given
        UserInfo memberInfo = new UserInfo(1L, null, "MEMBER");
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.CONFIRMED;

        PointAccumulationEvent mockEvent = mock(PointAccumulationEvent.class);
        when(orderItemConfirmService.confirmOrderItem(orderItem)).thenReturn(mockEvent);
        
        // processEvent가 예외를 던지도록 설정
        doThrow(new RuntimeException("API Error")).when(pointAccumulationService).processEvent(mockEvent);

        // when
        // 예외가 발생하지 않아야 함 (try-catch로 잡았으므로)
        orderItemServiceImpl.updateOrderItemStatus(memberInfo, order, orderItem, status);

        // then
        verify(orderItemConfirmService, times(1)).confirmOrderItem(orderItem);
        verify(pointAccumulationService, times(1)).processEvent(mockEvent);
    }

    @Test
    @DisplayName("상태 변경(CONFIRMED) - 비회원: 구매 확정 서비스 호출, 포인트 적립은 안 함")
    void updateOrderItemStatus_Confirmed_ForNonMember() {
        // given
        UserInfo nonMemberInfo = null; // 비회원
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.CONFIRMED;

        // confirmOrderItem이 null을 반환하도록 설정 (비회원인 경우)
        when(orderItemConfirmService.confirmOrderItem(orderItem)).thenReturn(null);

        // when
        orderItemServiceImpl.updateOrderItemStatus(nonMemberInfo, order, orderItem, status);

        // then
        verify(orderItemConfirmService, times(1)).confirmOrderItem(orderItem);
        // null이 반환되었으므로 processEvent는 호출되지 않아야 함
        verify(pointAccumulationService, never()).processEvent(any());
    }

    @Test
    @DisplayName("상태 변경(RETURNED) - 회원: 환불 오케스트레이터 호출")
    void updateOrderItemStatus_Returned_ForMember() {
        // given
        UserInfo memberInfo = new UserInfo(1L, null, "ADMIN");
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.RETURNED;

        // when
        orderItemServiceImpl.updateOrderItemStatus(memberInfo, order, orderItem, status);

        // then
        verify(orderItemRefundOrchestrator, times(1)).processItemRefund(memberInfo.userId(), order, orderItem);
        
        verify(orderItemConfirmService, never()).confirmOrderItem(any());
        verify(pointAccumulationService, never()).processEvent(any());
    }

    @Test
    @DisplayName("상태 변경(SHIPPED) - 기본: 상태 변경 서비스 호출")
    void updateOrderItemStatus_Default_Shipped() {
        // given
        UserInfo memberInfo = new UserInfo(1L, null, "ADMIN");
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.SHIPPED;

        // when
        orderItemServiceImpl.updateOrderItemStatus(memberInfo, order, orderItem, status);

        // then
        verify(orderItemUpdateService, times(1)).updateOrderItemStatus(eq(orderItem), any(OrderItemStatusUpdateStrategy.class));
        
        verify(orderItemConfirmService, never()).confirmOrderItem(any());
        verify(orderItemRefundOrchestrator, never()).processItemRefund(any(), any(), any());
    }
}