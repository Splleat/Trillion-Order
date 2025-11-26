package com.nhnacademy.order.order.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service("securityService")
public class SecurityService {
    private final OrderRepository orderRepository;

    public boolean isAdmin(UserInfo userInfo) {
        return (userInfo != null && userInfo.role().equals("ADMIN"));
    }

    public boolean isOrderOwner(Long orderId, UserInfo userInfo) {
        Long ownerMemberId = orderRepository.findMemberIdByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문 ID: " + orderId));

        return userInfo.userId().equals(ownerMemberId);
    }

    public boolean isAuthenticated(UserInfo userInfo) {
        return (userInfo != null && userInfo.userId() != null);
    }
}
