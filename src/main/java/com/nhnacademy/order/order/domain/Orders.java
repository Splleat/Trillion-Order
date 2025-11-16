package com.nhnacademy.order.order.domain;

import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.exception.PolicyNotConfiguredException;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.orderitem.exception.OrderItemNotFoundException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Orders {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    private String orderNumber;

    private Long memberId;

    private String nonMemberPassword;

    private PaymentStatus paymentStatus;

    @Embedded
    private OrdererInfo ordererInfo;

    @Embedded
    private ReceiverInfo receiverInfo;

    @Embedded
    private OrderDetails orderDetails;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Orders create(Long memberId, String encryptedPassword, OrdererInfo ordererInfo, ReceiverInfo receiverInfo, OrderDetails orderDetails) {

        return new Orders(
            null,
            UUID.randomUUID().toString(),
            memberId,
            encryptedPassword,
            PaymentStatus.PENDING,
            ordererInfo,
            receiverInfo,
            orderDetails,
            new ArrayList<>()
        );
    }

    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);
        orderItem.setOrder(this);
    }
}
