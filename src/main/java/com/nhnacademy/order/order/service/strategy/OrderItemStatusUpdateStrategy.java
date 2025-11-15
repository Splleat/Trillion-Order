package com.nhnacademy.order.order.service.strategy;

import com.nhnacademy.order.order.domain.Orders;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.exception.OrderItemNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public enum OrderItemStatusUpdateStrategy {
    REQUEST_RETURN(OrderItemStatus.RETURN_REQUESTED) {
        @Override
        public void updateStatus(Orders order, Long orderItemId) {
            OrderItem orderItem = findOrderItem(order, orderItemId);
            orderItem.requestReturn();
        }
    },
    RETURNED(OrderItemStatus.RETURNED) {
        @Override
        public void updateStatus(Orders order, Long orderItemId) {
            OrderItem orderItem = findOrderItem(order, orderItemId);

            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.RETURN_REQUESTED)) {
                throw new IllegalStateException("반품 요청 상태가 아닌 상품: " + orderItemId);
            }

            // TODO: 포인트 환불 로직
            // 멤버 API를 호출해 결제 금액 만큼의 포인트 추가

            orderItem.completeReturn();
        }
    },
    CANCELED(OrderItemStatus.CANCELED) {
        @Override
        public void updateStatus(Orders order, Long orderItemId) {
            OrderItem orderItem = findOrderItem(order, orderItemId);

            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.PREPARING)) {
                throw new IllegalStateException("결제 취소가 불가능한 상품: " + orderItemId);
            }

            // TODO: 결제 취소 로직
            // 결제 API를 호출해 환불

            orderItem.cancel();
        }
    };

    private final OrderItemStatus targetStatus;

    public abstract void updateStatus(Orders order, Long orderItemId);

    protected OrderItem findOrderItem(Orders order, Long orderItemId) {
        return order.getOrderItems().stream()
                .filter(orderItem -> orderItem.getOrderItemId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new OrderItemNotFoundException("해당 주문에 존재하지 않는 상품: " + orderItemId));
    }

    public static OrderItemStatusUpdateStrategy from(OrderItemStatus status) {
        return Arrays.stream(values())
                .filter(strategy -> strategy.targetStatus == status)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 상태 변경: " + status));
    }
}
