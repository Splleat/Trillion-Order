package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Arrays;

enum Role {
    ADMIN,  // 관리자만 가능
    USER,   // 회원, 비회원 가능
    DISABLE // 비활성 (현재 불가능)
}

@Slf4j
@RequiredArgsConstructor
public enum OrderItemStatusUpdateStrategy {
    SHIPPED(OrderItemStatus.SHIPPED, Role.ADMIN) {
        @Override
        public void updateStatus(Order order, OrderItem orderItem) {

            // 상품 준비 상태가 아니면 배송 불가
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.PREPARING)) {
                throw new OrderStatusTransitionException("준비 상태가 아닌 상품: " + orderItem.getOrderItemId());
            }

            orderItem.ship();
        }
    },
    DELIVERED(OrderItemStatus.DELIVERED, Role.ADMIN) {
        @Override
        public void updateStatus(Order order, OrderItem orderItem) {
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.SHIPPED)) {
                throw new OrderStatusTransitionException("배송 중이 아닌 상품: " + orderItem.getOrderItemId());
            }

            orderItem.delivered();
        }
    },
    REQUEST_RETURN_CHANGE_OF_MIND(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND, Role.USER) {
        @Override
        public void updateStatus(Order order, OrderItem orderItem) {
            // 배송 완료 상태가 아니면 반품 요청 불가
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.DELIVERED)) {
                throw new OrderStatusTransitionException("배송 완료 상태가 아닌 상품: " + orderItem.getOrderItemId());
            }

            // 출고일 기준 10일 경과 시 반품 요청 불가
            if (LocalDateTime.now().isAfter(orderItem.getShippingDate().plusDays(10))) {
                throw new OrderStatusTransitionException("단순 변심 반품 기간이 지난 상품: " + orderItem.getOrderItemId());
            }

            orderItem.setOrderItemStatus(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND);
        }
    },
    REQUEST_RETURN_DAMAGED(OrderItemStatus.RETURN_REQUESTED_DAMAGED, Role.USER) {
        @Override
        public void updateStatus(Order order, OrderItem orderItem) {
            // 배송 완료 상태가 아니면 반품 요청 불가
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.DELIVERED)) {
                throw new OrderStatusTransitionException("배송 완료 상태가 아닌 상품: " + orderItem.getOrderItemId());
            }

            // 출고일 기준 30일 경과 시 반품 요청 불가
            if (LocalDateTime.now().isAfter(orderItem.getShippingDate().plusDays(30))) {
                throw new OrderStatusTransitionException("파손 반품 기간이 지난 상품: " + orderItem.getOrderItemId());
            }

            orderItem.setOrderItemStatus(OrderItemStatus.RETURN_REQUESTED_DAMAGED);
        }
    },
    RETURNED(OrderItemStatus.RETURNED, Role.ADMIN) {
        @Override
        public void updateStatus(Order order, OrderItem orderItem) {
            // 이제 반품은 사가에 의해서 이루어짐
//            OrderItem orderItem = findOrderItem(order, orderItemId);
//
//            // 반품 요청 상태가 아니면 반품 완료 불가
//            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND) &&
//            !orderItem.getOrderItemStatus().equals(OrderItemStatus.RETURN_REQUESTED_DAMAGED)) {
//                throw new OrderStatusTransitionException("반품 요청 상태가 아닌 상품: " + orderItemId);
//            }
//
//            // 상태 변경
//            orderItem.setOrderItemStatus(OrderItemStatus.RETURNED);
//
//            // 주문 전체 상태 업데이트
//            order.reflectItemStatusChange();
        }
    },
    CANCELED(OrderItemStatus.CANCELED, Role.DISABLE) {
        @Override
        public void updateStatus(Order order, OrderItem orderItem) {
            // 상품 준비 중 상태가 아니면 취소 불가
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.PREPARING)) {
                throw new OrderStatusTransitionException("주문 취소가 불가능한 상태의 상품: " + orderItem.getOrderItemId());
            }

            // 상태 변경
            orderItem.setOrderItemStatus(OrderItemStatus.CANCELED);
        }
    },
    CONFIRMED(OrderItemStatus.CONFIRMED, Role.USER) {
        @Override
        public void updateStatus(Order order, OrderItem orderItem) {
            // 배송 완료 상태가 아니면 구매 확정 불가
            if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.DELIVERED)) {
                throw new OrderStatusTransitionException("구매 확정이 불가능한 상태의 상품: " + orderItem.getOrderItemId());
            }

            orderItem.setOrderItemStatus(OrderItemStatus.CONFIRMED);
        }
    };

    private final OrderItemStatus targetStatus;
    private final Role requiredRole;

    public abstract void updateStatus(Order order, OrderItem orderItem);

    public boolean hasPermission(String userRole) {
        // 비활성화된 기능
        if (this.requiredRole == Role.DISABLE) {
            log.info("비활성화된 기능 호출");
            return false;
        }

        // 관리자만 가능
        if (this.requiredRole == Role.ADMIN) {
            return "ADMIN".equals(userRole);
        }

        // 그 외에는 모두 가능
        return true;
    }

    public static OrderItemStatusUpdateStrategy from(OrderItemStatus status) {
        return Arrays.stream(values())
                .filter(strategy -> strategy.targetStatus == status)
                .findFirst()
                .orElseThrow(() -> new OrderStatusTransitionException("지원하지 않는 상태 변경: " + status));
    }
}
