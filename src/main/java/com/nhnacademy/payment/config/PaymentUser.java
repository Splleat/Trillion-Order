package com.nhnacademy.payment.config;

public record PaymentUser(
        Long memberId,
        Long guestId,
        String role,
        boolean isMember
) {
}
