package com.nhnacademy.payment.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Getter
@NoArgsConstructor
@ToString
public class TossPaymentResponseDto {
    private String paymentKey;
    private String status;      // "DONE", "CANCELED" 등
    private String orderId;     // Toss는 orderId라고 보냅니다.
    private String orderName;
    private Integer totalAmount;
    private String method;      // "카드", "가상계좌" 등
    private String requestedAt;
    private String approvedAt;  // ISO 8601 날짜 문자열
    private Receipt receipt;    // 중첩 객체 필수

    private List<Cancel> cancels;

    @Getter
    @NoArgsConstructor
    @ToString
    public static class Receipt {
        private String url; // 영수증 URL
    }

    @Getter
    @NoArgsConstructor
    @ToString
    public static class Cancel{
        private Integer cancelAmount;
        private String cancelReason;
    }
}
