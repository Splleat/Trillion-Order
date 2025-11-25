package com.nhnacademy.order.orderitem.dto;

public record OrderItemCreateRequest(
    Long bookId,
    int quantity,
    Long couponId,
    Long packagingId
) {}
