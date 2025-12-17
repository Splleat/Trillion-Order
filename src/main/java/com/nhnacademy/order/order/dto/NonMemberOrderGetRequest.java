package com.nhnacademy.order.order.dto;

import jakarta.validation.constraints.NotBlank;

public record NonMemberOrderGetRequest(
    @NotBlank(message = "주문 번호는 필수입니다.")
    String orderNumber,

    @NotBlank(message = "비회원 주문 비밀번호는 필수입니다.")
    String nonMemberPassword
) {}
