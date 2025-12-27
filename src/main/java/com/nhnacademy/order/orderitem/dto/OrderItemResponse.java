package com.nhnacademy.order.orderitem.dto;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;

public record OrderItemResponse(
    Long orderItemId,
    Long orderId,
    Long bookId,
    String bookName,
    String bookImage,
    int quantity,
    int unitPrice, // 단가
    int totalItemOriginalPrice, // 총 원래 상품 가격 ((단가 + 포장비) * 수량)
    int itemDiscountAmount, // 이 상품 라인에 적용된 할인액
    int totalItemSalePrice, // 이 상품 라인의 최종 결제 금액 (totalItemOriginPrice - 아이템 할인액)
    int packagingPrice,
    int refundPrice,
    OrderItemStatus orderItemStatus
) {
    public static OrderItemResponse create(OrderItem orderItem) {
        int unitPrice = orderItem.getPrice(); // OrderItem.price는 단가
        int quantity = orderItem.getQuantity();
        int itemDiscountAmount = orderItem.getCouponDiscountAmount(); // OrderItem.couponDiscountAmount는 이 라인의 총 할인액

        int totalOriginalPrice = unitPrice * quantity;
        int totalSalePrice = totalOriginalPrice - itemDiscountAmount;

        return new OrderItemResponse(
            orderItem.getOrderItemId(),
            orderItem.getOrder().getOrderId(),
            orderItem.getBookId(),
            orderItem.getBookName(),
            orderItem.getBookImage(),
            quantity,
            unitPrice,
            totalOriginalPrice,
            itemDiscountAmount,
            totalSalePrice,
            orderItem.getPackagingInfo().packagingPrice(),
            orderItem.getRefundPrice(),
            orderItem.getOrderItemStatus()
        );
    }
}
