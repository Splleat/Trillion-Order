package com.nhnacademy.order.ordersaga.cancellation.domain;

public enum CancelSagaStep {
    STARTED,            // 시작
    PAYMENT_CANCELED,   // 결제 취소
    COUPON_RESTORED,    // 쿠폰 복원
    POINT_REFUNDED,     // 포인트 환불
    STOCK_INCREASED     // 재고 증가
}
