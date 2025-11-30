package com.nhnacademy.order.order.repository;

import com.nhnacademy.order.ordersaga.cancelation.domain.OrderCancelSaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCancelSagaRepository extends JpaRepository<OrderCancelSaga, Long> {
}
