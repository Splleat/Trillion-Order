package com.nhnacademy.order.client.member.service;

import com.nhnacademy.order.client.member.MemberClient;
import com.nhnacademy.order.client.member.dto.PointUsageRequest;
import com.nhnacademy.order.client.common.handler.ResilienceFallbackHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberClient memberClient;
    private final ResilienceFallbackHandler fallbackHandler;
    private static final String SERVICE_NAME = "회원 API";

    // 포인트 차감 (주문 생성)
    @CircuitBreaker(name = "member-service", fallbackMethod = "fallbackDecreasePoint")
    @Retry(name = "member-service")
    public void decreasePoint(UUID sagaId, Long memberId, int point) {
        memberClient.decreasePoint(sagaId, new PointUsageRequest(memberId, point));
    }

    // 포인트 적립 (주문 취소)
    @CircuitBreaker(name = "member-service", fallbackMethod = "fallbackIncreasePoint")
    @Retry(name = "member-service")
    public void increasePoint(UUID sagaId, Long memberId, int point) {
        memberClient.increasePoint(sagaId, new PointUsageRequest(memberId, point));
    }

    // 포인트 복구 (주문 생성 실패 시)
    @CircuitBreaker(name = "member-service", fallbackMethod = "fallbackRollbackPoint")
    @Retry(name = "member-service")
    public void rollbackPoint(UUID sagaId, Long memberId, int point) {
        memberClient.rollbackPoint(sagaId, new PointUsageRequest(memberId, point));
    }

    public void fallbackDecreasePoint(UUID sagaId, Long memberId, int point, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "포인트 감소", throwable);
    }

    public void fallbackIncreasePoint(UUID sagaId, Long memberId, int point, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "포인트 증가", throwable);
    }

    public void fallbackRollbackPoint(UUID sagaId, Long memberId, int point, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "포인트 복구", throwable);
    }
}
