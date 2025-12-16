package com.nhnacademy.order.client.member.service;

import com.nhnacademy.order.client.member.MemberClient;
import com.nhnacademy.order.client.member.dto.PointUsageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("local")
@Component
public class MockMemberClient implements MemberClient {

    @Override
    public void increasePoint(PointUsageRequest request) {
        log.info("MockMemberClient 포인트 증가 요청: {}", request);
    }

    @Override
    public void decreasePoint(PointUsageRequest request) {
        log.info("MockMemberClient 포인트 감소 요청: {}", request);
    }

    @Override
    public void rollbackPoint(PointUsageRequest request) {
        log.info("MockMemberClient 포인트 복구 요청: {}", request);
    }
}