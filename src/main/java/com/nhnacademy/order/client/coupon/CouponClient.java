package com.nhnacademy.order.client.coupon;

import com.nhnacademy.order.client.coupon.dto.CouponApplyRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "coupon-service")
public interface CouponClient {
    @GetMapping("/coupons/{coupon-id}")
    CouponCalculationResponse calculateDiscount(@PathVariable("coupon-id") Long couponId, @RequestHeader("X-Member-Id") Long memberId, @RequestParam List<Long> bookIds, @RequestParam List<Long> quantities);

    @PostMapping("/coupons/{coupon-id}/use")
    void applyCoupon(@PathVariable("coupon-id") Long couponId, @RequestHeader("X-Member-Id") Long memberId, @RequestBody CouponApplyRequest request);

    @DeleteMapping("/coupons/{coupon-id}/use")
    void rollbackCoupon(@PathVariable("coupon-id") Long couponId, @RequestHeader("X-Member-Id") Long memberId);
}