package com.nhnacademy.order.order.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.exception.OrderPasswordMismatchException;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.exception.OrderItemNotFoundException;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import com.nhnacademy.order.ordersaga.itemrefund.service.NonMemberOrderItemRefundOrchestrator;
import com.nhnacademy.order.ordersaga.itemrefund.service.OrderItemRefundOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceUpdateTest {

    @InjectMocks
    private OrderServiceImpl orderServiceImpl;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private DeliveryPolicyRepository deliveryPolicyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OrderCancelService orderCancelService;
    @Mock
    private OrderCancelOrchestrator orderCancelOrchestrator;
    @Mock
    private OrderItemRefundOrchestrator orderItemRefundOrchestrator;
    @Mock
    private NonMemberOrderItemRefundOrchestrator nonMemberOrderItemRefundOrchestrator;


    @Test
    @DisplayName("주문 상품 상태 변경 - 성공: 배송중")
    void patchOrderItemStatus_Success_Shipped() {
        // given
        long orderId = 1L;
        long orderItemId = 101L;
        UserInfo adminInfo = new UserInfo(999L, "ADMIN"); // 관리자 정보

        // 1. '준비중' 상태의 OrderItem과 이를 포함하는 Order 객체 준비
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);
        when(orderItem.getOrderItemId()).thenReturn(orderItemId);

        Order order = mock(Order.class);
        when(order.getOrderItems()).thenReturn(List.of(orderItem));

        // 2. Repository가 준비된 객체들을 반환하도록 설정
        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));

        // 3. '배송중'으로 상태를 변경하라는 요청 DTO 준비
        OrderItemStatusPatchRequest request = new OrderItemStatusPatchRequest(OrderItemStatus.SHIPPED);

        // when
        orderServiceImpl.patchOrderItemStatus(adminInfo, orderId, orderItemId, request);

        // then
        // 1. 상태 변경 전략에 따라 OrderItem의 ship() 메소드가 호출되었는지 확인
        verify(orderItem, times(1)).ship();
        // 2. 환불 관련 로직은 호출되지 않았는지 확인
        verify(orderItemRefundOrchestrator, never()).processItemRefund(anyLong(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("주문 상품 상태 변경 - 성공: 회원 환불 승인")
    void patchOrderItemStatus_Success_MemberReturn() {
        // given
        long orderId = 1L;
        long orderItemId = 101L;
        UserInfo userInfo = new UserInfo(1L, "ADMIN");

        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(new DeliveryPolicy(1L, 5000, 30000)));

        // 1. '반품 요청' 상태의 OrderItem과 이를 포함하는 Order 객체 준비
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        when(orderItem.getOrderItemId()).thenReturn(orderItemId);

        Order order = mock(Order.class);
        when(order.getOrderItems()).thenReturn(List.of(orderItem));
        when(order.findOrderItemInOrder(anyLong())).thenReturn(orderItem);

        // 2. Repository가 준비된 객체들을 반환하도록 설정
        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));

        // 3. '환불 완료'로 상태를 변경하라는 요청 DTO 준비
        OrderItemStatusPatchRequest request = new OrderItemStatusPatchRequest(OrderItemStatus.RETURNED);

        // when
        orderServiceImpl.patchOrderItemStatus(userInfo, orderId, orderItemId, request);

        // then
        // 1. 상태 변경 전략에 따라 OrderItem의 상태가 RETURNED로 변경되었는지 확인
        verify(orderItem, times(1)).setOrderItemStatus(OrderItemStatus.RETURNED);
        // 2. 주문 전체 상태를 업데이트하는 로직이 호출되었는지 확인
        verify(order, times(1)).reflectItemStatusChange();

        // 3. ★★★ 버그 수정 검증 ★★★
        // 회원 환불이므로, '회원' 환불 오케스트레이터가 '정확히 1번만' 호출되어야 함
        verify(orderItemRefundOrchestrator, times(1)).processItemRefund(anyLong(), any(Order.class), any(OrderItem.class), anyInt());
        // '비회원' 환불 오케스트레이터는 호출되지 않아야 함
        verify(nonMemberOrderItemRefundOrchestrator, never()).processNonMemberItemRefund(any(Order.class), any(OrderItem.class), anyInt());
    }

    @Test
    @DisplayName("주문 상품 상태 변경 - 실패: 존재하지 않는 주문상품")
    void patchOrderItemStatus_Failure_NotFound() {
        // given
        long orderId = 1L;
        long nonExistentOrderItemId = 999L;
        UserInfo userInfo = new UserInfo(1L, "ADMIN");

        Order order = mock(Order.class);
        OrderItemStatusPatchRequest request = new OrderItemStatusPatchRequest(OrderItemStatus.SHIPPED);

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));

        // when & then
        assertThrows(OrderItemNotFoundException.class, () -> {
            orderServiceImpl.patchOrderItemStatus(userInfo, orderId, nonExistentOrderItemId, request);
        });

        // verify
        verify(orderRepository, times(1)).findOrderWithItemsByOrderId(orderId);
    }

    @Test
    @DisplayName("주문 상품 상태 변경 - 실패: 허용되지 않는 상태 전환")
    void patchOrderItemStatus_Failure_InvalidStateTransition() {
        // given
        long orderId = 1L;
        long orderItemId = 101L;
        UserInfo userInfo = new UserInfo(1L, "ADMIN");

        // 1. '배송 완료' 상태의 OrderItem 준비 (이 상태에서는 '배송중'으로 변경 불가)
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.DELIVERED);
        when(orderItem.getOrderItemId()).thenReturn(orderItemId);

        Order order = mock(Order.class);
        when(order.getOrderItems()).thenReturn(List.of(orderItem));

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));

        // 2. '배송중(SHIPPED)'으로 변경하려는 잘못된 요청
        OrderItemStatusPatchRequest request = new OrderItemStatusPatchRequest(OrderItemStatus.SHIPPED);

        // when & then
        assertThrows(OrderStatusTransitionException.class, () -> {
            orderServiceImpl.patchOrderItemStatus(userInfo, orderId, orderItemId, request);
        });

        // verify: 상태를 변경하는 메소드가 호출되지 않았는지 확인
        verify(orderItem, never()).setOrderItemStatus(any());
        verify(orderItem, never()).ship();
    }

    @Test
    @DisplayName("비회원 주문 상품 상태 변경 - 성공")
    void patchOrderItemStatusForNonMember_Success() {
        // given
        long orderId = 2L;
        long orderItemId = 201L;
        String rawPassword = "password1234";
        String encodedPassword = "encoded-password";

        // 1. '배송 완료' 상태의 OrderItem과 이를 포함하는 비회원 Order 객체 준비
        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.DELIVERED);
        when(orderItem.getOrderItemId()).thenReturn(orderItemId);
        when(orderItem.getShippingDate()).thenReturn(LocalDateTime.now());

        Order nonMemberOrder = mock(Order.class);
        when(nonMemberOrder.getNonMemberPassword()).thenReturn(encodedPassword);
        when(nonMemberOrder.getOrderItems()).thenReturn(List.of(orderItem));

        // 2. Repository가 이 Order 객체를 반환하도록 설정
        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(nonMemberOrder));

        // 3. 비밀번호가 일치하는 상황을 설정
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        // 4. '파손에 의한 환불 요청'으로 상태를 변경하라는 요청 DTO 준비
        NonMemberOrderItemStatusPatchRequest request = new NonMemberOrderItemStatusPatchRequest(rawPassword, OrderItemStatus.RETURN_REQUESTED_DAMAGED);

        // when
        orderServiceImpl.patchOrderItemStatusForNonMember(orderId, orderItemId, request);

        // then
        // 1. 비밀번호 검증 로직이 호출되었는지 확인
        verify(passwordEncoder, times(1)).matches(rawPassword, encodedPassword);
        // 2. OrderItem의 상태가 RETURN_REQUESTED_DAMAGED로 변경되었는지 확인
        verify(orderItem, times(1)).setOrderItemStatus(OrderItemStatus.RETURN_REQUESTED_DAMAGED);
    }

    @Test
    @DisplayName("비회원 주문 상품 상태 변경 - 실패: 비밀번호 불일치")
    void patchOrderItemStatusForNonMember_Failure_PasswordMismatch() {
        // given
        long orderId = 2L;
        long orderItemId = 201L;
        String wrongPassword = "wrong-password";
        String encodedPassword = "encoded-password";

        Order nonMemberOrder = mock(Order.class);
        when(nonMemberOrder.getNonMemberPassword()).thenReturn(encodedPassword);

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(nonMemberOrder));
        // 핵심: 비밀번호가 일치하지 않는 상황(false)을 설정
        when(passwordEncoder.matches(wrongPassword, encodedPassword)).thenReturn(false);

        NonMemberOrderItemStatusPatchRequest request = new NonMemberOrderItemStatusPatchRequest(wrongPassword, OrderItemStatus.CANCELED);

        // when & then
        assertThrows(OrderPasswordMismatchException.class, () -> {
            orderServiceImpl.patchOrderItemStatusForNonMember(orderId, orderItemId, request);
        });

        // verify
        verify(orderRepository, times(1)).findOrderWithItemsByOrderId(orderId);
        verify(passwordEncoder, times(1)).matches(wrongPassword, encodedPassword);
        // 상태 변경 로직은 호출되지 않아야 함
        verify(orderItemRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("주문 전체 취소 - 성공")
    void cancelOrder_Success() {
        // given
        long orderId = 1L;
        UserInfo userInfo = new UserInfo(1L, "MEMBER");

        // 1. 모든 상품이 취소 가능한 상태(PREPARING)인 Order 객체를 준비
        OrderItem item1 = mock(OrderItem.class);
        when(item1.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);

        OrderItem item2 = mock(OrderItem.class);
        when(item2.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);

        Order cancelableOrder = mock(Order.class);
        when(cancelableOrder.getOrderItems()).thenReturn(List.of(item1, item2));

        // 2. Repository가 이 Order 객체를 반환하도록 설정
        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(cancelableOrder));

        // when
        orderServiceImpl.cancelOrder(userInfo, orderId);

        // then
        // 1. 주문을 잘 찾아왔는지 확인
        verify(orderRepository, times(1)).findOrderWithItemsByOrderId(orderId);
        // 2. 취소 사가와 완료 서비스가 각각 1번씩 호출되었는지 확인
        verify(orderCancelOrchestrator, times(1)).processCancelOrder(userInfo.userId(), cancelableOrder);
        verify(orderCancelService, times(1)).completeOrder(cancelableOrder);
    }

    @Test
    @DisplayName("주문 전체 취소 - 실패: 취소 불가능 상태")
    void cancelOrder_Failure_CannotCancel() {
        // given
        long orderId = 1L;
        UserInfo userInfo = new UserInfo(1L, "MEMBER");

        // 1. 상품 중 하나가 취소 불가능한 상태(SHIPPED)인 Order 객체를 준비
        OrderItem item1 = mock(OrderItem.class);
        when(item1.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);

        OrderItem item2 = mock(OrderItem.class);
        when(item2.getOrderItemStatus()).thenReturn(OrderItemStatus.SHIPPED); // 취소 불가능!

        Order uncancelableOrder = mock(Order.class);
        when(uncancelableOrder.getOrderItems()).thenReturn(List.of(item1, item2));

        // 2. Repository가 이 Order 객체를 반환하도록 설정
        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(uncancelableOrder));

        // when & then
        // 1. cancelOrder를 실행하면, OrderStatusTransitionException 예외가 발생해야 함
        assertThrows(OrderStatusTransitionException.class, () -> {
            orderServiceImpl.cancelOrder(userInfo, orderId);
        });

        // 2. 예외가 발생했으므로, 취소 사가나 완료 서비스는 호출되지 않아야 함
        verify(orderCancelOrchestrator, never()).processCancelOrder(any(), any());
        verify(orderCancelService, never()).completeOrder(any());
    }
}
