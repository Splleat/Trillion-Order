package com.nhnacademy.order.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NonMemberOrderCancelRequest {
    @NotBlank
    private String nonMemberPassword;
}
