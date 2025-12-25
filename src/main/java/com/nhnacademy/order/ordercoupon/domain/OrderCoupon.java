package com.nhnacademy.order.ordercoupon.domain;

import com.nhnacademy.order.order.domain.Order;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class OrderCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_coupon_id")
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "target_id")
    private Long targetId; // 도서 ID, 장바구니 쿠폰일 경우 null

    private OrderCoupon(Order order, Long couponId, int discountAmount, Long targetId) {
        this.order = order;
        this.couponId = couponId;
        this.discountAmount = discountAmount;
        this.targetId = targetId;
    }

    public static OrderCoupon createInitial(Long couponId) {
        return new OrderCoupon(null, couponId, 0, null);
    }

    public void completeOrderCoupon(int discountAmount, Long targetId) {
        this.discountAmount = discountAmount;
        this.targetId = targetId;
    }
}
