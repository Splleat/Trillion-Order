package com.nhnacademy.order.scheduler;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.order.service.OrderCancelService;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.orderitem.service.OrderItemRefundService;
import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.cancellation.repository.OrderCancelSagaRepository;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.repository.OrderCreateSagaRepository;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.repository.OrderItemRefundSagaRepository;
import com.nhnacademy.order.ordersaga.itemrefund.service.OrderItemRefundOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ReconciliationService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCreateSagaRepository orderCreateSagaRepository;
    private final OrderCancelSagaRepository orderCancelSagaRepository;
    private final OrderItemRefundSagaRepository orderItemRefundSagaRepository;
    private final OrderCancelService orderCancelService;
    private final OrderCreateOrchestrator orderCreateOrchestrator;
    private final OrderItemRefundService orderItemRefundService;
    private final OrderCancelOrchestrator orderCancelOrchestrator;
    private final OrderItemRefundOrchestrator orderItemRefundOrchestrator;

    @Transactional
    public void compensateStuckCreationOrder(Order order) {
        try {
            orderCreateSagaRepository.findByOrderId(order.getOrderId()).ifPresentOrElse(saga -> {
                orderCreateOrchestrator.compensate(saga, order);
                order.setOrderStatus(OrderStatus.CREATION_FAILED);
                orderRepository.save(order);
                log.info("[도메인 스케줄러] AWAITING_POST_PROCESSING 상태 주문 보상 처리 성공: {}", order.getOrderId());
            }, () -> {
                log.warn("[도메인 스케줄러] AWAITING_POST_PROCESSING 상태 주문에 대한 생성 사가를 찾을 수 없어 FAILED 처리: {}", order.getOrderId());
                order.setOrderStatus(OrderStatus.CREATION_FAILED);
                orderRepository.save(order);
            });
        } catch (Exception e) {
            log.error("[도메인 스케줄러] AWAITING_POST_PROCESSING 상태 주문 보상 처리 실패: {}", order.getOrderId(), e);
        }
    }

    @Transactional
    public void processStuckCancellationOrder(Order order) {
        try {
            orderCancelService.completeOrder(order);
        } catch (Exception e) {
            log.error("[도메인 스케줄러] 주문 취소 최종 처리 실패: {}", order.getOrderId(), e);
        }
    }

    @Transactional
    public void processStuckRefundOrderItem(OrderItem orderItem) {
        try {
            orderItemRefundService.completeOrderItem(orderItem);
        } catch (Exception e) {
            log.error("[도메인 스케줄러] 주문 상품 환불 최종 처리 실패: {}", orderItem.getOrderItemId(), e);
        }
    }

    @Transactional
    public void processStuckCreateSagaCompensation(OrderCreateSaga saga) {
        try {
            orderRepository.findOrderWithItemsByOrderId(saga.getOrderId()).ifPresent(order -> {
                orderCreateOrchestrator.compensate(saga, order);
            });
        } catch (Exception e) {
            log.error("[도메인 스케줄러] 멈춰있는 주문 생성 사가 보상 처리 실패: {}", saga.getOrderId(), e);
        }
    }

    @Transactional
    public void processStuckCancelSagaRetry(OrderCancelSaga saga) {
        try {
            log.info("[사가 스케줄러] 멈춰있는 주문 취소 사가 재시도 시작: {}", saga.getOrderId());
            orderRepository.findOrderWithItemsByOrderId(saga.getOrderId()).ifPresent(order -> {
                orderCancelOrchestrator.processCancelOrder(order.getMemberId(), order);
            });
        } catch (Exception e) {
            log.error("[사가 스케줄러] 멈춰있는 주문 취소 사가 재시도 실패: {}", saga.getOrderId(), e);
        }
    }

    @Transactional
    public void processStuckRefundItemSagaRetry(OrderItemRefundSaga saga) {
        try {
            log.info("[사가 스케줄러] 멈춰있는 주문 상품 환불 사가 재시도 시작: {}", saga.getOrderItemId());
            orderRepository.findOrderWithItemsByOrderId(saga.getOrderId()).ifPresent(order -> {
                OrderItem orderItem = order.findOrderItemInOrder(saga.getOrderItemId());
                int deliveryFee = order.getOrderDetails().deliveryFee();
                orderItemRefundOrchestrator.processItemRefund(order.getMemberId(), order, orderItem, deliveryFee);
            });
        } catch (Exception e) {
            log.error("[사가 스케줄러] 멈춰있는 주문 상품 환불 사가 재시도 실패: {}", saga.getOrderId(), e);
        }
    }

    @Transactional
    public void compensateForCreateSagaBridgingFailure(OrderCreateSaga saga) {
        try {
            orderRepository.findOrderWithItemsByOrderId(saga.getOrderId()).ifPresent(order -> {
                orderCreateOrchestrator.compensate(saga, order);
                order.setOrderStatus(OrderStatus.CREATION_FAILED);
                orderRepository.save(order);
            });
        } catch (Exception e) {
            log.error("[도메인 스케줄러] 완료되었으나 브릿징 안된 주문 생성 사가 보상 처리 실패: {}", saga.getOrderId(), e);
        }
    }

    @Transactional
    public void processCompletedCancelSagaBridge(OrderCancelSaga saga) {
        try {
            orderRepository.findOrderWithItemsByOrderId(saga.getOrderId()).ifPresent(order -> {
                if (order.getOrderStatus() == OrderStatus.PENDING) {
                    order.setOrderStatus(OrderStatus.AWAITING_CANCELLATION);
                    orderRepository.save(order);
                    saga.setBridged(true);
                    orderCancelSagaRepository.save(saga);
                }
            });
        } catch (Exception e) {
            log.error("[도메인 스케줄러] 완료된 주문 취소 사가 브릿징 실패: {}", saga.getOrderId(), e);
        }
    }

    @Transactional
    public void processCompletedRefundSagaBridge(OrderItemRefundSaga saga) {
        try {
            orderItemRepository.findById(saga.getOrderItemId()).ifPresent(orderItem -> {
                if (orderItem.getOrderItemStatus() == OrderItemStatus.DELIVERED) {
                    orderItem.setOrderItemStatus(OrderItemStatus.AWAITING_REFUND_FINALIZATION);
                    orderItemRepository.save(orderItem);
                    saga.setBridged(true);
                    orderItemRefundSagaRepository.save(saga);
                }
            });
        } catch (Exception e) {
            log.error("[도메인 스케줄러] 완료된 주문 상품 환불 사가 브릿징 실패: {}", saga.getOrderItemId(), e);
        }
    }
}
