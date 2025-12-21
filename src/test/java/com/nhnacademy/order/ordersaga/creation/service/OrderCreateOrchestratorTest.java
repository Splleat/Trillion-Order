package com.nhnacademy.order.ordersaga.creation.service;

import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.client.coupon.service.CouponService;
import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.service.OrderFinalizerCompensateService;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.ordersaga.creation.domain.CreateSagaStep;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.domain.SagaStatus;
import com.nhnacademy.order.ordersaga.service.SagaUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreateOrchestratorTest {

    @InjectMocks
    private OrderCreateOrchestrator orchestrator;

    @Mock
    private SagaUpdateService sagaUpdateService;
    @Mock
    private MemberService memberService;
    @Mock
    private CouponService couponService;
    @Mock
    private BookService bookService;
    @Mock
    private OrderFinalizerCompensateService orderFinalizerCompensateService;

    private OrderCreateSaga saga;
    private Order order;
    private UUID mockSagaId;
    private Long mockCouponId = 1L;

    @BeforeEach
    void setUp() {
        // Given: An order and its items
        OrderDetails orderDetails = new OrderDetails(
                LocalDateTime.now(), "12345", LocalDateTime.now().plusDays(3),
                3000, 5000, 0, 61000, 55000);

        order = new Order();
        ReflectionTestUtils.setField(order, "orderId", 1L);
        ReflectionTestUtils.setField(order, "memberId", 2L);
        ReflectionTestUtils.setField(order, "orderDetails", orderDetails);

        // Add OrderCoupon to the Order
        OrderCoupon orderCoupon = new OrderCoupon();
        ReflectionTestUtils.setField(orderCoupon, "couponId", mockCouponId);
        order.addOrderCoupon(orderCoupon);

        OrderItem orderItem = new OrderItem();
        ReflectionTestUtils.setField(orderItem, "bookId", 10L);
        ReflectionTestUtils.setField(orderItem, "quantity", 2);
        ReflectionTestUtils.setField(orderItem, "price", 5000);
        order.addOrderItem(orderItem);

        // Given: A saga is created for this order
        mockSagaId = UUID.randomUUID();
        saga = OrderCreateSaga.create(mockSagaId, order.getOrderId());
        
        // Simulate JPA generating the UUID for the sagaId
        ReflectionTestUtils.setField(saga, "sagaId", mockSagaId);
    }

    @Test
    @DisplayName("주문 생성 오케스트레이터 - 성공 케이스 (쿠폰, 포인트 모두 사용)")
    void processCreateOrder_Success_WithCouponAndPoint() {
        // given
        Map<Long, Integer> quantityMap = order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getBookId, OrderItem::getQuantity));
        int pointUsage = order.getOrderDetails().pointUsage();

        // when
        orchestrator.processCreateOrder(saga, order);

        // then
        InOrder inOrder = inOrder(sagaUpdateService, bookService, couponService, memberService);

        // 1. Verify saga steps were updated in order
        inOrder.verify(sagaUpdateService).updateCreateSagaStep(saga, CreateSagaStep.STARTED);
        inOrder.verify(sagaUpdateService).updateCreateSagaStep(saga, CreateSagaStep.STOCK_DECREASING);
        inOrder.verify(bookService).decreaseStocks(any(UUID.class), eq(quantityMap));
        inOrder.verify(sagaUpdateService).updateCreateSagaStep(saga, CreateSagaStep.STOCK_DECREASED);
        inOrder.verify(sagaUpdateService).updateCreateSagaStep(saga, CreateSagaStep.COUPON_APPLYING);
        inOrder.verify(couponService).applyCoupon(any(UUID.class), eq(order.getMemberId()), eq(mockCouponId));
        inOrder.verify(sagaUpdateService).updateCreateSagaStep(saga, CreateSagaStep.COUPON_APPLIED);
        inOrder.verify(sagaUpdateService).updateCreateSagaStep(saga, CreateSagaStep.POINT_USING);
        inOrder.verify(memberService).decreasePoint(any(UUID.class), eq(order.getMemberId()), eq(pointUsage));
        inOrder.verify(sagaUpdateService).updateCreateSagaStep(saga, CreateSagaStep.POINT_USED);

        // 2. Verify the final saga status is COMPLETED
        verify(sagaUpdateService, times(1)).updateCreateSagaStatus(saga, SagaStatus.COMPLETED);

        // 3. Verify compensation was NOT triggered
        verify(orderFinalizerCompensateService, never()).compensateOrder(any(), any());
        verify(bookService, never()).increaseStocks(any(UUID.class), any());
        verify(couponService, never()).withdrawCoupon(any(UUID.class), anyLong(), anyLong());
        verify(memberService, never()).increasePoint(any(UUID.class), anyLong(), anyInt());
    }
}
