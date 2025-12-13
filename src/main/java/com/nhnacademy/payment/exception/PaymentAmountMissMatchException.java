package com.nhnacademy.payment.exception;

public class PaymentAmountMissMatchException extends RuntimeException {
    public PaymentAmountMissMatchException(String message) {
        super(message);
    }
}
