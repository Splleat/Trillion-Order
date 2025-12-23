package com.nhnacademy.order.orderitem.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public record PackagingInfo(
    String packagingType,
    int packagingPrice
) {
    public static PackagingInfo create(String packagingType, int packagingPrice) {
        return new PackagingInfo(packagingType, packagingPrice);
    }
}
