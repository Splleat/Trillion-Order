package com.nhnacademy.payment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nhnacademy.order.order.domain.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "Payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "payment_status")
    @NotNull
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Column(name = "payment_requested_at")
    private LocalDateTime paymentRequestAt;//결제 요청일

    @Column(name = "payment_approved_at")
    private LocalDateTime paymentApprovedAt; // 결제 승인일

    @Column(name = "payment_receipt")
    private String paymentReceipt;

    @OneToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Builder
    public Payment(String paymentKey, PaymentStatus paymentStatus, LocalDateTime paymentRequestAt,
                   LocalDateTime paymentApprovedAt, String paymentReceipt, Order order) {
        this.paymentKey = paymentKey;
        this.paymentStatus = paymentStatus;
        this.paymentRequestAt = paymentRequestAt;
        this.paymentApprovedAt = paymentApprovedAt;
        this.paymentReceipt = paymentReceipt;
        this.order = order;
    }

    //결제 취소 메서드
    public void cancelPayment() {
        this.paymentStatus = PaymentStatus.CANCELED;
    }
}
