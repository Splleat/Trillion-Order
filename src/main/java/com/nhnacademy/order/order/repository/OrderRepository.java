package com.nhnacademy.order.order.repository;

import com.nhnacademy.order.order.domain.Orders;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Orders, Long> {
}
