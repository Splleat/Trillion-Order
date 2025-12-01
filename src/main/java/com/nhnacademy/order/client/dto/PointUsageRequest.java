package com.nhnacademy.order.client.dto;

import java.util.UUID;

public record PointUsageRequest(
    UUID sagaId,
    Long memberId,
    int point
) {}
