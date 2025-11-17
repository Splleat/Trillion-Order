package com.nhnacademy.payment.dto.reqeust;

public record PaymentRequestDto(
        Long saleId,
        Long amount
) {
}
