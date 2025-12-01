package com.nhnacademy.order.order.service;

import com.nhnacademy.order.client.dto.BookResponse;
import com.nhnacademy.order.client.service.BookService;
import com.nhnacademy.order.client.service.CouponService;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import com.nhnacademy.order.packaging.domain.Packaging;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @InjectMocks
    private OrderServiceImpl orderServiceImpl;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private PackagingRepository packagingRepository;
    @Mock
    private DeliveryPolicyRepository deliveryPolicyRepository;
    @Mock
    private BookService bookService;
    @Mock
    private CouponService couponService;
    @Mock
    private OrderCreateService orderCreateService;
    @Mock
    private OrderCreateOrchestrator orderCreateOrchestrator;
    @Mock
    private PasswordEncoder passwordEncoder;

    private OrderCreateRequest defaultOrderRequest;
    private Order initialOrder;

    // 도우미 메소드: 기본 테스트 데이터 생성
    private OrderCreateRequest createDefaultOrderRequest() {
        List<OrderItemCreateRequest> orderItemRequests = List.of(
                new OrderItemCreateRequest(1L, 2, 1L, null),
                new OrderItemCreateRequest(2L, 1, null, 1L)
        );
        return new OrderCreateRequest(
                "홍길동", "010-1234-5678", LocalDateTime.now().plusDays(1),
                "이순신", "010-9876-5432", "서울시 강남구", "12345",
                null, 1000, 1L, orderItemRequests
        );
    }

    // @BeforeEach: 모든 테스트 실행 전에 공통적으로 필요한 Mock 설정
    @BeforeEach
    void setUp() {
        // given: 공통 Mock 객체 행동 정의
        defaultOrderRequest = createDefaultOrderRequest();
        initialOrder = Order.createInitial(1L, null, new OrdererInfo("홍길동", "010-1234-5678"), new ReceiverInfo("이순신", "010-9876-5432", "서울시 강남구"), OrderDetails.createInitial("12345", null, 0, 0L));

        when(orderCreateService.createInitialOrder(any(), any(), any(), any(), any())).thenReturn(initialOrder);

        List<BookResponse> bookResponses = List.of(
                new BookResponse(1L, 10000),
                new BookResponse(2L, 20000)
        );
        when(bookService.getBookInfos(anyList())).thenReturn(bookResponses.stream().collect(Collectors.toMap(BookResponse::bookId, b -> b)));

        when(packagingRepository.findAllById(anyList())).thenReturn(List.of(new Packaging(1L, "선물포장", 500)));
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(new DeliveryPolicy(1L, 30000, 5000)));

        // completeOrder가 호출되면, 전달받은 order 객체의 상태를 변경하도록 설정
        doAnswer(invocation -> {
            Order orderToComplete = invocation.getArgument(0);
            int originPrice = invocation.getArgument(1);
            int totalPrice = invocation.getArgument(2);
            int deliveryFee = invocation.getArgument(3);
            List<OrderItem> items = invocation.getArgument(4);

            orderToComplete.completeOrder(originPrice, totalPrice, deliveryFee);
            items.forEach(orderToComplete::addOrderItem);
            return null;
        }).when(orderCreateService).completeOrder(any(Order.class), anyInt(), anyInt(), anyInt(), anyList());
    }

    @Test
    @DisplayName("주문 생성 - 성공")
    void createOrder_Success() {
        // given: 이 테스트에만 필요한 특정 Mock 설정
        when(couponService.calculateDiscount(anyLong(), anyInt())).thenReturn(5000);

        // when
        OrderResponse orderResponse = orderServiceImpl.createOrder(1L, defaultOrderRequest);

        // then
        verify(couponService, times(1)).calculateDiscount(anyLong(), anyInt());
        assertThat(orderResponse.totalPrice()).isEqualTo(34500);
        assertThat(orderResponse.deliveryFee()).isZero();
    }

    @Test
    @DisplayName("주문 ID로 단건 조회 - 성공")
    void findOrderByOrderId_Success() {
        // given
        long orderId = 1L;
        OrderBaseResponse dummyBaseResponse = new OrderBaseResponse(orderId, 1L, "ORD-1234", LocalDateTime.now(), OrderStatus.PENDING, 35000, 0, new OrdererInfo("홍길동", "010-1234-5678"), new ReceiverInfo("이순신", "010-9876-5432", "서울"));
        List<OrderItemResponse> dummyItems = List.of(new OrderItemResponse(orderId, 1L, 2, 20000, 500, OrderItemStatus.SHIPPED));
        when(orderRepository.findBaseOrderById(orderId)).thenReturn(Optional.of(dummyBaseResponse));
        when(orderItemRepository.findOrderItemByOrder_OrderId(orderId)).thenReturn(dummyItems);

        // when
        OrderResponse response = orderServiceImpl.findOrderByOrderId(orderId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.ordererInfo().ordererName()).isEqualTo("홍길동");
        assertThat(response.orderItems()).hasSize(1);
        verify(orderRepository, times(1)).findBaseOrderById(orderId);
        verify(orderItemRepository, times(1)).findOrderItemByOrder_OrderId(orderId);
    }

    @Test
    @DisplayName("주문 ID로 단건 조회 - 실패: 존재하지 않는 주문")
    void findOrderByOrderId_Failure_NotFound() {
        // given
        long nonExistentOrderId = 999L;
        when(orderRepository.findBaseOrderById(nonExistentOrderId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(OrderNotFoundException.class, () -> orderServiceImpl.findOrderByOrderId(nonExistentOrderId));
        verify(orderRepository, times(1)).findBaseOrderById(nonExistentOrderId);
        verify(orderItemRepository, never()).findOrderItemByOrder_OrderId(anyLong());
    }

    // ================================================================================
    // ||                         여기부터 테스트 뼈대 코드                           ||
    // ================================================================================

    @Test
    @DisplayName("주문 생성 - 성공: 쿠폰/포인트 미사용")
    void createOrder_Success_NoDiscount() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 생성 - 성공: 배송비 부과")
    void createOrder_Success_WithDeliveryFee() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 생성 - 성공: 비회원")
    void createOrder_Success_NonMember() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 생성 - 실패: 재고 부족")
    void createOrder_Failure_InsufficientStock() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("비회원 주문 조회 - 성공")
    void findOrderByOrderNumber_Success() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("비회원 주문 조회 - 실패: 비밀번호 불일치")
    void findOrderByOrderNumber_Failure_PasswordMismatch() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("회원 주문 목록 조회 - 성공")
    void findAllOrderByMemberId_Success() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("회원 주문 목록 조회 - 성공: 주문 내역 없음")
    void findAllOrderByMemberId_Success_NoOrders() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 상품 상태 변경 - 성공: 배송중")
    void patchOrderItemStatus_Success_Shipped() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 상품 상태 변경 - 성공: 회원 환불 승인")
    void patchOrderItemStatus_Success_MemberReturn() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 상품 상태 변경 - 실패: 존재하지 않는 주문상품")
    void patchOrderItemStatus_Failure_NotFound() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("비회원 주문 상품 상태 변경 - 성공")
    void patchOrderItemStatusForNonMember_Success() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("비회원 주문 상품 상태 변경 - 실패: 비밀번호 불일치")
    void patchOrderItemStatusForNonMember_Failure_PasswordMismatch() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 전체 취소 - 성공")
    void cancelOrder_Success() {
        // TODO: 테스트 구현
    }

    @Test
    @DisplayName("주문 전체 취소 - 실패: 취소 불가능 상태")
    void cancelOrder_Failure_CannotCancel() {
        // TODO: 테스트 구현
    }
}