package com.nhnacademy.order.ordersaga.service;

import com.nhnacademy.order.ordersaga.cancelation.repository.OrderCancelSagaRepository;
import com.nhnacademy.order.ordersaga.creation.repository.OrderCreateSagaRepository;
import com.nhnacademy.order.ordersaga.itemrefund.repository.OrderItemRefundSagaRepository;
import com.nhnacademy.order.ordersaga.cancelation.domain.CancelSagaStep;
import com.nhnacademy.order.ordersaga.cancelation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.creation.domain.CreateSagaStep;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.ItemRefundSagaStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SagaUpdateService {
    private final OrderCreateSagaRepository orderCreateSagaRepository;
    private final OrderCancelSagaRepository orderCancelSagaRepository;
    private final OrderItemRefundSagaRepository orderItemRefundSagaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCreateSagaStep(OrderCreateSaga orderCreateSaga, CreateSagaStep step) {
        orderCreateSaga.setLastCompletedStep(step);
        orderCreateSagaRepository.save(orderCreateSaga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCreateSagaStatus(OrderCreateSaga orderCreateSaga, SagaStatus status) {
        orderCreateSaga.setOverallStatus(status);
        orderCreateSagaRepository.save(orderCreateSaga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCancelSagaStep(OrderCancelSaga orderCancelSaga, CancelSagaStep step) {
        orderCancelSaga.setLastCompletedStep(step);
        orderCancelSagaRepository.save(orderCancelSaga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCancelSagaStatus(OrderCancelSaga orderCancelSaga, SagaStatus status) {
        orderCancelSaga.setOverallStatus(status);
        orderCancelSagaRepository.save(orderCancelSaga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateItemRefundSagaStep(OrderItemRefundSaga orderItemRefundSaga, ItemRefundSagaStep step) {
        orderItemRefundSaga.setLastCompletedStep(step);
        orderItemRefundSagaRepository.save(orderItemRefundSaga);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateItemRefundSagaStatus(OrderItemRefundSaga orderItemRefundSaga, SagaStatus status) {
        orderItemRefundSaga.setOverallStatus(status);
        orderItemRefundSagaRepository.save(orderItemRefundSaga);
    }
}