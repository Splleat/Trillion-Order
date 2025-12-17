package com.nhnacademy.order.client.member.dto;

public record PointUsageRequest(
    Long memberId,
    int point
) {}
