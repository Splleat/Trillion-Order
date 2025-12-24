package com.nhnacademy.order.point.domain;

import com.nhnacademy.order.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccumulationEvent extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    private Long orderId;

    private Long orderItemId;

    private int purchaseAmount;

    @Enumerated(EnumType.STRING)
    private PointAccumulationStatus status;

    private int retryCount;

    @Column(columnDefinition = "TEXT")
    private String lastErrorMessage;

    private PointAccumulationEvent(Long memberId, Long orderId, Long orderItemId, int purchaseAmount) {
        this.memberId = memberId;
        this.orderId = orderId;
        this.orderItemId = orderItemId;
        this.purchaseAmount = purchaseAmount;
        this.status = PointAccumulationStatus.PENDING;
        this.retryCount = 0;
    }

    public static PointAccumulationEvent create(Long memberId, Long orderId, Long orderItemId, int purchaseAmount) {
        return new PointAccumulationEvent(memberId, orderId, orderItemId, purchaseAmount);
    }

    public void markAsCompleted() {
        this.status = PointAccumulationStatus.COMPLETED;
    }

    public void recordFailure(String errorMessage) {
        this.status = PointAccumulationStatus.FAILED;
        this.lastErrorMessage = errorMessage;
    }

    public void requeueForRetry() {
        this.status = PointAccumulationStatus.PENDING;
        this.retryCount++;
    }
}
