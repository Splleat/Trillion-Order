package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.domain.OrdererInfo;
import com.nhnacademy.order.order.domain.ReceiverInfo;
import com.nhnacademy.order.order.dto.NonMemberOrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.exception.OrderPasswordMismatchException;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.orderitem.service.OrderItemService;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NonMemberOrderServiceTest {

    @InjectMocks
    private NonMemberOrderService nonMemberOrderService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OrderItemService orderItemService;
    @Mock
    private OrderCancelOrchestrator orderCancelOrchestrator;

    @Test
    @DisplayName("비회원 주문 조회 - 성공")
    void findOrderByOrderNumber_Success() {
        // given
        String orderNumber = "ORD-NON-MEMBER-123";
        String rawPassword = "password1234";
        String encodedPassword = "encoded-password";
        NonMemberOrderBaseResponse dummyResponse = new NonMemberOrderBaseResponse(
                2L, encodedPassword, null, orderNumber, LocalDateTime.now(),
                OrderStatus.PENDING, 27000, 30000, 0, new OrdererInfo("비회원", "010-0000-0000", "test@email.com"),
                new ReceiverInfo("받는사람", "010-1111-2222", "주소")
        );
        when(orderRepository.findNonMemberOrderByOrderNumber(orderNumber)).thenReturn(Optional.of(dummyResponse));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        when(orderItemRepository.findOrderItemByOrder_OrderId(2L)).thenReturn(Collections.emptyList());

        // when
        OrderResponse response = nonMemberOrderService.findOrderByOrderNumber(orderNumber, rawPassword);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderNumber()).isEqualTo(orderNumber);
        verify(orderRepository, times(1)).findNonMemberOrderByOrderNumber(orderNumber);
        verify(passwordEncoder, times(1)).matches(rawPassword, encodedPassword);
        verify(orderItemRepository, times(1)).findOrderItemByOrder_OrderId(2L);
    }

    @Test
    @DisplayName("비회원 주문 조회 - 실패: 비밀번호 불일치")
    void findOrderByOrderNumber_Failure_PasswordMismatch() {
        // given
        String orderNumber = "ORD-NON-MEMBER-123";
        String wrongPassword = "wrong-password";
        String encodedPassword = "encoded-password";
        NonMemberOrderBaseResponse dummyResponse = new NonMemberOrderBaseResponse(
                2L, encodedPassword, null, orderNumber, LocalDateTime.now(),
                OrderStatus.PENDING, 27000, 30000, 0, new OrdererInfo("비회원", "010-0000-0000", "test@email.com"),
                new ReceiverInfo("받는사람", "010-1111-2222", "주소")
        );
        when(orderRepository.findNonMemberOrderByOrderNumber(orderNumber)).thenReturn(Optional.of(dummyResponse));
        when(passwordEncoder.matches(wrongPassword, encodedPassword)).thenReturn(false);

        // when & then
        assertThrows(OrderPasswordMismatchException.class, () -> nonMemberOrderService.findOrderByOrderNumber(orderNumber, wrongPassword));
        verify(orderRepository, times(1)).findNonMemberOrderByOrderNumber(orderNumber);
        verify(passwordEncoder, times(1)).matches(wrongPassword, encodedPassword);
        verify(orderItemRepository, never()).findOrderItemByOrder_OrderId(anyLong());
    }

    @Test
    @DisplayName("비회원 주문 조회 - 실패: 존재하지 않는 주문")
    void findOrderByOrderNumber_Failure_OrderNotFound() {
        // given
        String orderNumber = "ORD-NOT-FOUND";
        String rawPassword = "password1234";
        when(orderRepository.findNonMemberOrderByOrderNumber(orderNumber)).thenReturn(Optional.empty());

        // when & then
        assertThrows(OrderNotFoundException.class, () -> nonMemberOrderService.findOrderByOrderNumber(orderNumber, rawPassword));
        verify(orderRepository, times(1)).findNonMemberOrderByOrderNumber(orderNumber);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }


    @Test
    @DisplayName("비회원 주문 상품 상태 변경 - 성공")
    void patchOrderItemStatusForNonMember_Success() {
        // given
        long orderId = 2L;
        long orderItemId = 201L;
        String rawPassword = "password1234";
        String encodedPassword = "encoded-password";
        NonMemberOrderItemStatusPatchRequest request = new NonMemberOrderItemStatusPatchRequest(rawPassword, OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);

        Order nonMemberOrder = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);
        OrderDetails orderDetails = mock(OrderDetails.class);

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(nonMemberOrder));
        when(nonMemberOrder.getNonMemberPassword()).thenReturn(encodedPassword);
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        when(nonMemberOrder.findOrderItemInOrder(orderItemId)).thenReturn(orderItem);

        when(nonMemberOrder.getOrderId()).thenReturn(orderId);
        when(nonMemberOrder.getOrderItems()).thenReturn(Collections.emptySet());
        when(nonMemberOrder.getOrderDetails()).thenReturn(orderDetails);
        when(nonMemberOrder.getOrderDetails().orderDate()).thenReturn(LocalDateTime.now().minusDays(1));


        doNothing().when(orderItemService).updateOrderItemStatus(null, nonMemberOrder, orderItem, request.status());

        // when
        OrderResponse response = nonMemberOrderService.patchOrderItemStatusForNonMember(orderId, orderItemId, request);

        // then
        assertNotNull(response);
        verify(passwordEncoder, times(1)).matches(rawPassword, encodedPassword);
        verify(orderItemService, times(1)).updateOrderItemStatus(null, nonMemberOrder, orderItem, request.status());
    }

    @Test
    @DisplayName("비회원 주문 취소 - 성공")
    void cancelOrderForNonMember_Success() {
        // given
        long orderId = 3L;
        String rawPassword = "password1234";
        String encodedPassword = "encoded-password";

        OrderItem item1 = mock(OrderItem.class);
        when(item1.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);
        Order cancelableOrder = mock(Order.class);
        when(cancelableOrder.getOrderItems()).thenReturn(Set.of(item1));
        when(cancelableOrder.getNonMemberPassword()).thenReturn(encodedPassword);

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(cancelableOrder));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        doNothing().when(orderCancelOrchestrator).processCancelOrder(null, cancelableOrder);

        // when
        nonMemberOrderService.cancelOrderForNonMember(orderId, rawPassword);

        // then
        verify(orderRepository, times(1)).findOrderWithItemsByOrderId(orderId);
        verify(passwordEncoder, times(1)).matches(rawPassword, encodedPassword);
        verify(orderCancelOrchestrator, times(1)).processCancelOrder(null, cancelableOrder);
    }

    @Test
    @DisplayName("비회원 주문 취소 - 실패: 취소 불가능 상태")
    void cancelOrderForNonMember_Failure_CannotCancel() {
        // given
        long orderId = 3L;
        String rawPassword = "password1234";
        String encodedPassword = "encoded-password";

        OrderItem item1 = mock(OrderItem.class);
        when(item1.getOrderItemStatus()).thenReturn(OrderItemStatus.SHIPPED); // 취소 불가능 상태
        Order uncancelableOrder = mock(Order.class);
        when(uncancelableOrder.getOrderItems()).thenReturn(Set.of(item1));
        when(uncancelableOrder.getNonMemberPassword()).thenReturn(encodedPassword);


        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(uncancelableOrder));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        // when & then
        assertThrows(OrderStatusTransitionException.class, () -> nonMemberOrderService.cancelOrderForNonMember(orderId, rawPassword));
        verify(orderCancelOrchestrator, never()).processCancelOrder(any(), any());
    }
}
