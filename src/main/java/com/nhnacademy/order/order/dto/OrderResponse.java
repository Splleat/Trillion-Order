package com.nhnacademy.order.order.dto;

import com.nhnacademy.order.order.domain.OrdererInfo;
import com.nhnacademy.order.order.domain.PaymentStatus;
import com.nhnacademy.order.order.domain.ReceiverInfo;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Long orderId,
    String orderTitle,
    LocalDateTime orderDate,
    PaymentStatus paymentStatus,
    int totalPrice,
    OrdererInfo ordererInfo,
    ReceiverInfo receiverInfo,
    List<OrderItemResponse> orderItems
) {
    public static OrderResponse create(OrderBaseResponse orderBaseResponse, List<OrderItemResponse> orderItems) {
        return new OrderResponse(
            orderBaseResponse.orderId(),
            orderBaseResponse.orderTitle(),
            orderBaseResponse.orderDate(),
            orderBaseResponse.paymentStatus(),
            orderBaseResponse.totalPrice(),
            orderBaseResponse.ordererInfo(),
            orderBaseResponse.receiverInfo(),
            orderItems
        );
    }
}
