package com.nhnacademy.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;

@FeignClient(name = "member-service")
public interface MemberClient {
    @PatchMapping("/api/point/1")
    void usePoint(int point);
}
