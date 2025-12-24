package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.repository.PointAccumulationEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderItemConfirmServiceTest {

    @InjectMocks
    private OrderItemConfirmService orderItemConfirmService;

    @Mock
    private OrderItemUpdateService orderItemUpdateService;

    @Mock
    private PointAccumulationEventRepository pointAccumulationEventRepository;

    @Test
    @DisplayName("회원 주문 상품 확정: 상태 변경 및 포인트 적립 이벤트 저장")
    void confirmOrderItem_Member() {
        // given
        Order order = mock(Order.class);
        when(order.getMemberId()).thenReturn(1L);
        when(order.getOrderId()).thenReturn(100L);

        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrder()).thenReturn(order);
        when(orderItem.getOrderItemId()).thenReturn(200L);
        when(orderItem.getPrice()).thenReturn(10000);
        when(orderItem.getQuantity()).thenReturn(1);
        when(orderItem.getCouponDiscountAmount()).thenReturn(1000);

        PointAccumulationEvent savedEvent = mock(PointAccumulationEvent.class);
        when(pointAccumulationEventRepository.save(any(PointAccumulationEvent.class))).thenReturn(savedEvent);

        // when
        PointAccumulationEvent result = orderItemConfirmService.confirmOrderItem(orderItem);

        // then
        // 1. 상태 변경 호출 확인
        verify(orderItemUpdateService, times(1))
                .updateOrderItemStatus(eq(orderItem), eq(OrderItemStatusUpdateStrategy.CONFIRMED));
        
        // 2. 이벤트 저장 호출 확인
        verify(pointAccumulationEventRepository, times(1)).save(any(PointAccumulationEvent.class));
        
        // 3. 반환값 확인
        assertThat(result).isEqualTo(savedEvent);
    }

    @Test
    @DisplayName("비회원 주문 상품 확정: 상태 변경만 호출하고 이벤트는 생성하지 않음")
    void confirmOrderItem_NonMember() {
        // given
        Order order = mock(Order.class);
        when(order.getMemberId()).thenReturn(null); // 비회원

        OrderItem orderItem = mock(OrderItem.class);
        when(orderItem.getOrder()).thenReturn(order);

        // when
        PointAccumulationEvent result = orderItemConfirmService.confirmOrderItem(orderItem);

        // then
        // 1. 상태 변경 호출 확인
        verify(orderItemUpdateService, times(1))
                .updateOrderItemStatus(eq(orderItem), eq(OrderItemStatusUpdateStrategy.CONFIRMED));
        
        // 2. 이벤트 저장은 호출되지 않아야 함
        verify(pointAccumulationEventRepository, never()).save(any());
        
        // 3. 반환값은 null이어야 함
        assertThat(result).isNull();
    }
}
