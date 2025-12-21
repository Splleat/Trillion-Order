package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.ordersaga.itemrefund.service.NonMemberOrderItemRefundOrchestrator;
import com.nhnacademy.order.ordersaga.itemrefund.service.OrderItemRefundOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderItemServiceImplTest {

    @InjectMocks
    private OrderItemServiceImpl orderItemServiceImpl;

    @Mock
    private OrderItemRefundOrchestrator orderItemRefundOrchestrator;

    @Mock
    private OrderItemUpdateService orderItemUpdateService;

    @Test
    @DisplayName("상태 변경(CONFIRMED) - 회원: 포인트 적립 및 상태 변경 호출")
    void updateOrderItemStatus_Confirmed_ForMember() {
        // given
        UserInfo memberInfo = new UserInfo(1L, null, "MEMBER");
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.CONFIRMED;

        // when
        orderItemServiceImpl.updateOrderItemStatus(memberInfo, order, orderItem, status);

        // then
        // 1. 포인트 적립이 호출되어야 함
        verify(orderItemUpdateService, times(1)).accumulatePoint(order, orderItem);
        // 2. 기본 상태 변경이 호출되어야 함
        verify(orderItemUpdateService, times(1)).updateOrderItemStatus(eq(order), eq(orderItem), any(OrderItemStatusUpdateStrategy.class));
        // 3. 환불 로직은 호출되지 않아야 함
        verify(orderItemRefundOrchestrator, never()).processItemRefund(any(), any(), any());
    }

    @Test
    @DisplayName("상태 변경(CONFIRMED) - 비회원: 포인트 적립 없이 상태 변경만 호출")
    void updateOrderItemStatus_Confirmed_ForNonMember() {
        // given
        UserInfo nonMemberInfo = null; // 비회원
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.CONFIRMED;

        // when
        orderItemServiceImpl.updateOrderItemStatus(nonMemberInfo, order, orderItem, status);

        // then
        // 1. 비회원이므로 포인트 적립은 호출되지 않아야 함
        verify(orderItemUpdateService, never()).accumulatePoint(any(), any());
        // 2. 기본 상태 변경만 호출되어야 함
        verify(orderItemUpdateService, times(1)).updateOrderItemStatus(eq(order), eq(orderItem), any(OrderItemStatusUpdateStrategy.class));
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
        // 1. 회원 환불 오케스트레이터가 호출되어야 함
        verify(orderItemRefundOrchestrator, times(1)).processItemRefund(memberInfo.userId(), order, orderItem);
        // 2. 포인트 적립 및 기본 상태 변경은 호출되지 않아야 함
        verify(orderItemUpdateService, never()).accumulatePoint(any(), any());
        verify(orderItemUpdateService, never()).updateOrderItemStatus(any(), any(), any());
    }

    @Test
    @DisplayName("상태 변경(SHIPPED) - 기본: 상태 변경만 호출")
    void updateOrderItemStatus_Default_Shipped() {
        // given
        UserInfo memberInfo = new UserInfo(1L, null, "ADMIN");
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderItemStatus status = OrderItemStatus.SHIPPED;

        // when
        orderItemServiceImpl.updateOrderItemStatus(memberInfo, order, orderItem, status);

        // then
        // 1. 기본 상태 변경만 호출되어야 함
        verify(orderItemUpdateService, times(1)).updateOrderItemStatus(eq(order), eq(orderItem), any(OrderItemStatusUpdateStrategy.class));
        // 2. 포인트 적립 및 환불 로직은 호출되지 않아야 함
        verify(orderItemUpdateService, never()).accumulatePoint(any(), any());
        verify(orderItemRefundOrchestrator, never()).processItemRefund(any(), any(), any());
    }
}
