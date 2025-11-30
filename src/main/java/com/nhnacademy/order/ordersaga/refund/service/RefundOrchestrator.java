package com.nhnacademy.order.ordersaga.refund.service;

import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RefundOrchestrator {
    public void processRefund(Order order, OrderItem refundItem, int refundPoint) {
        int totalPrice = order.getOrderDetails().totalPrice(); // originPrice - (쿠폰 할인액 + 사용 포인트) + 배송비
        int deliveryFee = order.getOrderDetails().deliveryFee();

    }
}
