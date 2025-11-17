package com.nhnacademy.order.orderitem.domain;

import com.nhnacademy.order.order.domain.Orders;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;

    private Long bookId;

    private int quantity;
    private int price;
    private OrderItemStatus orderItemStatus;
    private Long couponId;
    private Long packagingId;

    public static OrderItem create(Orders order, Long bookId, int quantity, int price, Long couponId, Long packagingId) {
        return new OrderItem(
            null,
            order,
            bookId,
            quantity,
            price,
            OrderItemStatus.PREPARING,
            couponId,
            packagingId
        );
    }

    public void setOrder(Orders order) {
        this.order = order;
    }

    public void ship() {
        this.orderItemStatus = OrderItemStatus.SHIPPED;
    }

    public void requestReturn() {
        this.orderItemStatus = OrderItemStatus.RETURN_REQUESTED;
    }

    public void completeReturn() {
        this.orderItemStatus = OrderItemStatus.RETURNED;
    }

    public void cancel() {
        this.orderItemStatus = OrderItemStatus.CANCELED;
    }
}
