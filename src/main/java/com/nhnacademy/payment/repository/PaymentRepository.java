package com.nhnacademy.payment.repository;

import com.nhnacademy.payment.entity.Payment;
import io.github.resilience4j.core.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findByOrder_OrderNumber(String orderNumber);

    //관리자 전용 결제 내역 단건 조회.
    @NonNull
    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findById(@NonNull Long id);

    //관리자 전용 결제 내역 전체 조회
    @NonNull
    @EntityGraph(attributePaths = {"order"})
    Page<Payment> findAll(@NonNull Pageable pageable);

    //회원 전용 결제 내역 전체 조회.
    @EntityGraph(attributePaths = {"order"})
    Page<Payment> findByOrder_MemberId(Long memberId, Pageable pageable);

    //회원 전용 결제 내역 단건 조회
    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findByOrder_OrderNumberAndOrder_MemberId(String orderNumber, Long memberId);


}
