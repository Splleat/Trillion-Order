package com.nhnacademy.order.client.coupon.service;

import com.nhnacademy.order.client.coupon.CouponClient;
import com.nhnacademy.order.client.common.handler.ResilienceFallbackHandler;
import com.nhnacademy.order.client.coupon.dto.CouponApplyRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    public CouponCalculationResponse calculateDiscount(CouponCalculationRequest request) {
        return couponClient.calculateDiscount(request);
    }

    // 쿠폰 사용 (주문 생성)
    @CircuitBreaker(name = "COUPON-SERVICE", fallbackMethod = "fallbackApplyCoupon")
    @Retry(name = "COUPON-SERVICE")
    public void applyCoupon(UUID sagaId, Long memberId, Long couponId) {
        couponClient.applyCoupon(sagaId, new CouponApplyRequest(memberId, couponId));
    }

    // 쿠폰 사용 취소 (주문 취소)
    @CircuitBreaker(name = "COUPON-SERVICE", fallbackMethod = "fallbackWithdrawCoupon")
    @Retry(name = "COUPON-SERVICE")
    public void withdrawCoupon(UUID sagaId, Long memberId, Long couponId) {
        couponClient.withdrawCoupon(sagaId, new CouponApplyRequest(memberId, couponId));
    }

    // 쿠폰 복원 (주문 생성 실패 시)
    @CircuitBreaker(name = "COUPON-SERVICE", fallbackMethod = "fallbackRollbackCoupon")
    @Retry(name = "COUPON-SERVICE")
    public void rollbackCoupon(UUID sagaId, Long memberId, Long couponId) {
        couponClient.rollbackCoupon(sagaId, new CouponApplyRequest(memberId, couponId));
    }

    public CouponCalculationResponse fallbackCalculateDiscount(UUID sagaId, CouponCalculationRequest request, Throwable throwable) {
        return fallbackHandler.handle(SERVICE_NAME, "쿠폰 할인가 계산", throwable);
    }

    public void fallbackApplyCoupon(UUID sagaId, Long memberId, Long couponId, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "쿠폰 사용", throwable);
    }

    public void fallbackWithdrawCoupon(UUID sagaId, Long memberId, Long couponId, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "쿠폰 사용 취소", throwable);
    }

    public void fallbackRollbackCoupon(UUID sagaId, Long memberId, Long couponId, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "쿠폰 복원", throwable);
    }
}
