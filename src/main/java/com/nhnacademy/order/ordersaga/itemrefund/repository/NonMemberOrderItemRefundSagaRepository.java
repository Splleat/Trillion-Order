package com.nhnacademy.order.ordersaga.itemrefund.repository;

import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberOrderItemRefundSaga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NonMemberOrderItemRefundSagaRepository extends JpaRepository<NonMemberOrderItemRefundSaga, Long> {
}
