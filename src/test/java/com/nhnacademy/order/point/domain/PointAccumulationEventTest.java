package com.nhnacademy.order.point.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointAccumulationEventTest {

    @Test
    @DisplayName("생성: 초기 상태는 PENDING이고 재시도 횟수는 0이어야 한다")
    void create() {
        // given
        Long memberId = 1L;
        Long orderId = 100L;
        Long orderItemId = 200L;
        int purchaseAmount = 5000;

        // when
        PointAccumulationEvent event = PointAccumulationEvent.create(memberId, orderId, orderItemId, purchaseAmount);

        // then
        assertThat(event.getMemberId()).isEqualTo(memberId);
        assertThat(event.getOrderId()).isEqualTo(orderId);
        assertThat(event.getOrderItemId()).isEqualTo(orderItemId);
        assertThat(event.getPurchaseAmount()).isEqualTo(purchaseAmount);
        assertThat(event.getStatus()).isEqualTo(PointAccumulationStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("완료 처리: 상태가 COMPLETED로 변경되어야 한다")
    void markAsCompleted() {
        // given
        PointAccumulationEvent event = PointAccumulationEvent.create(1L, 100L, 200L, 5000);

        // when
        event.markAsCompleted();

        // then
        assertThat(event.getStatus()).isEqualTo(PointAccumulationStatus.COMPLETED);
    }

    @Test
    @DisplayName("실패 기록: 상태가 FAILED로 변경되고 에러 메시지가 저장되어야 한다")
    void recordFailure() {
        // given
        PointAccumulationEvent event = PointAccumulationEvent.create(1L, 100L, 200L, 5000);
        String errorMessage = "API Connection Error";

        // when
        event.recordFailure(errorMessage);

        // then
        assertThat(event.getStatus()).isEqualTo(PointAccumulationStatus.FAILED);
        assertThat(event.getLastErrorMessage()).isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("재시도 대기: 상태가 PENDING으로 유지되고 재시도 횟수가 증가해야 한다")
    void requeueForRetry() {
        // given
        PointAccumulationEvent event = PointAccumulationEvent.create(1L, 100L, 200L, 5000);
        
        // when
        event.requeueForRetry();

        // then
        assertThat(event.getStatus()).isEqualTo(PointAccumulationStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
    }
}