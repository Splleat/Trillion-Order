package com.nhnacademy.order.client.coupon.dto;

import java.util.List;

public record CouponCalculationResponse(
    Long targetId, // targetId가 null이면 장바구니 쿠폰

    int totalDiscountPrice,
    List<DiscountBookResponse> discountBooks
) {}
