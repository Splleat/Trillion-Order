package com.nhnacademy.order.client.coupon;

import com.nhnacademy.order.client.coupon.dto.CouponApplyRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@Profile("!local")
@FeignClient(name = "coupon-service")
public interface CouponClient {
    @PostMapping("/coupons/calculate")
    CouponCalculationResponse calculateDiscount(@RequestBody CouponCalculationRequest request);

    @PostMapping("/coupons/apply")
    void applyCoupon(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody CouponApplyRequest request);

    @PostMapping("/coupons/withdraw")
    void withdrawCoupon(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody CouponApplyRequest request);

    @PostMapping("/coupons/rollback")
    void rollbackCoupon(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody CouponApplyRequest request);
}