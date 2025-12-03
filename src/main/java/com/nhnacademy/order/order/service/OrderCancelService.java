package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderCancelService {
    private final OrderRepository orderRepository;

    @Transactional
    public void completeOrder(Order order) {

        // 이미 처리된 주문은 다시 처리하지 않음
        if (order.getOrderStatus() == OrderStatus.CANCELED) {
            return;
        }

        order.setOrderStatus(OrderStatus.CANCELED);

        orderRepository.save(order);
    }
}
