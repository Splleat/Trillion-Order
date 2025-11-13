package com.nhnacademy.order.orderitem.domain;

public enum OrderItemStatus {
    PREPARING, // 상품 준비중
    SHIPPED, // 배송중
    DELIVERED, // 배송 완료
    PURCHASE_CONFIRMED, // 구매 확정
    RETURN_REQUESTED, // 반품 요청
    RETURNED, // 반품 완료
    REFUNDED // 환불 완료
}
