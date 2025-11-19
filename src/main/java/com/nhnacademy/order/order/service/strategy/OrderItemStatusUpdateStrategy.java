package com.nhnacademy.order.order.service.strategy;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.exception.OrderItemNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public enum OrderItemStatusUpdateStrategy {
    SHIPPED(OrderItemStatus.SHIPPED) {
        @Override
        public void updateStatus(Order order, Long orderItemId) {
            OrderItem orderItem = findOrderItem(order, orderItemId);
            orderItem.ship();
        }
    },
    REQUEST_RETURN(OrderItemStatus.RETURN_REQUESTED) {

        @Override
        public void updateStatus(Order order, Long orderItemId) {
            OrderItem orderItem = findOrderItem(order, orderItemId);
            orderItem.requestReturn();
        }
    },
    RETURNED(OrderItemStatus.RETURNED) {
        @Override
        public void updateStatus(Order order, Long orderItemId) {
            OrderItem orderItem = findOrderItem(order, orderItemId);

            // 반품 요청 상태가 아니면 반품 완료 불가
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.RETURN_REQUESTED)) {
                throw new OrderStatusTransitionException("반품 요청 상태가 아닌 상품: " + orderItemId);
            }

            // TODO: 포인트 환불 로직
            // 멤버 API를 호출해 결제 금액 만큼의 포인트 추가 (적립 포인트만큼 제외? 어떻게?)
            // 쿠폰 API를 호출해 쿠폰 복구?
            // 도서 API를 호출해 재고 복구

            orderItem.completeReturn();

            // 주문 전체 상태 업데이트
            order.reflectItemStatusChange();
        }
    },
    CANCELED(OrderItemStatus.CANCELED) {
        @Override
        public void updateStatus(Order order, Long orderItemId) {
            OrderItem orderItem = findOrderItem(order, orderItemId);

            // 상품 준비 중 상태가 아니면 취소 불가
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.PREPARING)) {
                throw new OrderStatusTransitionException("주문 취소가 불가능한 상태의 상품: " + orderItemId);
            }

            // TODO: 주문 취소 로직
            // 결제 API를 호출해 환불
                // 적립된 포인트만큼 제외하고 환불?
            // 쿠폰 API를 호출해 쿠폰 복구?
            // 도서 API를 호출해 재고 복구

            orderItem.cancel();

            // 주문 전체 상태 업데이트
            order.reflectItemStatusChange();
        }
    };

    private final OrderItemStatus targetStatus;

    public abstract void updateStatus(Order order, Long orderItemId);

    protected OrderItem findOrderItem(Order order, Long orderItemId) {
        return order.getOrderItems().stream()
                .filter(orderItem -> orderItem.getOrderItemId().equals(orderItemId))
                .findFirst()
                .orElseThrow(() -> new OrderItemNotFoundException("해당 주문에 존재하지 않는 상품: " + orderItemId));
    }

    public static OrderItemStatusUpdateStrategy from(OrderItemStatus status) {
        return Arrays.stream(values())
                .filter(strategy -> strategy.targetStatus == status)
                .findFirst()
                .orElseThrow(() -> new OrderStatusTransitionException("지원하지 않는 상태 변경: " + status));
    }
}
