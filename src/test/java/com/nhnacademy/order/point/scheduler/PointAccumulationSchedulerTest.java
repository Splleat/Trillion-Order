package com.nhnacademy.order.point.scheduler;

import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.domain.PointAccumulationStatus;
import com.nhnacademy.order.point.repository.PointAccumulationEventRepository;
import com.nhnacademy.order.point.service.PointAccumulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointAccumulationSchedulerTest {

    @InjectMocks
    private PointAccumulationScheduler scheduler;

    @Mock
    private PointAccumulationEventRepository eventRepository;

    @Mock
    private PointAccumulationService pointAccumulationService;

    @Test
    @DisplayName("복구 스케줄러: 재시도 횟수가 남은 이벤트는 재시도를 수행해야 한다")
    void recoverStuckEvents_Retry() {
        // given
        PointAccumulationEvent event = mock(PointAccumulationEvent.class);
        when(event.getRetryCount()).thenReturn(0); // 재시도 횟수 0 (남음)

        when(eventRepository.findAllByStatusAndUpdatedAtBefore(eq(PointAccumulationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(event));

        // when
        scheduler.recoverStuckEvents();

        // then
        // 1. 서비스의 processEvent가 호출되어야 함 (재시도 수행)
        verify(pointAccumulationService, times(1)).processEvent(event);
        
        // 2. 실패 처리는 호출되지 않아야 함
        verify(event, never()).recordFailure(anyString());
    }

    @Test
    @DisplayName("복구 스케줄러: 재시도 중 예외 발생 시 재시도 횟수를 증가시켜야 한다")
    void recoverStuckEvents_RetryFailure() {
        // given
        PointAccumulationEvent event = mock(PointAccumulationEvent.class);
        when(event.getRetryCount()).thenReturn(0);

        when(eventRepository.findAllByStatusAndUpdatedAtBefore(eq(PointAccumulationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(event));

        // processEvent 호출 시 예외 발생
        doThrow(new RuntimeException("Retry Failed")).when(pointAccumulationService).processEvent(event);

        // when
        scheduler.recoverStuckEvents();

        // then
        // 1. processEvent 호출됨
        verify(pointAccumulationService, times(1)).processEvent(event);
        
        // 2. requeueForRetry가 호출되어야 함 (재시도 횟수 증가)
        verify(event, times(1)).requeueForRetry();
    }

    @Test
    @DisplayName("복구 스케줄러: 최대 재시도 횟수를 초과한 이벤트는 실패 처리해야 한다")
    void recoverStuckEvents_MaxRetryExceeded() {
        // given
        PointAccumulationEvent event = mock(PointAccumulationEvent.class);
        when(event.getRetryCount()).thenReturn(3); // 최대 횟수 도달 (MAX_RETRY_COUNT = 3 가정)

        when(eventRepository.findAllByStatusAndUpdatedAtBefore(eq(PointAccumulationStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(event));

        // when
        scheduler.recoverStuckEvents();

        // then
        // 1. processEvent는 호출되지 않아야 함
        verify(pointAccumulationService, never()).processEvent(event);
        
        // 2. recordFailure가 호출되어야 함
        verify(event, times(1)).recordFailure(contains("최대 재시도 횟수"));
    }
    
    @Test
    @DisplayName("복구 스케줄러: 처리할 이벤트가 없으면 아무 동작도 하지 않아야 한다")
    void recoverStuckEvents_NoEvents() {
        // given
        when(eventRepository.findAllByStatusAndUpdatedAtBefore(any(), any()))
                .thenReturn(Collections.emptyList());

        // when
        scheduler.recoverStuckEvents();

        // then
        verify(pointAccumulationService, never()).processEvent(any());
    }
}
