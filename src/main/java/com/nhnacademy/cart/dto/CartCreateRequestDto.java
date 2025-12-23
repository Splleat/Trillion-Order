package com.nhnacademy.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CartCreateRequestDto {
    @NotNull(message = "도서 ID는 필수입니다.")
    private Long bookId;

    @NotNull(message = "수량 값은 필수입니다.")
    @Min(value = 1, message = "수량은 최소 1개 이상이어야 합니다.")
    @Max(value = 100_000_000, message = "수량은 최대 1억개까지 가능합니다.")
    private Integer cartQuantity;
}