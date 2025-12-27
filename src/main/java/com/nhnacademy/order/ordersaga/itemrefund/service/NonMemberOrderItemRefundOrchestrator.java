package com.nhnacademy.order.ordersaga.itemrefund.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.exception.PolicyNotConfiguredException;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.exception.OrderItemRefundFailureException;
import com.nhnacademy.order.orderitem.service.OrderItemRefundService;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberOrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberRefundSagaStep;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
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
    private final PaymentFlowService paymentFlowService;

    private final DeliveryPolicyRepository deliveryPolicyRepository;
    private final OrderItemRefundService orderItemRefundService;

    public void processNonMemberItemRefund(Order order, OrderItem orderItem) {
        if (orderItem.getOrderItemStatus() != OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND &&
                orderItem.getOrderItemStatus() != OrderItemStatus.RETURN_REQUESTED_DAMAGED) {
            throw new OrderStatusTransitionException("반품 요청 상태가 아닌 상품: " + orderItem.getOrderItemId());
        }

        UUID sagaId = UUID.randomUUID();

        NonMemberOrderItemRefundSaga saga = NonMemberOrderItemRefundSaga.create(sagaId, order.getOrderId(), orderItem.getOrderItemId());

        // 배송비 정책 조회
        int deliveryFee = getDeliveryFee();

        // 1. 환불 금액 계산 (상품 가격 * 수량 - 쿠폰 할인액)
        // 비회원은 쿠폰을 못 쓰지만, 혹시 모르니 로직은 유지하거나 0으로 가정
        int refundAmount = Math.max(0, (orderItem.getPrice() * orderItem.getQuantity()) - orderItem.getCouponDiscountAmount());

        // 단순 변심인 경우 배송비 차감
        if (orderItem.getOrderItemStatus() == OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND) {
            refundAmount = Math.max(0, refundAmount - deliveryFee);
        }

        Map<Long, Integer> quantityMap = Map.of(orderItem.getBookId(), orderItem.getQuantity());

        // 1. 사가 시작
        sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.STARTED);

        try {
            // 2. 환불 (결제 부분 취소)
            try {
                PaymentUser systemUser = new PaymentUser(null, "GUEST", "ROLE_ADMIN", false);
                paymentFlowService.cancelPaymentByMember(
                    order.getOrderNumber(),
                    "비회원 반품 요청 (상품 ID: " + orderItem.getOrderItemId() + ")",
                    refundAmount, // 부분 취소 금액
                    systemUser
                );
            } catch (Exception e) {
                 if (isAlreadyCanceledException(e)) {
                    log.info("이미 결제 취소된 주문입니다: {}", order.getOrderNumber());
                } else {
                    throw e; 
                }
            }
            sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.PAYMENT_REFUNDED);

            // 3. 재고 증가
            bookService.increaseStocks(saga.getSagaId(), quantityMap);
            sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.STOCK_INCREASED);

            // 4. 완료
            sagaUpdateService.updateNonMemberItemRefundSagaStatus(saga, SagaStatus.COMPLETED);

            // 5. 도메인 반영 (환불 금액 저장 포함)
            orderItem.setRefundPrice(refundAmount);
            orderItemRefundService.completeNonMemberOrderItem(orderItem, saga);
        } catch (Exception e) {
            log.error("비회원 주문 상품 환불 사가 처리 실패: {}", sagaId, e);
            sagaUpdateService.updateNonMemberItemRefundSagaStatus(saga, SagaStatus.FAILED);
            throw new OrderItemRefundFailureException("주문 상품 환불 실패: " + orderItem.getOrderItemId());
        }
    }

    public void retry(NonMemberOrderItemRefundSaga saga, OrderItem orderItem) {
        if (saga.getOverallStatus() == SagaStatus.COMPLETED) {
            return;
        }

        Order order = orderItem.getOrder();
        UUID sagaId = saga.getSagaId();

        Map<Long, Integer> quantityMap = Map.of(orderItem.getBookId(), orderItem.getQuantity());

        int deliveryFee = getDeliveryFee();

        int refundAmount = Math.max(0, (orderItem.getPrice() * orderItem.getQuantity()) - orderItem.getCouponDiscountAmount());

        if (orderItem.getOrderItemStatus() == OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND) {
            refundAmount = Math.max(0, refundAmount - deliveryFee);
        }

        NonMemberRefundSagaStep currentStep = saga.getLastCompletedStep();

        try {
            if (currentStep.ordinal() < NonMemberRefundSagaStep.PAYMENT_REFUNDED.ordinal()) {
                try {
                    PaymentUser systemUser = new PaymentUser(null, "GUEST", "ROLE_ADMIN", false);
                    paymentFlowService.cancelPaymentByMember(
                        order.getOrderNumber(),
                        "비회원 반품 재시도 (상품 ID: " + orderItem.getOrderItemId() + ")",
                        refundAmount,
                        systemUser
                    );
                } catch (Exception e) {
                     if (isAlreadyCanceledException(e)) {
                        log.info("이미 결제 취소된 주문입니다: {}", order.getOrderNumber());
                    } else {
                        throw e; 
                    }
                }
                sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.PAYMENT_REFUNDED);

                currentStep = NonMemberRefundSagaStep.PAYMENT_REFUNDED;
            }

            if (currentStep.ordinal() < NonMemberRefundSagaStep.STOCK_INCREASED.ordinal()) {
                bookService.increaseStocks(saga.getSagaId(), quantityMap);

                sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, NonMemberRefundSagaStep.STOCK_INCREASED);
            }

            sagaUpdateService.updateNonMemberItemRefundSagaStatus(saga, SagaStatus.COMPLETED);

            orderItem.setRefundPrice(refundAmount);
            orderItemRefundService.completeNonMemberOrderItem(orderItem, saga);
        } catch (Exception e) {
            sagaUpdateService.updateNonMemberItemRefundSagaStatus(saga, SagaStatus.FAILED);
            log.error("비회원 주문 상품 환불 사가 재시도 실패: {}", sagaId, e);
        }
    }

    private int getDeliveryFee() {
        DeliveryPolicy deliveryPolicy = deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()
                .orElseThrow(() -> new PolicyNotConfiguredException("배송 정책이 설정되지 않음"));

        return deliveryPolicy.getDeliveryPolicyFee();
    }

    private boolean isAlreadyCanceledException(Exception e) {
        String message = e.getMessage();
        String className = e.getClass().getSimpleName();
        return className.contains("PaymentAlreadyCanceledException") || 
               className.contains("PaymentAlreadyApprovedException") ||
               (message != null && message.contains("이미 전액 취소된 결제"));
    }
}
