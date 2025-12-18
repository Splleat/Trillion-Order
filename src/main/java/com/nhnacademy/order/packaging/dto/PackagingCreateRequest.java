package com.nhnacademy.order.packaging.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record PackagingCreateRequest(
        @NotBlank(message = "포장 정보는 비어 있을 수 없습니다.")
        String packagingType,
        @Min(value = 0, message = "포장지 가격은 0원 이상이어야 합니다.")
        int packagingPrice
) {
}
