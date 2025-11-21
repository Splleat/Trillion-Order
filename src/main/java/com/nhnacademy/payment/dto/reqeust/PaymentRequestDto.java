package com.nhnacademy.payment.dto.reqeust;

public record PaymentRequestDto(
       String paymentKey,
       String orderNumber,
       Integer amount
) {
}
