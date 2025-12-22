package com.nhnacademy.order.client.coupon.dto;

import com.nhnacademy.order.orderitem.domain.OrderItem;

import java.util.List;

public record CouponCalculationRequest(
    Long memberId,
    Long couponId,
    List<CouponCalculationOrderItem> items
) {
    public record CouponCalculationOrderItem(
        Long bookId,
        int quantity
    ) {
        public static CouponCalculationOrderItem create(OrderItem orderItem) {
            return new CouponCalculationOrderItem(
                orderItem.getBookId(),
                orderItem.getQuantity()
            );
        }
    }
}
