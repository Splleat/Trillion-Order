package com.nhnacademy.payment.repository;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    //Optional<Payment> findByOrder_OrderNumber(String orderNumber);

    Payment findByOrder_OrderNumber(String orderNumber);

    Payment findByPaymentId(Long paymentId);

    Payment findByPaymentKey(String paymentKey);
}
