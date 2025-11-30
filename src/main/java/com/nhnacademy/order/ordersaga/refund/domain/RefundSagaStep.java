package com.nhnacademy.order.ordersaga.refund.domain;

public enum RefundSagaStep {
    STARTED,            // 시작
    COUPON_RESTORED,    // 쿠폰 복원
    POINT_REFUNDED,     // 포인트 환불
    STOCK_INCREASED     // 재고 증가
}
