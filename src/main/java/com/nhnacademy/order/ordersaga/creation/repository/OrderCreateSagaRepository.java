package com.nhnacademy.order.ordersaga.creation.repository;

import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderCreateSagaRepository extends JpaRepository<OrderCreateSaga, UUID> {
    // 왜인지 작동이 안되는 것 같아서 일단 JPQL로 작성해봄
    @Query("""
        SELECT s
        FROM OrderCreateSaga s
        WHERE s.orderId = :orderId
    """)
    Optional<OrderCreateSaga> findByOrderId(@Param("orderId") Long orderId);

    List<OrderCreateSaga> findAllByOverallStatusInAndUpdatedAtBefore(List<SagaStatus> sagaStatuses, LocalDateTime updatedAt);
    List<OrderCreateSaga> findAllByOverallStatusAndBridgedFalseAndUpdatedAtBefore(SagaStatus sagaStatus, LocalDateTime updatedAt);
}
