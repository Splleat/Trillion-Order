package com.nhnacademy.payment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nhnacademy.order.order.domain.Orders;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.query.Order;

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

    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders orders; //주문 id

    @Column(name = "amount", nullable = false) //해당 트랜잭션에 필수 금액이라고?
    @NotNull
    private Integer amount;

    @Builder
    public Payment(Orders orders, Integer amount, PaymentStatus paymentStatus, LocalDateTime paymentRequestAt) {
        this.orders = orders;
        this.amount = amount;
        this.paymentStatus = paymentStatus;
        this.paymentRequestAt = paymentRequestAt;
    }


    //결제 승인시 처리 메서드
    public void approvePayment(String paymentKey, String paymentReceipt, LocalDateTime paymentApprovedAt) {
        this.paymentKey = paymentKey;
        this.paymentStatus = PaymentStatus.DONE;
        this.paymentApprovedAt = paymentApprovedAt;
        this.paymentReceipt = paymentReceipt;
    }

    //결제 취소 메서드
    public void cancelPayment() {
        this.paymentStatus = PaymentStatus.CANCEL;
    }
}
