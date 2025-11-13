package com.nhnacademy.order.order.dto;

import com.nhnacademy.order.orderitem.dto.OrderItemRequest;

import java.util.List;

public record OrderCreateRequest(
    String ordererName,
    String ordererContact,
    String ordererNumber,

    String receiverName,
    String receiverContact,
    String receiverAddress,
    String receiverPostCode,

    String nonMemberPassword,
    int pointUsage,
    List<OrderItemRequest> orderItems
) {}
