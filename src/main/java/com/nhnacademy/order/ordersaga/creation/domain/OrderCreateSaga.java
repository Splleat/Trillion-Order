package com.nhnacademy.order.ordersaga.creation.domain;

import com.nhnacademy.order.ordersaga.domain.OrderSaga;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
public class OrderCreateSaga extends OrderSaga {
    @Setter
    @Enumerated(EnumType.STRING)
    private CreateSagaStep lastCompletedStep; // 마지막으로 성공한 단계

    private OrderCreateSaga(Long orderId) {
        super(orderId);
        this.lastCompletedStep = CreateSagaStep.STARTED;
    }

    public static OrderCreateSaga create(Long orderId) {
        return new OrderCreateSaga(orderId);
    }
}
