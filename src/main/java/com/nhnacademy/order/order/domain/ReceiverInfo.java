package com.nhnacademy.order.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ReceiverInfo(
    @Column(name = "receiver_name")
    String receiverName,

    @Column(name = "receiver_contact")
    String receiverContact,

    @Column(name = "receiver_address")
    String receiverAddress
) {}
