package com.nhnacademy.order.client.dto;

import java.util.UUID;

public record CouponApplyRequest(
    UUID sagaId,
    Long memberId,
    Long couponId
) {}
