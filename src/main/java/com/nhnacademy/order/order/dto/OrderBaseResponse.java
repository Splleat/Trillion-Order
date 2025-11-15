package com.nhnacademy.order.order.dto;

import com.nhnacademy.order.order.domain.OrdererInfo;
import com.nhnacademy.order.order.domain.PaymentStatus;
import com.nhnacademy.order.order.domain.ReceiverInfo;

import java.time.LocalDateTime;

public record OrderBaseResponse(
    Long orderId,
    String orderTitle,
    LocalDateTime orderDate,
    PaymentStatus paymentStatus,
    int totalPrice,
    OrdererInfo ordererInfo,
    ReceiverInfo receiverInfo
) {}
