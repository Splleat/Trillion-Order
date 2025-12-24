package com.nhnacademy.order.ordersaga.cancellation.repository;

import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderCancelSagaRepository extends JpaRepository<OrderCancelSaga, UUID> {
    List<OrderCancelSaga> findAllByOverallStatusInAndUpdatedAtBefore(List<SagaStatus> sagaStatuses, LocalDateTime updatedAt);
    List<OrderCancelSaga> findAllByOverallStatusAndBridgedFalseAndUpdatedAtBefore(SagaStatus sagaStatus, LocalDateTime updatedAt);

    Optional<OrderCancelSaga> findByOrderId(Long orderId);
}
