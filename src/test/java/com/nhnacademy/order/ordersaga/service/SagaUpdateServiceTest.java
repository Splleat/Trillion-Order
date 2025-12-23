package com.nhnacademy.order.ordersaga.service;

import com.nhnacademy.order.ordersaga.cancellation.domain.CancelSagaStep;
import com.nhnacademy.order.ordersaga.cancellation.domain.OrderCancelSaga;
import com.nhnacademy.order.ordersaga.cancellation.repository.OrderCancelSagaRepository;
import com.nhnacademy.order.ordersaga.creation.domain.CreateSagaStep;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.repository.OrderCreateSagaRepository;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.itemrefund.domain.ItemRefundSagaStep;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberOrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberRefundSagaStep;
import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.repository.NonMemberOrderItemRefundSagaRepository;
import com.nhnacademy.order.ordersaga.itemrefund.repository.OrderItemRefundSagaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaUpdateServiceTest {

    @InjectMocks
    private SagaUpdateService sagaUpdateService;

    @Mock private OrderCreateSagaRepository orderCreateSagaRepository;
    @Mock private OrderCancelSagaRepository orderCancelSagaRepository;
    @Mock private OrderItemRefundSagaRepository orderItemRefundSagaRepository;
    @Mock private NonMemberOrderItemRefundSagaRepository nonMemberOrderItemRefundSagaRepository;

    @Test
    @DisplayName("updateCreateSagaStep: 주문 생성 사가의 단계 업데이트 및 저장")
    void updateCreateSagaStep_ShouldUpdateAndSave() {
        // given
        OrderCreateSaga saga = mock(OrderCreateSaga.class);
        CreateSagaStep step = CreateSagaStep.POINT_USED;

        // when
        sagaUpdateService.updateCreateSagaStep(saga, step);

        // then
        verify(saga).setLastCompletedStep(step);
        verify(orderCreateSagaRepository).save(saga);
    }

    @Test
    @DisplayName("updateCreateSagaStatus: 주문 생성 사가의 전체 상태 업데이트 및 저장")
    void updateCreateSagaStatus_ShouldUpdateAndSave() {
        // given
        OrderCreateSaga saga = mock(OrderCreateSaga.class);
        SagaStatus status = SagaStatus.COMPLETED;

        // when
        sagaUpdateService.updateCreateSagaStatus(saga, status);

        // then
        verify(saga).setOverallStatus(status);
        verify(orderCreateSagaRepository).save(saga);
    }

    @Test
    @DisplayName("updateCancelSagaStep: 주문 취소 사가의 단계 업데이트 및 저장")
    void updateCancelSagaStep_ShouldUpdateAndSave() {
        // given
        OrderCancelSaga saga = mock(OrderCancelSaga.class);
        CancelSagaStep step = CancelSagaStep.PAYMENT_CANCELED;

        // when
        sagaUpdateService.updateCancelSagaStep(saga, step);

        // then
        verify(saga).setLastCompletedStep(step);
        verify(orderCancelSagaRepository).save(saga);
    }

    @Test
    @DisplayName("updateCancelSagaStatus: 주문 취소 사가의 전체 상태 업데이트 및 저장")
    void updateCancelSagaStatus_ShouldUpdateAndSave() {
        // given
        OrderCancelSaga saga = mock(OrderCancelSaga.class);
        SagaStatus status = SagaStatus.FAILED;

        // when
        sagaUpdateService.updateCancelSagaStatus(saga, status);

        // then
        verify(saga).setOverallStatus(status);
        verify(orderCancelSagaRepository).save(saga);
    }

    @Test
    @DisplayName("updateItemRefundSagaStep: 주문 상품 환불 사가(회원)의 단계 업데이트 및 저장")
    void updateItemRefundSagaStep_ShouldUpdateAndSave() {
        // given
        OrderItemRefundSaga saga = mock(OrderItemRefundSaga.class);
        ItemRefundSagaStep step = ItemRefundSagaStep.STOCK_INCREASED;

        // when
        sagaUpdateService.updateItemRefundSagaStep(saga, step);

        // then
        verify(saga).setLastCompletedStep(step);
        verify(orderItemRefundSagaRepository).save(saga);
    }

    @Test
    @DisplayName("updateItemRefundSagaStatus: 주문 상품 환불 사가(회원)의 전체 상태 업데이트 및 저장")
    void updateItemRefundSagaStatus_ShouldUpdateAndSave() {
        // given
        OrderItemRefundSaga saga = mock(OrderItemRefundSaga.class);
        SagaStatus status = SagaStatus.COMPLETED;

        // when
        sagaUpdateService.updateItemRefundSagaStatus(saga, status);

        // then
        verify(saga).setOverallStatus(status);
        verify(orderItemRefundSagaRepository).save(saga);
    }

    @Test
    @DisplayName("updateNonMemberItemRefundSagaStep: 주문 상품 환불 사가(비회원)의 단계 업데이트 및 저장")
    void updateNonMemberItemRefundSagaStep_ShouldUpdateAndSave() {
        // given
        NonMemberOrderItemRefundSaga saga = mock(NonMemberOrderItemRefundSaga.class);
        NonMemberRefundSagaStep step = NonMemberRefundSagaStep.PAYMENT_REFUNDED;

        // when
        sagaUpdateService.updateNonMemberItemRefundSagaStep(saga, step);

        // then
        verify(saga).setLastCompletedStep(step);
        verify(nonMemberOrderItemRefundSagaRepository).save(saga);
    }

    @Test
    @DisplayName("updateNonMemberItemRefundSagaStatus: 주문 상품 환불 사가(비회원)의 전체 상태 업데이트 및 저장")
    void updateNonMemberItemRefundSagaStatus_ShouldUpdateAndSave() {
        // given
        NonMemberOrderItemRefundSaga saga = mock(NonMemberOrderItemRefundSaga.class);
        SagaStatus status = SagaStatus.FAILED;

        // when
        sagaUpdateService.updateNonMemberItemRefundSagaStatus(saga, status);

        // then
        verify(saga).setOverallStatus(status);
        verify(nonMemberOrderItemRefundSagaRepository).save(saga);
    }
}
