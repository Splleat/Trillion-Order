package com.nhnacademy.order.ordersaga.itemrefund.repository;

import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRefundSagaRepository extends JpaRepository<OrderItemRefundSaga, Long> {
}
