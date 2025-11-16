package com.nhnacademy.order.delivery.repository;

import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryPolicyRepository extends JpaRepository<DeliveryPolicy, Long> {
    Optional<DeliveryPolicy> findFirstByOrderByIdAsc();
}
