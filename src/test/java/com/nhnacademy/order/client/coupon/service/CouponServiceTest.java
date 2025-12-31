package com.nhnacademy.order.client.coupon.service;

import com.nhnacademy.order.client.common.handler.ResilienceFallbackHandler;
import com.nhnacademy.order.client.coupon.CouponClient;
import com.nhnacademy.order.client.coupon.dto.CouponApplyRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import com.nhnacademy.order.client.coupon.dto.DiscountBookResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponClient couponClient;

    @Mock
    private ResilienceFallbackHandler fallbackHandler;

    @InjectMocks
    private CouponService couponService;

    @DisplayName("쿠폰 할인가 계산 호출 성공")
    @Test
    void calculateDiscount_Success() {
        // given
        Long couponId = 1L;
        Long memberId = 100L;
        List<Long> bookIds = List.of(10L);
        List<Long> quantities = List.of(1L);
        
        List<DiscountBookResponse> discountBooks = List.of(new DiscountBookResponse(10L, 5000L));
        CouponCalculationResponse expectedResponse = new CouponCalculationResponse(null, 5000, discountBooks);

        given(couponClient.calculateDiscount(couponId, memberId, bookIds, quantities))
                .willReturn(expectedResponse);

        // when
        CouponCalculationResponse response = couponService.calculateDiscount(couponId, memberId, bookIds, quantities);

        // then
        assertThat(response.totalDiscountPrice()).isEqualTo(5000);
        verify(couponClient).calculateDiscount(couponId, memberId, bookIds, quantities);
    }

    @DisplayName("쿠폰 사용 호출 성공")
    @Test
    void applyCoupon_Success() {
        // given
        Long couponId = 1L;
        Long memberId = 100L;
        List<Long> bookIds = List.of(10L);
        List<Long> quantities = List.of(1L);

        // when
        couponService.applyCoupon(couponId, memberId, bookIds, quantities);

        // then
        verify(couponClient).applyCoupon(eq(couponId), eq(memberId), any(CouponApplyRequest.class));
    }

    @DisplayName("쿠폰 사용 취소 호출 성공")
    @Test
    void withdrawCoupon_Success() {
        // given
        Long couponId = 1L;
        Long memberId = 100L;

        // when
        couponService.withdrawCoupon(couponId, memberId);

        // then
        verify(couponClient).rollbackCoupon(couponId, memberId);
    }

    @DisplayName("Fallback 메서드 호출 검증")
    @Test
    void fallbackMethods_CallHandler() {
        // given
        Throwable throwable = new RuntimeException("Error");

        // when
        couponService.fallbackCalculateDiscount(1L, 1L, List.of(), List.of(), throwable);
        couponService.fallbackApplyCoupon(1L, 1L, List.of(), List.of(), throwable);
        couponService.fallbackWithdrawCoupon(1L, 1L, throwable);

        // then
        verify(fallbackHandler).handle(any(), eq("쿠폰 할인가 계산"), eq(throwable));
        verify(fallbackHandler).handle(any(), eq("쿠폰 사용"), eq(throwable));
        verify(fallbackHandler).handle(any(), eq("쿠폰 사용 취소"), eq(throwable));
    }
}
