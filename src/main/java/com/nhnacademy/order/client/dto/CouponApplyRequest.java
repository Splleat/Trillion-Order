package com.nhnacademy.order.client.dto;

public record CouponApplyRequest(
    Long couponId,
    int price,
    Long memberId
) {}
