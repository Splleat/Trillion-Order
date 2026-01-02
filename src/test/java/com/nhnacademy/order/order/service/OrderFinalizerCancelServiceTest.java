package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.ordersaga.cancellation.domain.CancelSagaStep;
import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.cancellation.repository.OrderCancelSagaRepository;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFinalizerCancelServiceTest {

    @InjectMocks
    private OrderFinalizerCancelService orderFinalizerCancelService;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCancelSagaRepository orderCancelSagaRepository;
    @Mock
    private SagaUpdateService sagaUpdateService;

    @Test
    @DisplayName("취소 시작 성공 테스트 - 초기 상태")
    void cancelStart_success() {
        // given
        Order order = new Order();
        order.setOrderStatus(OrderStatus.PENDING);
        ReflectionTestUtils.setField(order, "orderId", 1L);

        // when
        OrderCancelSaga saga = orderFinalizerCancelService.cancelStart(order);

        // then
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELING);
        verify(orderRepository).save(order);
        verify(sagaUpdateService).updateCancelSagaStep(any(OrderCancelSaga.class), eq(CancelSagaStep.STARTED));
        assertThat(saga).isNotNull();
    }

    @Test
    @DisplayName("취소 시작 - 이미 취소 중인 경우 기존 사가 반환")
    void cancelStart_already_canceling() {
        // given
        Order order = new Order();
        order.setOrderStatus(OrderStatus.CANCELING);
        ReflectionTestUtils.setField(order, "orderId", 1L);
        OrderCancelSaga existingSaga = OrderCancelSaga.create(UUID.randomUUID(), 1L);

        when(orderCancelSagaRepository.findByOrderId(1L)).thenReturn(Optional.of(existingSaga));

        // when
        OrderCancelSaga saga = orderFinalizerCancelService.cancelStart(order);

        // then
        assertThat(saga).isEqualTo(existingSaga);
        verify(orderRepository, never()).save(order);
    }

    @Test
    @DisplayName("취소 시작 - 이미 취소 중이나 사가가 없는 경우 예외")
    void cancelStart_already_canceling_no_saga() {
        // given
        Order order = new Order();
        order.setOrderStatus(OrderStatus.CANCELING);
        ReflectionTestUtils.setField(order, "orderId", 1L);

        when(orderCancelSagaRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> orderFinalizerCancelService.cancelStart(order))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문 취소 최종 확정 성공")
    void cancelOrder_success() {
        // given
        Order order = new Order();
        order.setOrderStatus(OrderStatus.CANCELING);
        OrderItem item = new OrderItem();
        item.setOrderItemStatus(OrderItemStatus.PREPARING);
        order.addOrderItem(item);

        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), 1L);

        // when
        orderFinalizerCancelService.cancelOrder(order, saga);

        // then
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(item.getOrderItemStatus()).isEqualTo(OrderItemStatus.CANCELED);
        verify(orderRepository).save(order);
        assertThat(saga.isBridged()).isTrue();
        verify(orderCancelSagaRepository).save(saga);
    }

    @Test
    @DisplayName("주문 취소 최종 확정 - 이미 취소된 경우 사가 연결만 처리")
    void cancelOrder_already_canceled() {
        // given
        Order order = new Order();
        order.setOrderStatus(OrderStatus.CANCELED);
        OrderCancelSaga saga = OrderCancelSaga.create(UUID.randomUUID(), 1L);
        saga.setBridged(false);

        // when
        orderFinalizerCancelService.cancelOrder(order, saga);

        // then
        verify(orderRepository, never()).save(order);
        assertThat(saga.isBridged()).isTrue();
        verify(orderCancelSagaRepository).save(saga);
    }
}
