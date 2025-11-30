package com.nhnacademy.order.client.dto;

import java.util.UUID;

public record BookStockRequest(
    UUID sagaId,
    Long bookId,
    int quantity
) {}
