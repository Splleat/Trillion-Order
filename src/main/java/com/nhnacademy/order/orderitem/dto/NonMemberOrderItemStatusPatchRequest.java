package com.nhnacademy.order.orderitem.dto;

import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NonMemberOrderItemStatusPatchRequest(
    @NotBlank(message = "비회원 주문 비밀번호는 필수입니다.")
    String nonMemberPassword,
    @NotNull(message = "주문 상품 상태는 필수입니다.")
    OrderItemStatus status
) {}
