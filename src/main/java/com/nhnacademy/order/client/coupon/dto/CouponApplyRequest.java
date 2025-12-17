package com.nhnacademy.order.client.coupon.dto;

public record CouponApplyRequest(
    Long memberId,
    Long couponId
) {}
