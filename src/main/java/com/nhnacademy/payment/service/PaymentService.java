package com.nhnacademy.payment.service;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse ConfirmPayment(PaymentRequestDto request);

    PaymentResponse getPaymentById(Long paymentId);

    void cancelPayment(String orderNumber, String cancelReason);
}
