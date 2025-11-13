package com.nhnacademy.order.order.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record OrdererInfo(
    String ordererNumber,
    String ordererName,
    String ordererContact
) {}
