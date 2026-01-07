package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.repository.OrderCreateSagaRepository;
import com.nhnacademy.order.packaging.domain.Packaging;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderInitialCreateServiceTest {

    @InjectMocks
    private OrderInitialCreateService orderInitialCreateService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderCreateSagaRepository orderCreateSagaRepository;

    @Mock
    private PackagingRepository packagingRepository;

    @Test
    @DisplayName("초기 주문 생성 성공 - 포장 있는 상품과 없는 상품 혼합 및 사가 생성")
    void createInitialOrder_Success() {
        // given
        Long memberId = 1L;
        OrdererInfo ordererInfo = new OrdererInfo("주문자", "010-0000-0000", "test@email.com");
        ReceiverInfo receiverInfo = new ReceiverInfo("수령인", "010-1111-1111", "주소");
        OrderDetails orderDetails = OrderDetails.createInitial("12345", LocalDateTime.now(), 0);
        UUID sagaId = UUID.randomUUID();

        List<OrderItemCreateRequest> itemRequests = List.of(
            new OrderItemCreateRequest(101L, 2, null, 1L, null), // packagingId=1
            new OrderItemCreateRequest(102L, 1, null, null, null)    // packagingId=null
        );

        Packaging packaging1 = Packaging.create("선물포장", 3000);
        ReflectionTestUtils.setField(packaging1, "packagingId", 1L); // ID 설정

        // when
        when(packagingRepository.findAllById(anyList())).thenReturn(List.of(packaging1));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            ReflectionTestUtils.setField(order, "orderId", 1L); // ID 부여
            return order;
        });

        var result = orderInitialCreateService.createInitialOrderWithSaga(memberId, null, ordererInfo, receiverInfo, orderDetails, null, itemRequests, sagaId);

        // then
        // 1. Order 검증
        ArgumentCaptor<Order> orderArgumentCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderArgumentCaptor.capture());
        Order savedOrder = orderArgumentCaptor.getValue();

        assertThat(savedOrder.getMemberId()).isEqualTo(memberId);
        assertThat(savedOrder.getOrdererInfo()).isEqualTo(ordererInfo);
        assertThat(savedOrder.getOrderItems()).hasSize(2);

        OrderItem itemWithPackaging = savedOrder.getOrderItems().stream()
            .filter(item -> item.getBookId().equals(101L))
            .findFirst().orElseThrow();

        assertThat(itemWithPackaging.getPackagingInfo().packagingType()).isEqualTo("선물포장");

        // 2. Saga 검증
        ArgumentCaptor<OrderCreateSaga> sagaArgumentCaptor = ArgumentCaptor.forClass(OrderCreateSaga.class);
        verify(orderCreateSagaRepository).save(sagaArgumentCaptor.capture());
        OrderCreateSaga savedSaga = sagaArgumentCaptor.getValue();

        assertThat(savedSaga.getSagaId()).isEqualTo(sagaId);
        assertThat(savedSaga.getOrderId()).isEqualTo(savedOrder.getOrderId());
        
        // 3. Result 객체 검증
        assertThat(result.order()).isEqualTo(savedOrder);
        assertThat(result.saga()).isEqualTo(savedSaga);
    }
}