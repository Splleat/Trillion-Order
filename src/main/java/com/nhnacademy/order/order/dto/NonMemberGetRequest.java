package com.nhnacademy.order.order.dto;

import jakarta.validation.constraints.NotBlank;

public record NonMemberGetRequest(
    String orderNumber,

    @NotBlank
    String nonMemberPassword
) {}
