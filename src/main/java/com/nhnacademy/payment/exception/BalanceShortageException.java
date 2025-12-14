package com.nhnacademy.payment.exception;

public class BalanceShortageException extends RuntimeException {
    public BalanceShortageException(String message) {
        super(message);
    }
}
