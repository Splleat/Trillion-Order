package com.nhnacademy.order.ordersaga.cancelation.service;

import com.nhnacademy.order.client.service.BookService;
import com.nhnacademy.order.client.service.CouponService;
import com.nhnacademy.order.client.service.MemberService;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import com.nhnacademy.order.ordersaga.cancelation.domain.CancelSagaStep;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.ordersaga.cancelation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.order.exception.OrderCancelFailureException;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderCancelOrchestrator {
    private final SagaUpdateService sagaUpdateService;

    private final MemberService memberService;
    private final CouponService couponService;
    private final BookService bookService;

    public void processCancelOrder(Long memberId, Order order) {
        OrderCancelSaga orderCancelSaga = OrderCancelSaga.create(order.getOrderId());

        // 1. 사가 시작
        sagaUpdateService.updateCancelSagaStep(orderCancelSaga, CancelSagaStep.STARTED);

        UUID sagaId = orderCancelSaga.getSagaId();

        int pointUsage = order.getOrderDetails().pointUsage();

        Long couponId = order.getOrderDetails().couponId();

        Map<Long, Integer> quantityMap = order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getBookId, OrderItem::getQuantity));

        try {
            if (pointUsage > 0) {
                memberService.increasePoint(sagaId, memberId, pointUsage);
                sagaUpdateService.updateCancelSagaStep(orderCancelSaga, CancelSagaStep.POINT_REFUNDED);
            }

            if (Objects.nonNull(couponId)) {
                couponService.withdrawCoupon(sagaId, memberId, couponId);
                sagaUpdateService.updateCancelSagaStep(orderCancelSaga, CancelSagaStep.COUPON_RESTORED);
            }

            bookService.increaseStocks(sagaId, quantityMap);

            sagaUpdateService.updateCancelSagaStatus(orderCancelSaga, SagaStatus.COMPLETED);

        } catch (Exception e) {
            sagaUpdateService.updateCancelSagaStatus(orderCancelSaga, SagaStatus.FAILED);
            // 스케줄러를 사용해 재시도하거나, 배치 서버를 사용해 상태가 FAILED인 사가를 재시작
            throw new OrderCancelFailureException("주문 전체 취소 실패: " + order.getOrderId());
        }
    }
}
