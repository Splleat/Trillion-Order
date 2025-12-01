package com.nhnacademy.order.ordersaga.itemrefund.service;

import com.nhnacademy.order.client.service.BookService;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.exception.OrderItemRefundFailureException;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberOrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberRefundSagaStep;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import com.nhnacademy.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Service
public class NonMemberOrderItemRefundOrchestrator {
    private final SagaUpdateService sagaUpdateService;
    private final BookService bookService;
    // private final PaymentService paymentService;

    public void processNonMemberItemRefund(Order order, OrderItem orderItem) {
        NonMemberOrderItemRefundSaga saga = NonMemberOrderItemRefundSaga.create(order.getOrderId(), orderItem.getOrderItemId());

        UUID sagaId = saga.getSagaId();

        Map<Long, Integer> quantityMap = Map.of(orderItem.getOrderItemId(), orderItem.getQuantity());

        // 1. 사가 시작
        sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.STARTED);

        try {
            // 2. 환불
            // TODO: 환불 로직 수정
            // paymentService.refundPayment(...)
            sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.PAYMENT_REFUNDED);

            bookService.increaseStocks(sagaId, quantityMap);
            sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.STOCK_INCREASED);

            sagaUpdateService.updateNonMemberItemRefundSagaStatus(saga, SagaStatus.COMPLETED);
        } catch (Exception e) {
            sagaUpdateService.updateNonMemberItemRefundSagaStatus(saga, SagaStatus.FAILED);
            throw new OrderItemRefundFailureException("주문 상품 환불 실패: " + orderItem.getOrderItemId());
        }
    }
}
