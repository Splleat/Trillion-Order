package com.nhnacademy.order.order.dto;

import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;

import java.util.List;

public record OrderCreateRequest(
    String ordererName,
    String ordererContact,

    String receiverName,
    String receiverContact,
    String receiverAddress,
    String receiverPostCode,

    String nonMemberPassword,

    int pointUsage,

    List<OrderItemCreateRequest> orderItems
) {
    public int getTotalPrice() {
        return orderItems.stream()
                .mapToInt(OrderItemCreateRequest::price)
                .sum();
    }
}
