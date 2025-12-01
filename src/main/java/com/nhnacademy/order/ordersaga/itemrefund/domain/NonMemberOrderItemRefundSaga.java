package com.nhnacademy.order.ordersaga.itemrefund.domain;

import com.nhnacademy.order.ordersaga.domain.OrderSaga;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
public class NonMemberOrderItemRefundSaga extends OrderSaga {
    @Column(nullable = false)
    Long orderItemId;

    @Setter
    @Enumerated(EnumType.STRING)
    private NonMemberRefundSagaStep lastCompletedStep;

    private NonMemberOrderItemRefundSaga(Long orderId, Long orderItemId) {
        super(orderId);
        this.orderItemId = orderItemId;
        this.lastCompletedStep = NonMemberRefundSagaStep.STARTED;
    }

    public static NonMemberOrderItemRefundSaga create(Long orderId, Long orderItemId) {
        return new NonMemberOrderItemRefundSaga(orderId, orderItemId);
    }
}
