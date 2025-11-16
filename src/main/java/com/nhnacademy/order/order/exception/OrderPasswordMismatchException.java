package com.nhnacademy.order.order.exception;

public class OrderPasswordMismatchException extends RuntimeException {
    public OrderPasswordMismatchException(String message) {
        super(message);
    }
}
