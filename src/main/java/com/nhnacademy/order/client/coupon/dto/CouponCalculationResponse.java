package com.nhnacademy.order.client.coupon.dto;

import com.nhnacademy.order.ordercoupon.domain.CouponType;

import java.util.List;

public record CouponCalculationResponse(
    Long couponId,
    CouponType couponType,
    Long targetId,
    int totalDiscountAmount,
    List<ItemDiscount> itemDiscounts
) {
    public record ItemDiscount(
        Long bookId,
        int discountAmount
    ) {}
}
