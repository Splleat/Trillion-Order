package com.nhnacademy.order.orderitem.domain;

public enum OrderItemStatus {
    PREPARING,  // 상품 준비중
    SHIPPED,    // 배송중
    DELIVERED,  // 배송 완료
    RETURNED,   // 반품 완료

    CONFIRMED,  // 구매 확정
    CANCELED,   // 주문 취소

    RETURN_REQUESTED_CHANGE_OF_MIND,    // 반품 요청 (단순 변심)
    RETURN_REQUESTED_DAMAGED,           // 반품 요청 (파손)
}
