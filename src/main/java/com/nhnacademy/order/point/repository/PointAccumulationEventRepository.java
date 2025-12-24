package com.nhnacademy.order.point.repository;

import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.domain.PointAccumulationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PointAccumulationEventRepository extends JpaRepository<PointAccumulationEvent, Long> {
    List<PointAccumulationEvent> findAllByStatusAndUpdatedAtBefore(PointAccumulationStatus status, LocalDateTime lastAttemptAt);
}
