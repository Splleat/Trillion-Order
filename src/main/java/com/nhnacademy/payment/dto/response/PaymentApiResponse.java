package com.nhnacademy.payment.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
public record PaymentApiResponse(
        String paymentKey,
        String orderId,
        Integer totalAmount,
        String status,
        String requestedAt,
        String approvedAt,
        String receiptUrl,
        String provider // "TOSS", "KAKAO" 등
) {}
