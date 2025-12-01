package com.nhnacademy.order.ordersaga.itemrefund.domain;

public enum NonMemberRefundSagaStep {
    STARTED,            // 시작
    PAYMENT_REFUNDED,   // 포인트 환불
    STOCK_INCREASED     // 재고 증가
}
