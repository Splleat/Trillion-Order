package com.nhnacademy.order.client.member;

import com.nhnacademy.order.client.member.dto.PointAccumulationRequest;
import com.nhnacademy.order.client.member.dto.PointUsageRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "member-service")
public interface MemberClient {
    @PatchMapping("/members/points/use")
    void decreasePoint(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody PointUsageRequest request);

    @PatchMapping("/members/points/increase")
    void increasePoint(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody PointUsageRequest request);

    @PatchMapping("/members/points/rollback")
    void rollbackPoint(@RequestHeader("X-Saga-Id") UUID sagaId, @RequestBody PointUsageRequest request);

    @PatchMapping("/members/points/accumulate")
    void accumulatePoint(@RequestHeader("X-Saga-Id") UUID idempotencyKey, @RequestBody PointAccumulationRequest request);
}