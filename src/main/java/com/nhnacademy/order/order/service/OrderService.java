package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;

import java.util.List;

public interface OrderService {
    Long createOrder(Long memberId, OrderCreateRequest request);
    OrderResponse findOrderByOrderId(Long orderId);
    List<OrderResponse> findAllOrderByMemberId(Long memberId);
    void patchOrderItemStatus(Long memberId, Long orderId, Long orderItemId, OrderItemStatusPatchRequest request);
}
