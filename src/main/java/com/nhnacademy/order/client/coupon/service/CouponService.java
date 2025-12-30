package com.nhnacademy.order.client.coupon.service;

import com.nhnacademy.order.client.coupon.CouponClient;
import com.nhnacademy.order.client.common.handler.ResilienceFallbackHandler;
import com.nhnacademy.order.client.coupon.dto.CouponApplyRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CouponService {
    private final CouponClient couponClient;
    private final ResilienceFallbackHandler fallbackHandler;
    private static final String SERVICE_NAME = "쿠폰 API";

    // 쿠폰 할인가 계산
    @CircuitBreaker(name = "COUPON-SERVICE", fallbackMethod = "fallbackCalculateDiscount")
    @Retry(name = "COUPON-SERVICE")
    public CouponCalculationResponse calculateDiscount(Long couponId, Long memberId, List<Long> bookIds, List<Long> quantities) {
        return couponClient.calculateDiscount(couponId, memberId, bookIds, quantities);
    }

    // 쿠폰 사용 (주문 생성)
    @CircuitBreaker(name = "COUPON-SERVICE", fallbackMethod = "fallbackApplyCoupon")
    @Retry(name = "COUPON-SERVICE")
    public void applyCoupon(Long couponId, Long memberId, List<Long> bookIds, List<Long> quantities) {
        couponClient.applyCoupon(couponId, memberId, new CouponApplyRequest(bookIds, quantities));
    }

    // 쿠폰 사용 취소 (주문 취소)
    @CircuitBreaker(name = "COUPON-SERVICE", fallbackMethod = "fallbackWithdrawCoupon")
    @Retry(name = "COUPON-SERVICE")
    public void withdrawCoupon(Long couponId, Long memberId) {
        couponClient.rollbackCoupon(couponId, memberId);
    }

    public CouponCalculationResponse fallbackCalculateDiscount(Long couponId, Long memberId, List<Long> bookIds, List<Long> quantities, Throwable throwable) {
        return fallbackHandler.handle(SERVICE_NAME, "쿠폰 할인가 계산", throwable);
    }

    public void fallbackApplyCoupon(Long couponId, Long memberId, List<Long> bookIds, List<Long> quantities, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "쿠폰 사용", throwable);
    }

    public void fallbackWithdrawCoupon(Long couponId, Long memberId, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "쿠폰 사용 취소", throwable);
    }
}
