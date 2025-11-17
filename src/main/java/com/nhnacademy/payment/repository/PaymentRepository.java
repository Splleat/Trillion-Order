package com.nhnacademy.payment.repository;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findBySaleIdAndPaymentStatus(Long saleId, PaymentStatus paymentStatus);

    Payment findByPaymentId(Long paymentId);
}
