package com.nhnacademy.order.ordersaga.cancelation.domain;

import com.nhnacademy.order.ordersaga.domain.OrderSaga;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
public class OrderCancelSaga extends OrderSaga {
    @Setter
    @Enumerated(EnumType.STRING)
    private CancelSagaStep lastCompletedStep;

    private OrderCancelSaga(Long orderId) {
        super(orderId);
        this.lastCompletedStep = CancelSagaStep.STARTED;
    }

    public static OrderCancelSaga create(Long orderId) {
        return new OrderCancelSaga(orderId);
    }
}
