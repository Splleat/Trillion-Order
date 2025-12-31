package com.nhnacademy.order.point.service;

import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.domain.PointAccumulationStatus;
import com.nhnacademy.order.point.repository.PointAccumulationEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointStatusUpdateServiceTest {

    @Mock
    private PointAccumulationEventRepository eventRepository;

    @InjectMocks
    private PointStatusUpdateService service;

    @DisplayName("이벤트 처리 성공 기록")
    @Test
    void markAsCompleted_Success() {
        // given
        Long eventId = 1L;
        PointAccumulationEvent event = PointAccumulationEvent.create(1L, 1L, 1L, 1000);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        // when
        service.markAsCompleted(eventId);

        // then
        assertThat(event.getStatus()).isEqualTo(PointAccumulationStatus.COMPLETED);
    }

    @DisplayName("이벤트 처리 실패 기록")
    @Test
    void recordFailure_Success() {
        // given
        Long eventId = 1L;
        String errorMessage = "Error occurred";
        PointAccumulationEvent event = PointAccumulationEvent.create(1L, 1L, 1L, 1000);
        given(eventRepository.findById(eventId)).willReturn(Optional.of(event));

        // when
        service.recordFailure(eventId, errorMessage);

        // then
        assertThat(event.getStatus()).isEqualTo(PointAccumulationStatus.FAILED);
        assertThat(event.getLastErrorMessage()).isEqualTo(errorMessage);
    }

    @DisplayName("이벤트가 없으면 아무 일도 일어나지 않음")
    @Test
    void markAsCompleted_NotFound() {
        // given
        Long eventId = 999L;
        given(eventRepository.findById(eventId)).willReturn(Optional.empty());

        // when
        service.markAsCompleted(eventId);

        // then
        // No exception, nothing happens
        verify(eventRepository).findById(eventId);
    }
}
