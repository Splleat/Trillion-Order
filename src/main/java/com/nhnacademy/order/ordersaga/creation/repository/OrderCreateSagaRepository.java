package com.nhnacademy.order.ordersaga.creation.repository;

import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderCreateSagaRepository extends JpaRepository<OrderCreateSaga, Long> {
}
