package com.nhnacademy.order.client.member.dto;

import lombok.Builder;

@Builder
public record PointAccumulationRequest(
    Long memberId,
    Long orderId,
    int purchaseAmount
) {
}
