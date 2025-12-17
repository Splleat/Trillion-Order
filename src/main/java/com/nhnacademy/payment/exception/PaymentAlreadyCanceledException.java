package com.nhnacademy.payment.exception;

public class PaymentAlreadyCanceledException extends RuntimeException {
    public PaymentAlreadyCanceledException(String message) {
        super(message);
    }
}
