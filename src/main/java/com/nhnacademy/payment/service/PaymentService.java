package com.nhnacademy.payment.service;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;

public interface PaymentService {
    Payment createPendingPayment(Long orderId);

    Payment ConfirmPayment(String paymentKey, String orderNumber);

    Payment getPaymentById(Long paymentId);

    void cancelPayment(Long paymentId, String cancelReason);
}
