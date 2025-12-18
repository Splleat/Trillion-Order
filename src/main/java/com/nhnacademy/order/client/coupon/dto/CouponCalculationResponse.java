package com.nhnacademy.order.client.coupon.dto;

import com.nhnacademy.order.ordercoupon.domain.CouponType;

import java.util.List;

public record CouponCalculationResponse(
    // TODO: 필드 삭제 검토
    Long couponId,
    // TODO: 필드 삭제 검토
    CouponType couponType,

    // TODO: 필요한가?
    Long targetId,

    int totalDiscountAmount,
    List<ItemDiscount> itemDiscounts
) {
    public record ItemDiscount(
        Long bookId,
        int discountAmount
    ) {}
}
