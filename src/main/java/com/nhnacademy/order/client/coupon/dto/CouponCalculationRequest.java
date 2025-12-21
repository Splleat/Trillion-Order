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
        int price, // 주문 당시 가격을 기준으로 해야 해서 남겨둠
        int quantity
    ) {
        public static CouponCalculationOrderItem create(OrderItem orderItem) {
            return new CouponCalculationOrderItem(
                orderItem.getBookId(),
                orderItem.getPrice(),
                orderItem.getQuantity()
            );
        }
    }
}
