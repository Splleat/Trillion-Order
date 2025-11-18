package com.nhnacademy.order.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDateTime;

@Embeddable
public record OrderDetails(
    @Column(name = "order_date")
    LocalDateTime orderDate,

    @Column(name = "shipping_date")
    LocalDateTime shippingDate, // 출고일

    @Column(name = "shipping_post_code")
    String shippingPostCode, // 우편번호

    @Column(name = "delivery_date")
    LocalDateTime deliveryDate,

    @Column(name = "delivery_fee")
    int deliveryFee,

    @Column(name = "point_usage")
    int pointUsage,

    @Column(name = "total_price")
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

    public OrderDetails withNewTotalPrice(int newTotalPrice) {
        return new OrderDetails(
            this.orderDate,
            this.shippingDate,
            this.shippingPostCode,
            this.deliveryDate,
            this.deliveryFee,
            this.pointUsage,
            newTotalPrice
        );
    }

    public OrderDetails withNewDeliveryFee(int newDeliveryFee) {
        return new OrderDetails(
            this.orderDate,
            this.shippingDate,
            this.shippingPostCode,
            this.deliveryDate,
            newDeliveryFee,
            this.pointUsage,
            this.totalPrice
        );
    }
}
