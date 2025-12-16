package com.nhnacademy.order.client.coupon.service;

import com.nhnacademy.order.client.coupon.CouponClient;
import com.nhnacademy.order.client.coupon.dto.CouponApplyRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import com.nhnacademy.order.ordercoupon.domain.CouponType; // 추가
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Profile("local")
@Component
public class MockCouponClient implements CouponClient {

    private static final long CART_COUPON_ID = 1L;
    private static final long CATEGORY_COUPON_ID = 2L;
    private static final long BOOK_COUPON_ID = 3L;

    private static final long TARGET_CATEGORY_ID = 101L;
    private static final long TARGET_BOOK_ID = 99L;

    @Override
    public CouponCalculationResponse calculateDiscount(CouponCalculationRequest request) {
        log.info("MockCouponClient 쿠폰 할인가 계산: {}", request);

        if (request.couponId() == null) {
            return new CouponCalculationResponse(null, null, null, 0, List.of());
        }

        List<CouponCalculationResponse.ItemDiscount> itemDiscounts = new ArrayList<>();
        int totalDiscount = 0;

        if (Objects.equals(request.couponId(), CART_COUPON_ID)) {
            // Mock: 10% 장바구니 할인
            int totalOriginPrice = request.items().stream()
                                          .mapToInt(item -> item.price() * item.quantity())
                                          .sum();
            double discountRate = 0.10;
            int cartDiscountTotal = (int) (totalOriginPrice * discountRate);
            
            // 할인액 분배
            for (CouponCalculationRequest.CouponCalculationOrderItem item : request.items()) {
                double itemPrice = item.price() * item.quantity();
                int itemDiscount = (int) Math.round(cartDiscountTotal * (itemPrice / totalOriginPrice));
                itemDiscounts.add(new CouponCalculationResponse.ItemDiscount(item.bookId(), itemDiscount));
                totalDiscount += itemDiscount;
            }
            return new CouponCalculationResponse(request.couponId(), CouponType.CART, null, totalDiscount, itemDiscounts);

        } else if (Objects.equals(request.couponId(), CATEGORY_COUPON_ID)) {
            // Mock: 카테고리(101L) 5,000원 할인
            for (CouponCalculationRequest.CouponCalculationOrderItem item : request.items()) {
                if (item.categoryIds().contains(TARGET_CATEGORY_ID)) {
                    int discountAmount = Math.min(item.price() * item.quantity(), 5000); // 상품 금액을 넘지 않도록
                    itemDiscounts.add(new CouponCalculationResponse.ItemDiscount(item.bookId(), discountAmount));
                    totalDiscount += discountAmount;
                }
            }
            return new CouponCalculationResponse(request.couponId(), CouponType.CATEGORY, TARGET_CATEGORY_ID, totalDiscount, itemDiscounts);

        } else if (Objects.equals(request.couponId(), BOOK_COUPON_ID)) {
            // Mock: 특정 책(99L) 1,000원 할인
            for (CouponCalculationRequest.CouponCalculationOrderItem item : request.items()) {
                if (Objects.equals(item.bookId(), TARGET_BOOK_ID)) {
                    int discountAmount = Math.min(item.price() * item.quantity(), 1000); // 상품 금액을 넘지 않도록
                    itemDiscounts.add(new CouponCalculationResponse.ItemDiscount(item.bookId(), discountAmount));
                    totalDiscount += discountAmount;
                }
            }
            return new CouponCalculationResponse(request.couponId(), CouponType.BOOK, TARGET_BOOK_ID, totalDiscount, itemDiscounts);

        } else {
            // Not a recognized mock coupon
            log.warn("Unrecognized mock couponId: {}", request.couponId());
            return new CouponCalculationResponse(null, null, null, 0, List.of()); // Updated
        }
    }

    @Override
    public void applyCoupon(CouponApplyRequest request) {
        log.info("MockCouponClient 쿠폰 사용 요청: {}", request);
    }

    @Override
    public void withdrawCoupon(CouponApplyRequest request) {
        log.info("MockCouponClient 쿠폰 사용 취소 요청: {}", request);
    }

    @Override
    public void rollbackCoupon(CouponApplyRequest request) {
        log.info("MockCouponClient 쿠폰 복원 요청: {}", request);
    }
}