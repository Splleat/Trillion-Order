package com.nhnacademy.order.orderitem.dto;

import com.nhnacademy.order.orderitem.domain.OrderItemStatus;

public record NonMemberOrderItemStatusPatchRequest(
    String nonMemberPassword,
    OrderItemStatus status
) {}
