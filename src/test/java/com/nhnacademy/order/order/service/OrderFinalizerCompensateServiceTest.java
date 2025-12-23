package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.repository.OrderCreateSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFinalizerCompensateServiceTest {

    @InjectMocks
    private OrderFinalizerCompensateService orderFinalizerCompensateService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCreateSagaRepository orderCreateSagaRepository;

    private Order order;
    private OrderCreateSaga saga;

    @BeforeEach
    void setUp() {
        order = mock(Order.class);
        saga = mock(OrderCreateSaga.class);
    }

    @Test
    @DisplayName("compensateOrder: 주문 상태를 CREATION_FAILED로 변경하고 사가를 브릿징")
    void compensateOrder_ShouldSetStatusToCreationFailedAndBridgeSaga() {
        // when
        orderFinalizerCompensateService.compensateOrder(order, saga);

        // then
        verify(order).setOrderStatus(OrderStatus.CREATION_FAILED);
        verify(orderRepository).save(order);
        verify(saga).setBridged(true);
        verify(orderCreateSagaRepository).save(saga);
    }

    @Test
    @DisplayName("compensateOrder: 이미 CREATION_FAILED 상태인 경우, 사가만 브릿징 (필요하다면)")
    void compensateOrder_ShouldOnlyBridgeSaga_WhenAlreadyCreationFailed() {
        // given
        when(order.getOrderStatus()).thenReturn(OrderStatus.CREATION_FAILED);
        when(saga.isBridged()).thenReturn(false); // 사가가 아직 브릿지되지 않음

        // when
        orderFinalizerCompensateService.compensateOrder(order, saga);

        // then
        verify(order, never()).setOrderStatus(any(OrderStatus.class)); // 상태 변경 호출 안됨
        verify(orderRepository, never()).save(order); // Order 저장 호출 안됨
        verify(saga).setBridged(true); // 사가 브릿징은 호출됨
        verify(orderCreateSagaRepository).save(saga); // 사가 저장 호출됨
    }

    @Test
    @DisplayName("compensateOrder: 이미 CREATION_FAILED 상태이고 사가도 이미 브릿지된 경우, 아무것도 하지 않음")
    void compensateOrder_ShouldDoNothing_WhenAlreadyCreationFailedAndBridged() {
        // given
        when(order.getOrderStatus()).thenReturn(OrderStatus.CREATION_FAILED);
        when(saga.isBridged()).thenReturn(true); // 사가가 이미 브릿지됨

        // when
        orderFinalizerCompensateService.compensateOrder(order, saga);

        // then
        verify(order, never()).setOrderStatus(any(OrderStatus.class));
        verify(orderRepository, never()).save(any(Order.class));
        verify(saga, never()).setBridged(anyBoolean());
        verify(orderCreateSagaRepository, never()).save(any(OrderCreateSaga.class));
    }
}
