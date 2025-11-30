package com.nhnacademy.order.client.dto;

import java.util.Map;
import java.util.UUID;

public record BookStockRequest(
    UUID sagaId,
    Map<Long, Integer> quantityMap
) {}
