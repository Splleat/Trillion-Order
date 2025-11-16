package com.nhnacademy.order.order.dto;

public record GuestOrderGetRequest(
    String orderNumber,
    String nonMemberPassword
) {}
