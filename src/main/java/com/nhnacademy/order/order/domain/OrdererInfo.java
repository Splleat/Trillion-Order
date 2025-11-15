package com.nhnacademy.order.order.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record OrdererInfo(
    String ordererName,
    String ordererContact
) {}
