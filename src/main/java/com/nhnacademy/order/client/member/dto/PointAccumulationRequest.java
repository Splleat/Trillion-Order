package com.nhnacademy.order.client.member.dto;

public record PointAccumulationRequest(
    Long memberId,
    Long orderId,
    int purchaseAmount
) {
}
