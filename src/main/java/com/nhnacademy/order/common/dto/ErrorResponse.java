package com.nhnacademy.order.common.dto;

public record ErrorResponse(
    String message,
    String code
) {
    public static ErrorResponse create(String message, String code) {
        return new ErrorResponse(message, code);
    }
}
