package com.nhnacademy.order.order.service;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderItemUpdateService {
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public void updateStatus(OrderItem orderItem, OrderItemStatus status) {
        orderItem.setOrderItemStatus(status);
        orderItemRepository.save(orderItem);
    }
}
