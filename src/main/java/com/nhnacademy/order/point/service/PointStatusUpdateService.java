package com.nhnacademy.order.point.service;

import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.repository.PointAccumulationEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointStatusUpdateService {
    private final PointAccumulationEventRepository eventRepository;

    // 이벤트 처리 성공 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsCompleted(Long eventId) {
        eventRepository.findById(eventId).ifPresent(PointAccumulationEvent::markAsCompleted);
    }

    // 이벤트 처리 실패 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long eventId, String errorMessage) {
        eventRepository.findById(eventId).ifPresent(event -> {
            event.recordFailure(errorMessage);
        });
    }
}
