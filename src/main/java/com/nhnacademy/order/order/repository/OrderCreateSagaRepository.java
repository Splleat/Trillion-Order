package com.nhnacademy.order.order.repository;

import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCreateSagaRepository extends JpaRepository<OrderCreateSaga, Long> {
}
