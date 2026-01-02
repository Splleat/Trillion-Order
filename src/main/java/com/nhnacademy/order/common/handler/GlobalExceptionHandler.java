package com.nhnacademy.order.common.handler;

import com.nhnacademy.order.common.dto.ErrorResponse;
import com.nhnacademy.order.common.exception.AccessDeniedException;
import com.nhnacademy.order.delivery.exception.PolicyNotConfiguredException;
import com.nhnacademy.order.order.exception.OrderCreateFailureException;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.exception.OrderPasswordMismatchException;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.orderitem.exception.OrderItemNotFoundException;
import com.nhnacademy.order.packaging.exception.PackagingNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({
        OrderNotFoundException.class,
        OrderItemNotFoundException.class,
        PolicyNotConfiguredException.class,
        PackagingNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFoundException(Exception ex) {
        ErrorResponse errorResponse = ErrorResponse.create(ex.getMessage(), "NOT_FOUND");

        return ResponseEntity.status(404).body(errorResponse);
    }

    // @Valid 실패 시 발생하는 예외 (@RequestBody)
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
    })
    public ResponseEntity<ErrorResponse> handleMethodArgumentValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        ErrorResponse errorResponse = ErrorResponse.create(errorMessage, "VALIDATION_FAILED");

        return ResponseEntity.status(400).body(errorResponse);
    }

    // @Validated 실패 시 발생하는 예외 (@PathVariable, @RequestParam)
    @ExceptionHandler({
        ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        String errorMessage = ex.getConstraintViolations().iterator().next().getMessage();
        ErrorResponse errorResponse = ErrorResponse.create(errorMessage, "VALIDATION_FAILED");

        return ResponseEntity.status(400).body(errorResponse);
    }

    @ExceptionHandler({
        AccessDeniedException.class
    })
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorResponse errorResponse = ErrorResponse.create(ex.getMessage(), "FORBIDDEN");

        return ResponseEntity.status(403).body(errorResponse);
    }

    @ExceptionHandler({
        OrderPasswordMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handlePasswordMismatchException(OrderPasswordMismatchException ex) {
        ErrorResponse errorResponse = ErrorResponse.create(ex.getMessage(), "PASSWORD_MISMATCH");
        return ResponseEntity.status(401).body(errorResponse);
    }

    @ExceptionHandler({
        OrderStatusTransitionException.class
    })
    public ResponseEntity<ErrorResponse> handleStatusTransitionException(OrderStatusTransitionException ex) {
        ErrorResponse errorResponse = ErrorResponse.create(ex.getMessage(), "INVALID_STATUS_TRANSITION");
        return ResponseEntity.status(409).body(errorResponse);
    }

    @ExceptionHandler({
            OrderCreateFailureException.class
    })
    public ResponseEntity<ErrorResponse> handleOrderCreateFailureException(OrderCreateFailureException ex) {
        ErrorResponse errorResponse = ErrorResponse.create(ex.getMessage(), "SERVICE_UNAVAILABLE");
        return ResponseEntity.status(503).body(errorResponse);
    }

    @ExceptionHandler({
        Exception.class
    })
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        ErrorResponse errorResponse = ErrorResponse.create(ex.getMessage(), "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(500).body(errorResponse);
    }
}
