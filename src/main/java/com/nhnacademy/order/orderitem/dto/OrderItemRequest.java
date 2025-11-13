package com.nhnacademy.order.orderitem.dto;

public record OrderItemRequest(
    Long bookId,
    int quantity,
    int price,
    Long couponId,
    Long packagingId
) {}
