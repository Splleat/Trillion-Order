package com.nhnacademy.order.ordercoupon.domain;

import com.nhnacademy.order.common.entity.BaseTimeEntity;
import com.nhnacademy.order.order.domain.Order;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@AllArgsConstructor
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

    // TODO: 필드 삭제 검토
    @Enumerated(EnumType.STRING)
    @Column(name = "coupon_type", nullable = false)
    private CouponType couponType;

    @Column(name = "target_id")
    private Long targetId; // 도서 ID, 장바구니 쿠폰일 경우 null

}
