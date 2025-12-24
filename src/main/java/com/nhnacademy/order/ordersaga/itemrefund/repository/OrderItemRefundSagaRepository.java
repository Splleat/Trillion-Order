package com.nhnacademy.order.ordersaga.itemrefund.repository;

import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderItemRefundSagaRepository extends JpaRepository<OrderItemRefundSaga, UUID> {
    List<OrderItemRefundSaga> findAllByOverallStatusInAndUpdatedAtBefore(List<SagaStatus> sagaStatuses, LocalDateTime updatedAt);
    List<OrderItemRefundSaga> findAllByOverallStatusAndBridgedFalseAndUpdatedAtBefore(SagaStatus overallStatus, LocalDateTime updatedAt);
}
