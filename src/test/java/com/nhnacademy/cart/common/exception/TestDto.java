package com.nhnacademy.cart.common.exception;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TestDto {
    @NotNull(message = "필수값입니다")
    private String name;
}