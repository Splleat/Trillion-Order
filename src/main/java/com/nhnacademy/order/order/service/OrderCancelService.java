package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.cancellation.repository.OrderCancelSagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OrderCancelService {
    private final OrderRepository orderRepository;
    private final OrderCancelSagaRepository orderCancelSagaRepository;

    @Transactional
    public void cancelOrder(Order order, OrderCancelSaga saga) {

        // 이미 처리된 주문은 다시 처리하지 않음
        if (order.getOrderStatus() == OrderStatus.CANCELED) {
            // 하지만 도메인과 연결되지 않았을 수 있으므로 처리
            if (!saga.isBridged()) {
                saga.setBridged(true);
                orderCancelSagaRepository.save(saga);
            }
            return;
        }

        order.setOrderStatus(OrderStatus.CANCELED);

        // 주문 상품도 모두 취소 처리
        order.getOrderItems().forEach(orderItem ->
                orderItem.setOrderItemStatus(OrderItemStatus.CANCELED)
        );

        orderRepository.save(order);

        saga.setBridged(true);

        orderCancelSagaRepository.save(saga);
    }
}
