package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderItemRefundService {
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public void completeOrderItem(OrderItem orderItem) {
        orderItem.setOrderItemStatus(OrderItemStatus.RETURNED);

        orderItemRepository.save(orderItem);
    }
}
