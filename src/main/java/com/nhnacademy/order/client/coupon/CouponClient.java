package com.nhnacademy.order.client.coupon;

import com.nhnacademy.order.client.coupon.dto.CouponApplyRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import com.nhnacademy.order.common.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Profile("!local")
@FeignClient(name = "COUPON-SERVICE", configuration = FeignClientConfig.class)
public interface CouponClient {
    @PostMapping("/coupons/calculate")
    CouponCalculationResponse calculateDiscount(@RequestBody CouponCalculationRequest request);

    @PostMapping("/coupons/apply")
    void applyCoupon(@RequestBody CouponApplyRequest request);

    @PostMapping("/coupons/withdraw")
    void withdrawCoupon(@RequestBody CouponApplyRequest request);

    @PostMapping("/coupons/rollback")
    void rollbackCoupon(@RequestBody CouponApplyRequest request);
}