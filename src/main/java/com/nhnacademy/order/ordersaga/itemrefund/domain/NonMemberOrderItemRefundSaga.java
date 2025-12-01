package com.nhnacademy.order.ordersaga.itemrefund.domain;

import com.nhnacademy.order.ordersaga.domain.OrderSaga;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "non_member_order_item_refund_saga")
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
