package com.nhnacademy.order.point.service;

import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.repository.PointAccumulationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointAccumulationService {
    private final MemberService memberService;
    private final PointStatusUpdateService pointStatusUpdateService;

    // 포인트 적립 메서드
    public void processEvent(PointAccumulationEvent event) {
        try {
            String idempotencySource = event.getOrderId() + ":" + event.getOrderItemId();
            UUID idempotencyKey = UUID.nameUUIDFromBytes(idempotencySource.getBytes());

            memberService.accumulatePoint(
                idempotencyKey,
                event.getMemberId(),
                event.getOrderId(),
                event.getPurchaseAmount()
            );

            pointStatusUpdateService.markAsCompleted(event.getId());

        } catch (Exception e) {
            log.warn("[PointAccumulation] 포인트 적립 API 호출 실패. Event ID: {}", event.getId(), e);
            // 상태를 FAILED로 바꾸지 않고 예외를 던져서,
            // 1. ServiceImpl에서는 로그를 남기고 넘어가게 하고 (스케줄러 처리 대상 유지)
            // 2. Scheduler에서는 retryCount를 증가시키게 함
            throw e;
        }
    }
}
