package com.nhnacademy.order.ordersaga.cancellation.repository;

import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCancelSagaRepository extends JpaRepository<OrderCancelSaga, Long> {
}
