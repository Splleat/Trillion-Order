package com.nhnacademy.payment.service;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;

public interface PaymentService {
    Payment createPendingPayment(PaymentRequestDto request);

    Payment ConfirmPayment(String paymentKey, Long saleId, Long amount);

    Payment getPaymentById(Long paymentId);
}
