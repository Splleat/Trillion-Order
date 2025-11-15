package com.nhnacademy.order.orderitem.dto;

public record OrderItemCreateRequest(
    Long bookId,
    int quantity,
    int price,
    Long couponId,
    Long packagingId
) {}
