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
        Long categoryId,
        int price,
        int quantity
    ) {
        public static CouponCalculationOrderItem create(OrderItem orderItem, Long categoryId) {
            return new CouponCalculationOrderItem(
                orderItem.getBookId(),
                categoryId,
                orderItem.getPrice(),
                orderItem.getQuantity()
            );
        }
    }
}
