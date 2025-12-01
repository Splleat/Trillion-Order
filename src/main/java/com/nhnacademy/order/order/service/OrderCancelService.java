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

        order.setOrderStatus(OrderStatus.CANCELED);

        orderRepository.save(order);
    }
}
