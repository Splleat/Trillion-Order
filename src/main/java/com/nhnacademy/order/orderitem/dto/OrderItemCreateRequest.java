package com.nhnacademy.order.orderitem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record OrderItemCreateRequest(
    @NotNull(message = "도서 ID는 필수입니다.")
    @Positive(message = "도서 ID는 양수여야 합니다.")
    Long bookId,
    @Min(value = 1, message = "수량은 최소 1개 이상이어야 합니다.")
    int quantity,
    Long couponId,
    Long packagingId,
    LocalDateTime shippingDate
) {}
