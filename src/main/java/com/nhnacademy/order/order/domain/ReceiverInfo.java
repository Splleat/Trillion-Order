package com.nhnacademy.order.order.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record ReceiverInfo(
    String receiverName,
    String receiverContact,
    String receiverAddress
) {}
