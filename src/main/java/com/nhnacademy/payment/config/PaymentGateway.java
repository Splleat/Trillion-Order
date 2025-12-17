package com.nhnacademy.payment.config;

import com.nhnacademy.payment.dto.response.PaymentApiResponse;

public interface PaymentGateway {
    PaymentApiResponse confirm(String paymentKey, String orderNumber, Integer amount);
    PaymentApiResponse cancel(String paymentKey, String cancelReason, Integer Amount);
    boolean supports(String providerName);
}
