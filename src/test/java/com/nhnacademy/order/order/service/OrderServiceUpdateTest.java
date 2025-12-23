package com.nhnacademy.order.order.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.service.OrderItemService;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceUpdateTest {

    @InjectMocks
    private OrderService orderServiceImpl;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemService orderItemService; // 리팩토링으로 추가된 의존성
    @Mock
    private OrderCancelOrchestrator orderCancelOrchestrator;

    @Test
    @DisplayName("회원 주문 상품 상태 변경 - 성공: OrderItemService에 위임")
    void patchOrderItemStatus_Success_DelegatesToOrderItemService() {
        // given
        long orderId = 1L;
        long orderItemId = 101L;
        UserInfo userInfo = new UserInfo(999L, null, "ADMIN");
        OrderItemStatusPatchRequest request = new OrderItemStatusPatchRequest(OrderItemStatus.CONFIRMED);

        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderDetails orderDetails = mock(OrderDetails.class);

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));
        when(order.findOrderItemInOrder(orderItemId)).thenReturn(orderItem);

        // OrderResponse 생성을 위한 최소한의 stubbing
        when(order.getOrderId()).thenReturn(orderId);
        when(order.getOrderItems()).thenReturn(Collections.emptySet());
        when(order.getOrderDetails()).thenReturn(orderDetails);
        when(order.getOrderDetails().orderDate()).thenReturn(LocalDateTime.now().minusDays(1));

        // doNothing()은 void 메소드에 사용
        doNothing().when(orderItemService).updateOrderItemStatus(userInfo, order, orderItem, request.status());

        // when
        com.nhnacademy.order.order.dto.OrderResponse response = orderServiceImpl.patchOrderItemStatus(userInfo, orderId, orderItemId, request);

        // then
        assertNotNull(response);
        // OrderServiceImpl의 역할은 OrderItemService의 메서드를 호출하는 것
        // 정확한 인자로 1번 호출되었는지 검증
        verify(orderItemService, times(1)).updateOrderItemStatus(userInfo, order, orderItem, request.status());
    }

    @Test
    @DisplayName("주문 전체 취소 - 성공")
    void cancelOrder_Success() {
        // given
        long orderId = 1L;
        UserInfo userInfo = new UserInfo(1L, null, "MEMBER");

        OrderItem item1 = mock(OrderItem.class);
        when(item1.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);
        Order cancelableOrder = mock(Order.class);
        when(cancelableOrder.getOrderItems()).thenReturn(Set.of(item1));

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(cancelableOrder));
        doNothing().when(orderCancelOrchestrator).processCancelOrder(anyLong(), any(Order.class));

        // when
        orderServiceImpl.cancelOrder(userInfo, orderId);

        // then
        verify(orderRepository, times(1)).findOrderWithItemsByOrderId(orderId);
        verify(orderCancelOrchestrator, times(1)).processCancelOrder(userInfo.userId(), cancelableOrder);
    }

    @Test
    @DisplayName("주문 전체 취소 - 실패: 취소 불가능 상태")
    void cancelOrder_Failure_CannotCancel() {
        // given
        long orderId = 1L;
        UserInfo userInfo = new UserInfo(1L, null, "MEMBER");

        OrderItem item1 = mock(OrderItem.class);
        when(item1.getOrderItemStatus()).thenReturn(OrderItemStatus.SHIPPED); // 취소 불가능 상태
        Order uncancelableOrder = mock(Order.class);
        when(uncancelableOrder.getOrderItems()).thenReturn(Set.of(item1));

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(uncancelableOrder));

        // when & then
        assertThrows(OrderStatusTransitionException.class, () -> {
            orderServiceImpl.cancelOrder(userInfo, orderId);
        });

        // 예외가 발생했으므로, 취소 오케스트레이터는 호출되지 않아야 함
        verify(orderCancelOrchestrator, never()).processCancelOrder(any(), any());
    }
}