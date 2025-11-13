package com.nhnacademy.order.orderitem.repository;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
