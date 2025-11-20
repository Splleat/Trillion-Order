package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.PaymentStatus;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {
    Page<OrderResponse> findAllOrders(Pageable pageable);
    Long createOrder(Long memberId, OrderCreateRequest request);
    OrderResponse findOrderByCustomer(Long memberId, Long orderId);
    OrderResponse findOrderByOrderId(Long orderId);
    Page<OrderResponse> findAllOrderByMemberId(Pageable pageable, Long memberId);
    void patchOrderItemStatus(Long memberId, Long orderId, Long orderItemId, OrderItemStatusPatchRequest request);
    void patchOrderItemStatusForNonMember(Long orderId, Long orderItemId, NonMemberOrderItemStatusPatchRequest request);
    OrderResponse findOrderByOrderNumber(String orderNumber, String nonMemberPassword);

    // PaymentService에서 호출 -> 회원/비회원 인증 과정 필요 없을듯?
    void patchPaymentStatus(Long orderId, String paymentStatus);
}
