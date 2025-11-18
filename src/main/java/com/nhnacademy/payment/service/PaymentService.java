package com.nhnacademy.payment.service;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;

public interface PaymentService {
    Payment createPendingPayment(PaymentRequestDto request);

    Payment ConfirmPayment(String paymentKey, String orderNumber, Integer amount);

    Payment getPaymentById(Long paymentId);

    void cancelPayment(Long paymentId, String cancelReason);
}
