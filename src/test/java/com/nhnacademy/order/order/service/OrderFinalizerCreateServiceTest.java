package com.nhnacademy.order.order.service;

import com.nhnacademy.order.client.book.dto.BookResponse;
import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationRequest;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import com.nhnacademy.order.client.coupon.service.CouponService;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.PackagingInfo;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.repository.OrderCreateSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderFinalizerCreateServiceTest {

    @InjectMocks
    private OrderFinalizerCreateService orderFinalizerCreateService;

    @Mock private DeliveryPolicyRepository deliveryPolicyRepository;
    @Mock private BookService bookService;
    @Mock private CouponService couponService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderCreateSagaRepository orderCreateSagaRepository;

    private Order order;
    private OrderCreateSaga saga;

    @BeforeEach
    void setUp() {
        OrdererInfo ordererInfo = new OrdererInfo("주문자", "010-1234-5678");
        ReceiverInfo receiverInfo = new ReceiverInfo("수령인", "010-9876-5432", "주소");
        OrderDetails orderDetails = OrderDetails.createInitial(null, null, 1000); // 포인트 1000원 사용

        order = Order.createInitial(1L, null, ordererInfo, receiverInfo, orderDetails);

        OrderItem item1 = OrderItem.createInitial(order, 101L, 2, null, PackagingInfo.create("일반포장", 500));
        order.addOrderItem(item1);

        OrderCoupon orderCoupon = OrderCoupon.createInitial(1L);
        order.addOrderCoupon(orderCoupon);

        saga = OrderCreateSaga.create(UUID.randomUUID(), order.getOrderId());
    }

    @Test
    @DisplayName("주문 생성 완료 처리 - 성공 (회원, 쿠폰 사용)")
    void finalizeOrderCreation_Success_MemberWithCoupon() {
        // given
        BookResponse bookResponse = new BookResponse(101L, "테스트 책", 10000, true, "url");
        when(bookService.getBookInfos(anyList())).thenReturn(Map.of(101L, bookResponse));

        CouponCalculationResponse couponResponse = new CouponCalculationResponse(101L, 2000, List.of(new CouponCalculationResponse.ItemDiscount(101L, 2000)));
        when(couponService.calculateDiscount(any(CouponCalculationRequest.class))).thenReturn(couponResponse);

        DeliveryPolicy deliveryPolicy = DeliveryPolicy.create(3000, 20000); // 2만원 미만 배송비 3000원
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(deliveryPolicy));

        // when
        orderFinalizerCreateService.finalizeOrderCreation(order, saga);

        // then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();

        assertThat(savedOrder.getOrderStatus()).isEqualTo(OrderStatus.PENDING);

        OrderDetails details = savedOrder.getOrderDetails();
        // 원가 = (책 가격 10000 + 포장비 500) * 2 = 21000
        assertThat(details.originPrice()).isEqualTo(21000);
        // 쿠폰 할인 = 2000
        assertThat(details.couponDiscountAmount()).isEqualTo(2000);
        // 배송비: 21000 - 2000 = 19000 < 20000 이므로 3000원
        assertThat(details.deliveryFee()).isEqualTo(3000);
        // 최종가 = 21000 - 2000(쿠폰) - 1000(포인트) + 3000(배송비) = 21000
        assertThat(details.totalPrice()).isEqualTo(21000);

        ArgumentCaptor<OrderCreateSaga> sagaCaptor = ArgumentCaptor.forClass(OrderCreateSaga.class);
        verify(orderCreateSagaRepository).save(sagaCaptor.capture());
        assertThat(sagaCaptor.getValue().isBridged()).isTrue();
    }

    @Test
    @DisplayName("이미 PENDING 상태인 주문은 처리하지 않음")
    void finalizeOrderCreation_ShouldDoNothing_WhenStatusIsPending() {
        // given
        order.setOrderStatus(OrderStatus.PENDING);

        // when
        orderFinalizerCreateService.finalizeOrderCreation(order, saga);

        // then
        verify(orderRepository, never()).save(any());
        verify(orderCreateSagaRepository, never()).save(any());
        verify(bookService, never()).getBookInfos(anyList());
    }
}
