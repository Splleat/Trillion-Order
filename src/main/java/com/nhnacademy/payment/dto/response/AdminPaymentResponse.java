package com.nhnacademy.payment.dto.response;


import com.nhnacademy.payment.entity.Payment;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record AdminPaymentResponse(
        Long paymentId,
        String paymentKey,
        String orderNumber, //주문 번호
        Integer totalAmount, //결제 당시 총 금액
        String status, //결제 상태
        LocalDateTime requestedAt, //요청 날짜
        LocalDateTime approvedAt, //승인 날짜
        String receiptUrl
){
    public static AdminPaymentResponse from(Payment payment) {
        return AdminPaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentKey(payment.getPaymentKey())
                .orderNumber(payment.getOrder().getOrderNumber())
                .totalAmount(payment.getOrder().getOrderDetails().totalPrice())
                .status(payment.getPaymentStatus().toString())
                .requestedAt(payment.getPaymentRequestAt())
                .approvedAt(payment.getPaymentApprovedAt())
                .receiptUrl(payment .getPaymentReceipt())
                .build();
    }
}
