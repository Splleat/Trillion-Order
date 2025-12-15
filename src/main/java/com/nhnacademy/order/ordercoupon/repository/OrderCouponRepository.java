package com.nhnacademy.order.ordercoupon.repository;

import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCouponRepository extends JpaRepository<OrderCoupon, Long> {
}
