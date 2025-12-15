package com.nhnacademy.order.client.member;

import com.nhnacademy.order.client.member.dto.PointUsageRequest;
import com.nhnacademy.order.common.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Profile("!local")
@FeignClient(name = "MEMBER-SERVICE", configuration = FeignClientConfig.class)
public interface MemberClient {
    @PatchMapping("/api/point/1")
    void increasePoint(@RequestBody PointUsageRequest request);

    @PatchMapping("/api/point/2")
    void decreasePoint(@RequestBody PointUsageRequest request);

    @PatchMapping("/api/point/3")
    void rollbackPoint(@RequestBody PointUsageRequest request);
}
