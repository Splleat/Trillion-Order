package com.nhnacademy.payment.exception;

public class PaymentAlreadyApprovedException extends RuntimeException {
    public PaymentAlreadyApprovedException(String message) {
        super(message);
    }
}
