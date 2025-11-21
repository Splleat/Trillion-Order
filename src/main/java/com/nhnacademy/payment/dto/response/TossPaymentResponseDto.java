package com.nhnacademy.payment.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@ToString
public class TossPaymentResponseDto {
    private String paymentKey;
    private String status; // "DONE" 등 상태
    private String orderId;
    private String orderName;
    private Integer totalAmount;
    private String method;
    private String requestedAt;
    private String approvedAt; // ISO 8601 날짜 문자열
    private Receipt receipt;   // 영수증 정보 객체

    @Getter
    @NoArgsConstructor
    @ToString
    public static class Receipt {
        private String url; // 영수증 URL
    }

}
