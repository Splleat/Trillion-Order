package com.nhnacademy.order.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record OrdererInfo(
    @Column(name = "orderer_name")
    String ordererName,

    @Column(name = "orderer_contact")
    String ordererContact
) {}
