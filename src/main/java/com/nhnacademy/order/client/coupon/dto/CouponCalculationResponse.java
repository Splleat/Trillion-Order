package com.nhnacademy.order.client.coupon.dto;

import java.util.List;

public record CouponCalculationResponse(
    int totalDiscountAmount,
    List<ItemDiscount> itemDiscounts
) {
    public record ItemDiscount(
        Long bookId,
        int discountAmount
    ) {}
}
