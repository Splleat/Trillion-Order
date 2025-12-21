package com.nhnacademy.order.client.coupon.dto;

import com.nhnacademy.order.ordercoupon.domain.CouponType;

import java.util.List;

public record CouponCalculationResponse(
    Long targetId, // targetId가 null이면 장바구니 쿠폰

    int totalDiscountAmount,
    List<ItemDiscount> itemDiscounts
) {
    public record ItemDiscount(
        Long bookId,
        int discountAmount
    ) {}
}
