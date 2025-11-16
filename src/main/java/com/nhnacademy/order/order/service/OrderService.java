package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {
    Page<OrderResponse> findAllOrders(Pageable pageable);
    Long createOrder(Long memberId, OrderCreateRequest request);
    OrderResponse findOrderByCustomer(Long memberId, Long orderId);
    OrderResponse findOrderByOrderId(Long orderId);
    List<OrderResponse> findAllOrderByMemberId(Long memberId);
    void patchOrderItemStatus(Long memberId, Long orderId, Long orderItemId, OrderItemStatusPatchRequest request);
    OrderResponse findOrderByOrderNumber(String orderNumber, String nonMemberPassword);
}
