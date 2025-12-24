package com.nhnacademy.order.point.scheduler;

import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.domain.PointAccumulationStatus;
import com.nhnacademy.order.point.repository.PointAccumulationEventRepository;
import com.nhnacademy.order.point.service.PointAccumulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PointAccumulationScheduler {

    private final PointAccumulationEventRepository eventRepository;
    private final PointAccumulationService pointAccumulationService;
    private static final int MAX_RETRY_COUNT = 3;

    // 포인트 적립 대기(PENDING) 이벤트 처리
    @Scheduled(fixedDelay = 300000) // 5분 마다 실행
    @SchedulerLock(name = "PointAccumulationScheduler.recoverStuckEvents")
    @Transactional
    public void recoverStuckEvents() {
        log.info("[PointAccumulation] 포인트 적립 재시도 스케줄러 시작");
        
        // updatedAt이 5분 지났는데 여전히 PENDING인 이벤트 조회 (즉, 동기 호출이 실패하여 방치된 이벤트들)
        LocalDateTime stuckThreshold = LocalDateTime.now().minusMinutes(5);
        List<PointAccumulationEvent> pendingEvents = eventRepository.findAllByStatusAndUpdatedAtBefore(
                PointAccumulationStatus.PENDING, stuckThreshold);

        for (PointAccumulationEvent event : pendingEvents) {
            if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                event.recordFailure("최대 재시도 횟수(" + MAX_RETRY_COUNT + ") 초과로 인한 실패 처리");
                log.error("[PointAccumulation] 이벤트(ID: {}) 최대 재시도 횟수 초과. 실패 처리됨.", event.getId());
                continue;
            }

            try {
                log.info("[PointAccumulation] 이벤트(ID: {}) 재시도 수행 (시도 횟수: {})", event.getId(), event.getRetryCount() + 1);
                // 실제 재시도 수행
                pointAccumulationService.processEvent(event);
                
            } catch (Exception e) {
                // 재시도 실패 시, retryCount 증가시키고 PENDING 상태 유지
                event.requeueForRetry(); 
                log.warn("[PointAccumulation] 이벤트(ID: {}) 재시도 실패. 다음 주기에 다시 시도합니다. 에러: {}", event.getId(), e.getMessage());
            }
        }
    }
}
