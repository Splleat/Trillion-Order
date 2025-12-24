package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderItemUpdateService {
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public void updateOrderItemStatus(OrderItem orderItem, OrderItemStatusUpdateStrategy strategy) {
        strategy.updateStatus(orderItem);
        orderItemRepository.save(orderItem);
    }
}
