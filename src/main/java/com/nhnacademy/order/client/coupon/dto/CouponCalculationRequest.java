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
        // TODO: 필드 삭제 검토 -> 해당 내용은 쿠폰 서비스가 직접 조회
        Set<Long> categoryIds,
        // TODO: 필드 삭제 검토 -> 해당 내용은 쿠폰 서비스가 직접 조회
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
