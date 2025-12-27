package com.nhnacademy.order.ordersaga.cancellation.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.client.coupon.service.CouponService;
import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.exception.OrderCancelFailureException;
import com.nhnacademy.order.order.service.OrderFinalizerCancelService;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.ordersaga.cancellation.domain.CancelSagaStep;
import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderCancelOrchestrator {
    private final SagaUpdateService sagaUpdateService;

    private final MemberService memberService;
    private final CouponService couponService;
    private final BookService bookService;
    private final PaymentFlowService paymentFlowService;

    private final OrderFinalizerCancelService orderFinalizerCancelService;

    public void processCancelOrder(Long memberId, Order order) {
        if (order.getOrderStatus() == OrderStatus.PENDING || order.getOrderStatus() == OrderStatus.CREATING) {
            throw new OrderCancelFailureException("결제가 완료되지 않은 주문은 취소할 수 없습니다.");
        }

        // 1. 사가 시작 (사가 생성과 동시에 주문 상태를 '주문 취소 중'으로 변경)
        OrderCancelSaga saga = orderFinalizerCancelService.cancelStart(order);

        int pointUsage = order.getOrderDetails().pointUsage();
        Set<OrderCoupon> usedCoupons = order.getOrderCoupons();
        Map<Long, Integer> quantityMap = order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getBookId, OrderItem::getQuantity));

        try {
            // 2. 환불 (PaymentFlowService 호출)
            try {
                PaymentUser systemUser = new PaymentUser(memberId, null, "ROLE_ADMIN", memberId != null);
                paymentFlowService.cancelPaymentByMember(
                    order.getOrderNumber(), 
                    "사용자 요청에 의한 주문 취소", 
                    null, // null이면 전액 취소
                    systemUser
                );
            } catch (Exception e) {
                // 이미 취소된 경우 예외 무시하고 진행
                if (isAlreadyCanceledException(e)) {
                    log.info("이미 결제 취소된 주문입니다: {}", order.getOrderNumber());
                } else {
                    throw e; 
                }
            }
            
            sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.PAYMENT_CANCELED);

            // 3. 멤버 API에 사용한 포인트만큼 증가 요청
            if (memberId != null && pointUsage > 0) {
                memberService.increasePoint(saga.getSagaId(), memberId, order.getOrderId(), pointUsage);
                sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.POINT_REFUNDED);
            }

            // 4. 쿠폰 API에 사용한 쿠폰 반환 요청
            if (memberId != null && !usedCoupons.isEmpty()) {
                usedCoupons.forEach(orderCoupon ->
                    couponService.withdrawCoupon(saga.getSagaId(), memberId, orderCoupon.getCouponId())
                );
                sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.COUPON_RESTORED);
            }

            // 5. 도서 API에 재고 증가 요청
            bookService.increaseStocks(saga.getSagaId(), quantityMap);
            sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.STOCK_INCREASED);

            // 6. 사가 성공
            sagaUpdateService.updateCancelSagaStatus(saga, SagaStatus.COMPLETED);

            // 7. 사가 - 도메인 브릿징 완료
            orderFinalizerCancelService.cancelOrder(order, saga);

        } catch (Exception e) {
            log.error("주문 전체 취소 실패 (Saga ID: {}). 스케줄러가 재시도합니다.", saga.getSagaId(), e);
            sagaUpdateService.updateCancelSagaStatus(saga, SagaStatus.FAILED);
            // 롤백 없음! CANCELING 상태 유지
            throw new OrderCancelFailureException("주문 취소 처리 중 지연이 발생했습니다. 잠시 후 완료될 예정입니다.");
        }
    }

    public void retry(OrderCancelSaga saga, Order order) {
        if (saga.getOverallStatus() == SagaStatus.COMPLETED) {
            return;
        }

        UUID sagaId = saga.getSagaId();
        Long memberId = order.getMemberId();
        int pointUsage = order.getOrderDetails().pointUsage();
        Set<OrderCoupon> usedCoupons = order.getOrderCoupons();
        Map<Long, Integer> quantityMap = order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getBookId, OrderItem::getQuantity));

        CancelSagaStep currentStep = saga.getLastCompletedStep();

        try {
            // Step 1. 결제 취소 재시도
            if (currentStep.ordinal() < CancelSagaStep.PAYMENT_CANCELED.ordinal()) {
                try {
                    PaymentUser systemUser = new PaymentUser(memberId, null, "ROLE_ADMIN", memberId != null);
                    paymentFlowService.cancelPaymentByMember(
                        order.getOrderNumber(),
                        "시스템에 의한 주문 취소 재시도",
                        null,
                        systemUser
                    );
                } catch (Exception e) {
                    if (isAlreadyCanceledException(e)) {
                         log.info("이미 결제 취소된 주문입니다 (재시도 중): {}", order.getOrderNumber());
                    } else {
                        throw e; // 실패 시 재시도 종료 (다음 스케줄러 턴에 다시 시도)
                    }
                }
                sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.PAYMENT_CANCELED);
                currentStep = CancelSagaStep.PAYMENT_CANCELED;
            }

            // Step 2. 포인트 복구 재시도
            if (memberId != null && pointUsage > 0 && currentStep.ordinal() < CancelSagaStep.POINT_REFUNDED.ordinal()) {
                memberService.increasePoint(sagaId, memberId, order.getOrderId(), pointUsage);
                sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.POINT_REFUNDED);
                currentStep = CancelSagaStep.POINT_REFUNDED;
            }

            // Step 3. 쿠폰 복구 재시도
            if (memberId != null && !usedCoupons.isEmpty() && currentStep.ordinal() < CancelSagaStep.COUPON_RESTORED.ordinal()) {
                usedCoupons.forEach(orderCoupon ->
                    couponService.withdrawCoupon(sagaId, memberId, orderCoupon.getCouponId())
                );
                sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.COUPON_RESTORED);
                currentStep = CancelSagaStep.COUPON_RESTORED;
            }

            // Step 4. 재고 복구 재시도
            if (currentStep.ordinal() < CancelSagaStep.STOCK_INCREASED.ordinal()) {
                bookService.increaseStocks(sagaId, quantityMap);
                sagaUpdateService.updateCancelSagaStep(saga, CancelSagaStep.STOCK_INCREASED);
            }

            sagaUpdateService.updateCancelSagaStatus(saga, SagaStatus.COMPLETED);
            orderFinalizerCancelService.cancelOrder(order, saga);

        } catch (Exception e) {
            sagaUpdateService.updateCancelSagaStatus(saga, SagaStatus.FAILED);
            log.error("주문 취소 사가 재시도 실패: {}", sagaId, e);
        }
    }

    private boolean isAlreadyCanceledException(Exception e) {
        String message = e.getMessage();
        String className = e.getClass().getSimpleName();
        
        return className.contains("PaymentAlreadyCanceledException") || 
               className.contains("PaymentAlreadyApprovedException") || // 상태 불일치 관련
               (message != null && message.contains("이미 전액 취소된 결제"));
    }
}