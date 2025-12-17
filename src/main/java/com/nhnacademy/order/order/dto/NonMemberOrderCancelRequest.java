package com.nhnacademy.order.order.dto;

import jakarta.validation.constraints.NotBlank;

public record NonMemberOrderCancelRequest(
    @NotBlank(message = "비회원 주문 비밀번호는 필수입니다.")
    String nonMemberPassword
) {}
