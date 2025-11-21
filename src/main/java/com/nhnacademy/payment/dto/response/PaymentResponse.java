package com.nhnacademy.payment.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PaymentResponse (
        Long paymentId,
        String orderNumber,
        Integer totalAmount,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        String receiptUrl
){
}
