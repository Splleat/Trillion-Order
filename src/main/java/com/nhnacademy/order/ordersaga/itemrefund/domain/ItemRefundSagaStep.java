package com.nhnacademy.order.ordersaga.itemrefund.domain;

public enum ItemRefundSagaStep {
    STARTED,            // 시작
    POINT_REFUNDED,     // 포인트 환불
    STOCK_INCREASED     // 재고 증가
}
