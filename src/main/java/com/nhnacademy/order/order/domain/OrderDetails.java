package com.nhnacademy.order.order.domain;

import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

@Embeddable
public record OrderDetails(
    LocalDateTime orderDate,
    LocalDateTime shippingDate, // 출고일
    String shippingPostCode, // 우편번호
    LocalDateTime deliveryDate,
    int deliveryFee,
    int pointUsage,
    int totalPrice
) {}
