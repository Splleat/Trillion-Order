package com.nhnacademy.order.order.exception;

public class OrderCancelFailureException extends RuntimeException {
    public OrderCancelFailureException(String message) {
        super(message);
    }
}
