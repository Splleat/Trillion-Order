package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderItemService {
    List<Long> getTopNSellingBookIds(int limit);
    Page<OrderItemResponse> findRefundedOrderItemsByMemberId(UserInfo userInfo, Pageable pageable);
}
