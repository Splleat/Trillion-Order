package com.nhnacademy.order.packaging.repository;

import com.nhnacademy.order.packaging.domain.Packaging;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackagingRepository extends JpaRepository<Packaging, Long> {
    Packaging findByPackagingId(Long packagingId);
}
