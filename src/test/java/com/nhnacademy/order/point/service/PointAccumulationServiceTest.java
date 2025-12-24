package com.nhnacademy.order.point.service;

import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PointAccumulationServiceTest {

    @InjectMocks
    private PointAccumulationService pointAccumulationService;

    @Mock
    private MemberService memberService;

    @Mock
    private PointStatusUpdateService pointStatusUpdateService;

    @Test
    @DisplayName("이벤트 처리 성공: 멤버 서비스 호출 후 완료 상태 업데이트")
    void processEvent_Success() {
        // given
        PointAccumulationEvent event = mock(PointAccumulationEvent.class);
        when(event.getId()).thenReturn(1L);
        when(event.getMemberId()).thenReturn(100L);
        when(event.getOrderId()).thenReturn(200L);
        when(event.getOrderItemId()).thenReturn(300L);
        when(event.getPurchaseAmount()).thenReturn(5000);

        // when
        pointAccumulationService.processEvent(event);

        // then
        // 1. 멤버 서비스 호출 확인 (멱등성 키 생성 로직 검증은 어려우므로 호출 여부만 확인)
        verify(memberService, times(1)).accumulatePoint(
                any(UUID.class),
                eq(100L),
                eq(200L),
                eq(5000)
        );

        // 2. 성공 시 완료 상태 업데이트 호출 확인
        verify(pointStatusUpdateService, times(1)).markAsCompleted(1L);
    }

    @Test
    @DisplayName("이벤트 처리 실패: 예외를 다시 던져야 함")
    void processEvent_Failure() {
        // given
        PointAccumulationEvent event = mock(PointAccumulationEvent.class);
        when(event.getMemberId()).thenReturn(100L);
        when(event.getOrderId()).thenReturn(200L);
        when(event.getOrderItemId()).thenReturn(300L);
        when(event.getPurchaseAmount()).thenReturn(5000);

        doThrow(new RuntimeException("API Error"))
                .when(memberService).accumulatePoint(any(), anyLong(), anyLong(), anyInt());

        // when & then
        assertThatThrownBy(() -> pointAccumulationService.processEvent(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("API Error");

        // 1. 멤버 서비스는 호출되었어야 함
        verify(memberService, times(1)).accumulatePoint(any(), anyLong(), anyLong(), anyInt());

        // 2. 예외가 던져졌으므로 markAsCompleted는 호출되지 않아야 함
        verify(pointStatusUpdateService, never()).markAsCompleted(anyLong());

        // 3. recordFailure도 호출되지 않아야 함 (예외를 던져서 상위에서 처리하도록 변경했으므로)
        verify(pointStatusUpdateService, never()).recordFailure(anyLong(), anyString());
    }
}
