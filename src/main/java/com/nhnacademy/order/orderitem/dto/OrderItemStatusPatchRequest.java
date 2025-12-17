package com.nhnacademy.order.orderitem.dto;

import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import jakarta.validation.constraints.NotNull;

public record OrderItemStatusPatchRequest(
    @NotNull(message = "주문 상품 상태는 필수입니다.")
    OrderItemStatus status
) {}
