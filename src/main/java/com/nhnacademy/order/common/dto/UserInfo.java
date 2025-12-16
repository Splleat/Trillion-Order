package com.nhnacademy.order.common.dto;

public record UserInfo(
    Long userId,
    String guestId,
    String role
) {}
