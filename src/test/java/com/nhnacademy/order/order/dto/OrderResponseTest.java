package com.nhnacademy.order.order.dto;

import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.domain.PackagingInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OrderResponseTest {

    @DisplayName("Order 엔티티로부터 OrderResponse 생성 성공")
    @Test
    void createFromOrder_Success() {
        // given
        Long orderId = 1L;
        Long memberId = 100L;
        String orderNumber = "ORDER-1234";
        LocalDateTime now = LocalDateTime.now();

        OrdererInfo ordererInfo = new OrdererInfo("Tester", "010-1234-5678", "test@example.com");
        ReceiverInfo receiverInfo = new ReceiverInfo("Receiver", "010-9876-5432", "Seoul");
        
        OrderDetails orderDetails = new OrderDetails(
                now,
                "12345",
                now.plusDays(3),
                3000,
                1000,
                500,
                10000,
                11500
        );

        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderId", orderId);
        ReflectionTestUtils.setField(order, "memberId", memberId);
        ReflectionTestUtils.setField(order, "orderNumber", orderNumber);
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "ordererInfo", ordererInfo);
        ReflectionTestUtils.setField(order, "receiverInfo", receiverInfo);
        ReflectionTestUtils.setField(order, "orderDetails", orderDetails);
        ReflectionTestUtils.setField(order, "orderItems", new HashSet<>());

        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "orderItemId", 1L);
        ReflectionTestUtils.setField(orderItem, "bookId", 10L);
        ReflectionTestUtils.setField(orderItem, "order", order);
        ReflectionTestUtils.setField(orderItem, "price", 10000);
        ReflectionTestUtils.setField(orderItem, "quantity", 1);
        ReflectionTestUtils.setField(orderItem, "couponDiscountAmount", 0);
        ReflectionTestUtils.setField(orderItem, "orderItemStatus", OrderItemStatus.PREPARING);
        ReflectionTestUtils.setField(orderItem, "packagingInfo", new PackagingInfo("Box", 1000));
        ReflectionTestUtils.setField(orderItem, "refundPrice", 0);

        order.addOrderItem(orderItem);

        // when
        OrderResponse response = OrderResponse.create(order);

        // then
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.memberId()).isEqualTo(memberId);
        assertThat(response.orderNumber()).isEqualTo(orderNumber);
        assertThat(response.orderDate()).isEqualTo(now);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(response.originPrice()).isEqualTo(10000);
        assertThat(response.totalPrice()).isEqualTo(11500);
        assertThat(response.deliveryFee()).isEqualTo(3000);
        assertThat(response.pointUsage()).isEqualTo(1000);
        assertThat(response.totalCouponDiscount()).isEqualTo(500);
        assertThat(response.ordererInfo()).isEqualTo(ordererInfo);
        assertThat(response.receiverInfo()).isEqualTo(receiverInfo);
        assertThat(response.orderItems()).hasSize(1);
        assertThat(response.orderItems().get(0).orderItemId()).isEqualTo(1L);
    }
}