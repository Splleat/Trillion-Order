package com.nhnacademy.order.ordersaga.creation.domain;

public enum CreateSagaStep {
    STARTED,            // 시작
    STOCK_DECREASED,    // 도서 재고 감소
    COUPON_APPLIED,     // 쿠폰 적용
    POINT_USED          // 포인트 사용
}
