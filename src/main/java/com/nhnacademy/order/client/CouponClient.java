package com.nhnacademy.order.client;

import com.nhnacademy.order.client.dto.CouponApplyRequest;
import com.nhnacademy.order.client.dto.CouponResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "coupon-service")
public interface CouponClient {
    @GetMapping("/api/coupon/1")
    CouponResponse getCoupon(Long couponId);

    @PostMapping("/api/coupon/2")
    void applyCoupon(CouponApplyRequest request);
}
