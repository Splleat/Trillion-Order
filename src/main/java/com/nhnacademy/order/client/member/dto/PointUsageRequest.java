package com.nhnacademy.order.client.member.dto;

public record PointUsageRequest(
    Long memberId,
    Long orderId,
    int point
) {}
