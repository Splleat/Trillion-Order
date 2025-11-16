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
) {
    public static OrderDetails create(String shippingPostCode, LocalDateTime deliveryDate, int deliveryFee, int pointUsage, int totalPrice) {
        return new OrderDetails(
            LocalDateTime.now(),
            null, // 출고일 - 관리자가 설정
            shippingPostCode,
            deliveryDate,
            deliveryFee,
            pointUsage,
            totalPrice
        );
    }
}
