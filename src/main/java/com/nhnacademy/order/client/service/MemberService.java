package com.nhnacademy.order.client.service;

import com.nhnacademy.order.client.MemberClient;
import com.nhnacademy.order.client.dto.PointUsageRequest;
import com.nhnacademy.order.client.handler.ResilienceFallbackHandler;
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

    @CircuitBreaker(name = "member-service", fallbackMethod = "fallbackDecreasePoint")
    @Retry(name = "member-service")
    public void decreasePoint(UUID sagaId, Long memberId, int point) {
        memberClient.decreasePoint(new PointUsageRequest(sagaId, memberId, point));
    }

    @CircuitBreaker(name = "member-service", fallbackMethod = "fallbackIncreasePoint")
    @Retry(name = "member-service")
    public void increasePoint(UUID sagaId, Long memberId, int point) {
        memberClient.increasePoint(new PointUsageRequest(sagaId, memberId, point));
    }

    public void fallbackDecreasePoint(UUID sagaId, Long memberId, int point, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "포인트 감소", throwable);
    }

    public void fallbackIncreasePoint(UUID sagaId, Long memberId, int point, Throwable throwable) {
        fallbackHandler.handle(SERVICE_NAME, "포인트 증가", throwable);
    }
}
