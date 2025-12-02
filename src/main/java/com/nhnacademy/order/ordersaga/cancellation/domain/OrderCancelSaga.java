package com.nhnacademy.order.ordersaga.cancellation.domain;

import com.nhnacademy.order.ordersaga.domain.OrderSaga;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "order_cancel_saga")
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
