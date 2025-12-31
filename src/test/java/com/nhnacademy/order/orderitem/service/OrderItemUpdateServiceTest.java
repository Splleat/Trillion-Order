package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderItemUpdateServiceTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderItemUpdateService orderItemUpdateService;

    @DisplayName("주문 상품 상태 업데이트 성공")
    @Test
    void updateOrderItemStatus_Success() {
        // given
        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "orderItemId", 1L);
        ReflectionTestUtils.setField(orderItem, "orderItemStatus", OrderItemStatus.PREPARING);

        // PREPARING -> SHIPPED 로 변경하는 전략 사용
        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.SHIPPED;

        // when
        orderItemUpdateService.updateOrderItemStatus(orderItem, strategy);

        // then
        assertThat(orderItem.getOrderItemStatus()).isEqualTo(OrderItemStatus.SHIPPED);
        verify(orderItemRepository).save(any(OrderItem.class));
    }
}