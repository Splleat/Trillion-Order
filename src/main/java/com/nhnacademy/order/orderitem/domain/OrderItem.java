package com.nhnacademy.order.orderitem.domain;

import com.nhnacademy.order.common.entity.BaseTimeEntity;
import com.nhnacademy.order.order.domain.Order;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orderitem_id")
    private Long orderItemId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "book_id")
    private Long bookId;

    private String bookName;

    private String bookImage;

    private int quantity;

    private int price;

    @Setter
    private int couponDiscountAmount;

    @Column(name = "shipping_date")
    private LocalDateTime shippingDate; // 출고일

    @Embedded
    private PackagingInfo packagingInfo;

    @Setter
    @Column(name = "orderitem_status")
    @Enumerated(value = EnumType.STRING)
    private OrderItemStatus orderItemStatus;

    @Setter
    @Column(name = "payment_point")
    private int paymentPoint;

    @Setter
    @Column(name = "refund_price")
    private int refundPrice;

    public static OrderItem createInitial(Order order, Long bookId, int quantity, LocalDateTime shippingDate, PackagingInfo packagingInfo) {
        return new OrderItem(
            order,
            bookId,
            null,
            null,
            quantity,
            0,
            0,
            shippingDate,
            packagingInfo
        );
    }

    private OrderItem(Order order, Long bookId, String bookName, String bookImage, int quantity, Integer price, int couponDiscountAmount, LocalDateTime shippingDate, PackagingInfo packagingInfo) {
        this.orderItemId = null;
        this.order = order;
        this.bookId = bookId;
        this.bookName = bookName;
        this.bookImage = bookImage;
        this.quantity = quantity;
        this.price = price;
        this.couponDiscountAmount = couponDiscountAmount;
        this.shippingDate = shippingDate;
        this.packagingInfo = packagingInfo;
        this.orderItemStatus = OrderItemStatus.PREPARING;
        this.paymentPoint = 0;
        this.refundPrice = 0;
    }

    public void completeOrderItem(String bookName, String bookImage, int price, int couponDiscountAmount) {
        this.bookName = bookName;
        this.bookImage = bookImage;
        this.price = price;
        this.couponDiscountAmount = couponDiscountAmount;
    }

    public void ship() {
        this.orderItemStatus = OrderItemStatus.SHIPPED;
    }

    public void delivered() {
        this.orderItemStatus = OrderItemStatus.DELIVERED;

        if (this.shippingDate == null) {
            this.shippingDate = LocalDateTime.now();
        }
    }
}
