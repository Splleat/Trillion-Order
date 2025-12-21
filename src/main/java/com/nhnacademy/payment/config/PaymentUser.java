package com.nhnacademy.payment.config;

public record PaymentUser(
        Long memberId,
        String guestId,
        String role,
        boolean isMember
) {
}
