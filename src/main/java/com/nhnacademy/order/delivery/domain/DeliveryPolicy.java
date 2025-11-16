package com.nhnacademy.order.delivery.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "delivery_policy")
public class DeliveryPolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int fee;

    @Column(name = "free_delivery_threshold")
    private int freeDeliveryThreshold;
}
