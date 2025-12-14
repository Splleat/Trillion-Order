package com.nhnacademy.payment.exception;

public class PaymentNotApprovedException extends RuntimeException {
    public PaymentNotApprovedException(String message) {
        super(message);
    }
}
