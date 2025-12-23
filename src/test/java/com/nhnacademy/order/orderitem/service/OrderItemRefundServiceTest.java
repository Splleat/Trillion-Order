package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.ordersaga.itemrefund.domain.NonMemberOrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.domain.OrderItemRefundSaga;
import com.nhnacademy.order.ordersaga.itemrefund.repository.NonMemberOrderItemRefundSagaRepository;
import com.nhnacademy.order.ordersaga.itemrefund.repository.OrderItemRefundSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderItemRefundServiceTest {

    @InjectMocks
    private OrderItemRefundService orderItemRefundService;

    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderItemRefundSagaRepository orderItemRefundSagaRepository;
    @Mock private NonMemberOrderItemRefundSagaRepository nonMemberOrderItemRefundSagaRepository;

    private OrderItem orderItem;
    private OrderItemRefundSaga memberSaga;
    private NonMemberOrderItemRefundSaga nonMemberSaga;

    @BeforeEach
    void setUp() {
        orderItem = mock(OrderItem.class);
        memberSaga = mock(OrderItemRefundSaga.class);
        nonMemberSaga = mock(NonMemberOrderItemRefundSaga.class);
    }

    // --- completeOrderItem (회원) 테스트 ---

    @Test
    @DisplayName("completeOrderItem: 회원 주문 상품 환불 성공 - 상태 변경 및 사가 브릿징")
    void completeOrderItem_Member_Success() {
        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING); // 아직 반품 안됨

        // when
        orderItemRefundService.completeOrderItem(orderItem, memberSaga);

        // then
        verify(orderItem).setOrderItemStatus(OrderItemStatus.RETURNED);
        verify(orderItemRepository).save(orderItem);
        verify(memberSaga).setBridged(true);
        verify(orderItemRefundSagaRepository).save(memberSaga);
    }

    @Test
    @DisplayName("completeOrderItem: 회원 주문 상품 이미 반품됨 - 사가만 브릿징")
    void completeOrderItem_Member_AlreadyReturned() {
        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURNED); // 이미 반품됨
        when(memberSaga.isBridged()).thenReturn(false);

        // when
        orderItemRefundService.completeOrderItem(orderItem, memberSaga);

        // then
        verify(orderItem, never()).setOrderItemStatus(any(OrderItemStatus.class)); // 상태 변경 호출 안됨
        verify(orderItemRepository, never()).save(any(OrderItem.class)); // OrderItem 저장 호출 안됨
        verify(memberSaga).setBridged(true); // 사가 브릿징은 호출됨
        verify(orderItemRefundSagaRepository).save(memberSaga); // 사가 저장 호출됨
    }

    @Test
    @DisplayName("completeOrderItem: 회원 주문 상품 이미 반품 및 사가 브릿지됨 - 아무것도 안함")
    void completeOrderItem_Member_AlreadyReturnedAndBridged() {
        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURNED); // 이미 반품됨
        when(memberSaga.isBridged()).thenReturn(true); // 사가도 이미 브릿지됨

        // when
        orderItemRefundService.completeOrderItem(orderItem, memberSaga);

        // then
        verify(orderItem, never()).setOrderItemStatus(any(OrderItemStatus.class));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verify(memberSaga, never()).setBridged(anyBoolean());
        verify(orderItemRefundSagaRepository, never()).save(any(OrderItemRefundSaga.class));
    }

    // --- completeNonMemberOrderItem (비회원) 테스트 ---

    @Test
    @DisplayName("completeNonMemberOrderItem: 비회원 주문 상품 환불 성공 - 상태 변경 및 사가 브릿징")
    void completeNonMemberOrderItem_NonMember_Success() {
        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.PREPARING);

        // when
        orderItemRefundService.completeNonMemberOrderItem(orderItem, nonMemberSaga);

        // then
        verify(orderItem).setOrderItemStatus(OrderItemStatus.RETURNED);
        verify(orderItemRepository).save(orderItem);
        verify(nonMemberSaga).setBridged(true);
        verify(nonMemberOrderItemRefundSagaRepository).save(nonMemberSaga);
    }

    @Test
    @DisplayName("completeNonMemberOrderItem: 비회원 주문 상품 이미 반품됨 - 사가만 브릿징")
    void completeNonMemberOrderItem_NonMember_AlreadyReturned() {
        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURNED);
        when(nonMemberSaga.isBridged()).thenReturn(false);

        // when
        orderItemRefundService.completeNonMemberOrderItem(orderItem, nonMemberSaga);

        // then
        verify(orderItem, never()).setOrderItemStatus(any(OrderItemStatus.class));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verify(nonMemberSaga).setBridged(true);
        verify(nonMemberOrderItemRefundSagaRepository).save(nonMemberSaga);
    }

    @Test
    @DisplayName("completeNonMemberOrderItem: 비회원 주문 상품 이미 반품 및 사가 브릿지됨 - 아무것도 안함")
    void completeNonMemberOrderItem_NonMember_AlreadyReturnedAndBridged() {
        // given
        when(orderItem.getOrderItemStatus()).thenReturn(OrderItemStatus.RETURNED);
        when(nonMemberSaga.isBridged()).thenReturn(true);

        // when
        orderItemRefundService.completeNonMemberOrderItem(orderItem, nonMemberSaga);

        // then
        verify(orderItem, never()).setOrderItemStatus(any(OrderItemStatus.class));
        verify(orderItemRepository, never()).save(any(OrderItem.class));
        verify(nonMemberSaga, never()).setBridged(anyBoolean());
        verify(nonMemberOrderItemRefundSagaRepository, never()).save(any(NonMemberOrderItemRefundSaga.class));
    }
}
