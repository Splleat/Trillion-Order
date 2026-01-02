package com.nhnacademy.order.order.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderCreateFailureException;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.orderitem.service.OrderItemService;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderInitialCreateService orderInitialCreateService;
    @Mock
    private OrderFinalizerCreateService orderFinalizerCreateService;
    @Mock
    private OrderItemService orderItemService;
    @Mock
    private OrderCreateOrchestrator orderCreateOrchestrator;
    @Mock
    private OrderCancelOrchestrator orderCancelOrchestrator;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("주문 생성 성공 테스트")
    void createOrder_success() {
        // given
        UserInfo userInfo = new UserInfo(1L, null, "MEMBER");
        OrderCreateRequest request = new OrderCreateRequest(
                "orderer", "010-1234-5678", "test@test.com",
                LocalDateTime.now().plusDays(1),
                "receiver", "010-9876-5432", "address",
                "12345", "password", 0, 1L,
                Collections.emptyList()
        );
        Order order = mock(Order.class);
        OrderDetails orderDetails = mock(OrderDetails.class);
        
        when(order.getOrderId()).thenReturn(1L);
        when(order.getOrderDetails()).thenReturn(orderDetails);
        when(orderDetails.orderDate()).thenReturn(LocalDateTime.now());
        when(order.getOrderItems()).thenReturn(Collections.emptySet());
        when(order.getOrderStatus()).thenReturn(OrderStatus.PENDING);
        
        when(orderInitialCreateService.createInitialOrder(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(order);

        // when
        OrderResponse response = orderService.createOrder(userInfo, request);

        // then
        verify(orderCreateOrchestrator).processCreateOrder(any(OrderCreateSaga.class), eq(order));
        verify(orderFinalizerCreateService).finalizeOrderCreation(any(Order.class), any(OrderCreateSaga.class));
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("주문 생성 실패 시 보상 트랜잭션 실행 테스트")
    void createOrder_failure_compensate() {
        // given
        UserInfo userInfo = new UserInfo(1L, null, "MEMBER");
        OrderCreateRequest request = new OrderCreateRequest(
                "orderer", "010-1234-5678", "test@test.com",
                LocalDateTime.now().plusDays(1),
                "receiver", "010-9876-5432", "address",
                "12345", null, 0, null,
                Collections.emptyList()
        );
        Order order = mock(Order.class);
        when(order.getOrderId()).thenReturn(1L);
        when(orderInitialCreateService.createInitialOrder(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(order);
        doThrow(new OrderCreateFailureException("Fail")).when(orderCreateOrchestrator).processCreateOrder(any(), any());

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(userInfo, request))
                .isInstanceOf(OrderCreateFailureException.class);
        verify(orderCreateOrchestrator).compensate(any(OrderCreateSaga.class), eq(order));
        verify(order).setOrderStatus(OrderStatus.CREATION_FAILED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("전체 주문 조회 성공 테스트")
    void findAllOrders_success() {
        // given
        Pageable pageable = Pageable.unpaged();
        OrderBaseResponse baseResponse = new OrderBaseResponse(
                1L, 1L, "ORDER-123", LocalDateTime.now(), OrderStatus.PENDING,
                10000, 9000, 2500, 0, 1000, null, null
        );
        Page<OrderBaseResponse> page = new PageImpl<>(List.of(baseResponse));
        when(orderRepository.findAllBaseOrderByOrderStatusIn(any(), any())).thenReturn(page);
        when(orderItemRepository.findAllByOrderIds(anyList())).thenReturn(Collections.emptyList());

        // when
        Page<OrderResponse> result = orderService.findAllOrders(null, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        verify(orderRepository).findAllBaseOrderByOrderStatusIn(any(), any());
        verify(orderItemRepository).findAllByOrderIds(anyList());
    }

    @Test
    @DisplayName("주문 ID로 상세 조회 성공 테스트")
    void findOrderByOrderId_success() {
        // given
        Long orderId = 1L;
        OrderBaseResponse baseResponse = new OrderBaseResponse(
                orderId, 1L, "ORDER-123", LocalDateTime.now(), OrderStatus.PENDING,
                10000, 9000, 2500, 0, 1000, null, null
        );
        when(orderRepository.findBaseOrderById(orderId)).thenReturn(Optional.of(baseResponse));
        when(orderItemRepository.findOrderItemByOrder_OrderId(orderId)).thenReturn(Collections.emptyList());

        // when
        OrderResponse result = orderService.findOrderByOrderId(null, orderId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("주문 ID로 상세 조회 실패 - 존재하지 않는 주문")
    void findOrderByOrderId_notFound() {
        // given
        Long orderId = 1L;
        when(orderRepository.findBaseOrderById(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderService.findOrderByOrderId(null, orderId))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("회원 ID로 주문 목록 조회 성공")
    void findAllOrderByMemberId_success() {
        // given
        UserInfo userInfo = new UserInfo(1L, null, "MEMBER");
        Pageable pageable = Pageable.unpaged();
        OrderBaseResponse baseResponse = new OrderBaseResponse(
                1L, 1L, "ORDER-123", LocalDateTime.now(), OrderStatus.COMPLETED,
                10000, 9000, 2500, 0, 1000, null, null
        );
        Page<OrderBaseResponse> page = new PageImpl<>(List.of(baseResponse));

        when(orderRepository.findAllBaseOrderByMemberIdAndOrderStatusIn(any(), eq(1L), anyList())).thenReturn(page);
        when(orderItemRepository.findAllByOrderIds(anyList())).thenReturn(Collections.emptyList());

        // when
        Page<OrderResponse> result = orderService.findAllOrderByMemberId(userInfo, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("주문 전체 취소 성공 테스트")
    void cancelOrder_success() {
        // given
        UserInfo userInfo = new UserInfo(1L, null, "MEMBER");
        Long orderId = 1L;
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));
        // Domain uses Set<OrderItem>
        when(order.getOrderItems()).thenReturn(Set.of(orderItem));
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);

        // when
        orderService.cancelOrder(userInfo, orderId);

        // then
        verify(orderCancelOrchestrator).processCancelOrder(userInfo.userId(), order);
    }

    @Test
    @DisplayName("주문 전체 취소 실패 - 취소 불가능한 상태")
    void cancelOrder_failure_status() {
        // given
        UserInfo userInfo = new UserInfo(1L, null, "MEMBER");
        Long orderId = 1L;
        Order order = mock(Order.class);
        OrderItem orderItem = mock(OrderItem.class);

        when(orderRepository.findOrderWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));
        when(order.getOrderItems()).thenReturn(Set.of(orderItem));
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.SHIPPED); // 취소 불가능 상태

        // when & then
        assertThatThrownBy(() -> orderService.cancelOrder(userInfo, orderId))
                .isInstanceOf(OrderStatusTransitionException.class);
    }
}
