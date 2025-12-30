package com.nhnacademy.order.client.coupon.dto;

import java.util.List;

public record CouponApplyRequest(
    List<Long> bookIds,
    List<Long> quantities
) {}
