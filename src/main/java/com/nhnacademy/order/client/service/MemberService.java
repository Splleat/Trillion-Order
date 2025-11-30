package com.nhnacademy.order.client.service;

import com.nhnacademy.order.client.MemberClient;
import com.nhnacademy.order.client.dto.PointUsageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberClient memberClient;

    public void decreasePoint(UUID sagaId, Long memberId, int point) {
        memberClient.decreasePoint(new PointUsageRequest(sagaId, memberId, point));
    }

    public void increasePoint(UUID sagaId, Long memberId, int point) {
        memberClient.increasePoint(new PointUsageRequest(sagaId, memberId, point));
    }
}
