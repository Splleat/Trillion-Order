package com.nhnacademy.order.ordersaga.itemrefund.service;

import com.nhnacademy.order.client.service.BookService;
import com.nhnacademy.order.client.service.CouponService;
import com.nhnacademy.order.client.service.MemberService;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.ItemRefundSagaStep;
import com.nhnacademy.order.orderitem.exception.OrderItemRefundFailureException;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderItemRefundOrchestrator {
    private final SagaUpdateService sagaUpdateService;
    private final MemberService memberService;
    private final CouponService couponService;
    private final BookService bookService;

    public void processItemRefund(Long memberId, Order order, OrderItem orderItem, int deliveryFee) {
        OrderItemRefundSaga orderItemRefundSaga = OrderItemRefundSaga.create(order.getOrderId(), orderItem.getOrderItemId());

        // 1. 사가 시작
        sagaUpdateService.updateItemRefundSagaStep(orderItemRefundSaga, ItemRefundSagaStep.STARTED);

        UUID sagaId = orderItemRefundSaga.getSagaId();

        int quantity = orderItem.getQuantity();

        // 상품 가격 * 개수
        int refundPoint = orderItem.getPrice() * quantity;

        // 환불 이유가 단순 변심인 경우
        if (orderItem.getOrderItemStatus().equals(OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND)) {
            // 반품 배송비를 포인트에서 차감한 후 지급
            refundPoint = Math.max(0, refundPoint - deliveryFee);
        }

        try {
            // TODO: 쿠폰 조건(ex) 30,000원 이상 구매 시 10,000원 할인)이 깨진 경우 환불 포인트에서 차감 (단, 판매자 귀책인 경우 전액 환불)

            memberService.increasePoint(sagaId, memberId, refundPoint);
            sagaUpdateService.updateItemRefundSagaStep(orderItemRefundSaga, ItemRefundSagaStep.POINT_REFUNDED);

            bookService.increaseStock(sagaId, orderItem.getBookId(), quantity);
            sagaUpdateService.updateItemRefundSagaStep(orderItemRefundSaga, ItemRefundSagaStep.STOCK_INCREASED);

            sagaUpdateService.updateItemRefundSagaStatus(orderItemRefundSaga, SagaStatus.COMPLETED);
        } catch (Exception e) {
            sagaUpdateService.updateItemRefundSagaStatus(orderItemRefundSaga, SagaStatus.FAILED);
            throw new OrderItemRefundFailureException("주문 상품 환불 실패: " + orderItem.getOrderItemId());
        }
    }
}
