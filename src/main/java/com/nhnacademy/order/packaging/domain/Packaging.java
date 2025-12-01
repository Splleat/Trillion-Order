package com.nhnacademy.order.packaging.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Packaging {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "packaging_id")
    private Long packagingId;

    @Column(name = "packaging_type")
    private String packagingType;

    @Column(name = "packaging_price")
    private int packagingPrice;

    public Packaging(String packagingType, int packagingPrice) {
        this.packagingType = packagingType;
        this.packagingPrice = packagingPrice;
    }
}
