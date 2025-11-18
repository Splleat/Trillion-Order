package com.nhnacademy.payment.dto.reqeust;

public record PaymentRequestDto(
        Long orderId,
        Integer amount
) {
}
