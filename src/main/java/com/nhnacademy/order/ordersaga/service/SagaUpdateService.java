package com.nhnacademy.order.ordersaga.service;

import com.nhnacademy.order.order.repository.OrderCancelSagaRepository;
import com.nhnacademy.order.order.repository.OrderCreateSagaRepository;
import com.nhnacademy.order.ordersaga.cancelation.domain.CancelSagaStep;
import com.nhnacademy.order.ordersaga.cancelation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.creation.domain.CreateSagaStep;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SagaUpdateService {
    private final OrderCreateSagaRepository orderCreateSagaRepository;
    private final OrderCancelSagaRepository orderCancelSagaRepository;

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
}