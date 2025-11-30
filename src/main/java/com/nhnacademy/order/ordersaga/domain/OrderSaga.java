package com.nhnacademy.order.ordersaga.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@MappedSuperclass
public abstract class OrderSaga {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID sagaId;

    @Column(nullable = false)
    private Long orderId;

    @Setter
    @Enumerated(EnumType.STRING)
    private SagaStatus overallStatus;

    protected OrderSaga(Long orderId) {
        this.orderId = orderId;
        this.overallStatus = SagaStatus.PROGRESS;
    }

    protected OrderSaga() {}
}
