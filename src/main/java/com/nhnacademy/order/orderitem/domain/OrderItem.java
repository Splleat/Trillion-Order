package com.nhnacademy.order.orderitem.domain;

import com.nhnacademy.order.order.domain.Orders;
import jakarta.persistence.*;

@Entity
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders order;

    private Long bookId;
    private int quantity;
    private int price;
    private OrderItemStatus orderItemStatus;
    private Long couponId;
    private Long packagingId;
}
