package com.nhnacademy.order.client.coupon.dto;

import com.nhnacademy.order.orderitem.domain.OrderItem;

import java.util.List;
import java.util.Set;

public record CouponCalculationRequest(
    Long memberId,
    Long couponId,
    List<CouponCalculationOrderItem> items
) {
    public record CouponCalculationOrderItem(
        Long bookId,
        Set<Long> categoryIds,
        int price,
        int quantity
    ) {
        public static CouponCalculationOrderItem create(OrderItem orderItem, Set<Long> categoryIds) {
            return new CouponCalculationOrderItem(
                orderItem.getBookId(),
                categoryIds,
                orderItem.getPrice(),
                orderItem.getQuantity()
            );
        }
    }
}
