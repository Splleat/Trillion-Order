package com.nhnacademy.order.order.exception;

public class OrderStatusTransitionException extends RuntimeException {
    public OrderStatusTransitionException(String message) {
        super(message);
    }
}
