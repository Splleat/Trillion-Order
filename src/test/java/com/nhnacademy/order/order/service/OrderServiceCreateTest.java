package com.nhnacademy.order.order.service;

import com.nhnacademy.order.client.dto.BookResponse;
import com.nhnacademy.order.client.service.BookService;
import com.nhnacademy.order.client.service.CouponService;
import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.domain.OrdererInfo;
import com.nhnacademy.order.order.domain.ReceiverInfo;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import com.nhnacademy.order.packaging.domain.Packaging;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceCreateTest {

    @InjectMocks
    private OrderServiceImpl orderServiceImpl;

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

    // 공통 성공 로직 Mock 설정 (성공 테스트들에서 재사용)
    private void mockSuccessPath() {
        OrderDetails detailsForInitialOrder = OrderDetails.createInitial(
                defaultOrderRequest.receiverPostCode(), defaultOrderRequest.deliveryDate(),
                defaultOrderRequest.pointUsage(), defaultOrderRequest.couponId()
        );

        OrdererInfo ordererInfoForInitialOrder = new OrdererInfo(
                defaultOrderRequest.ordererName(),
                defaultOrderRequest.ordererContact()
        );

        ReceiverInfo receiverInfoForInitialOrder = new ReceiverInfo(
                defaultOrderRequest.receiverName(),
                defaultOrderRequest.receiverContact(),
                defaultOrderRequest.receiverAddress()
        );

        Order initialOrder = Order.createInitial(1L, null, ordererInfoForInitialOrder, receiverInfoForInitialOrder, detailsForInitialOrder);
        when(orderCreateService.createInitialOrder(any(), any(), any(), any(), any())).thenReturn(initialOrder);

        List<BookResponse> bookResponses = List.of(
                new BookResponse(1L, 10000),
                new BookResponse(2L, 20000)
        );
        when(bookService.getBookInfos(anyList())).thenReturn(bookResponses.stream().collect(Collectors.toMap(BookResponse::bookId, b -> b)));
        when(packagingRepository.findAllById(anyList())).thenReturn(List.of(new Packaging(1L, "선물포장", 500)));

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

    @BeforeEach
    void setUp() {
        // 모든 테스트가 반드시 공유하는 최소한의 준비
        defaultOrderRequest = createDefaultOrderRequest();
    }

    @Test
    @DisplayName("주문 생성 - 성공")
    void createOrder_Success() {
        UserInfo userInfo = new UserInfo(1L, "MEMBER");

        // given
        mockSuccessPath(); // 성공 경로에 필요한 공통 Mock 설정
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(new DeliveryPolicy(1L, 5000, 30000)));
        when(couponService.calculateDiscount(anyLong(), anyInt())).thenReturn(5000);

        // when
        OrderResponse orderResponse = orderServiceImpl.createOrder(userInfo, defaultOrderRequest);

        // then
        verify(bookService, times(1)).getBookInfos(anyList());
        verify(orderCreateOrchestrator, times(1)).processCreateOrder(anyLong(), any(Order.class));
        verify(couponService, times(1)).calculateDiscount(anyLong(), anyInt());
        assertThat(orderResponse.totalPrice()).isEqualTo(34500);
        assertThat(orderResponse.deliveryFee()).isZero();
    }

    @Test
    @DisplayName("주문 생성 - 성공: 쿠폰/포인트 미사용")
    void createOrder_Success_NoDiscount() {
        UserInfo userInfo = new UserInfo(1L, "MEMBER");

        // given
        mockSuccessPath(); // 성공 경로에 필요한 공통 Mock 설정
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(new DeliveryPolicy(1L, 5000, 30000)));
        OrderCreateRequest requestWithNoDiscount = new OrderCreateRequest(
                defaultOrderRequest.ordererName(), defaultOrderRequest.ordererContact(), defaultOrderRequest.deliveryDate(),
                defaultOrderRequest.receiverName(), defaultOrderRequest.receiverContact(), defaultOrderRequest.receiverAddress(),
                defaultOrderRequest.receiverPostCode(), null, 0, null, defaultOrderRequest.orderItems()
        );

        // when
        OrderResponse orderResponse = orderServiceImpl.createOrder(userInfo, requestWithNoDiscount);

        // then
        verify(couponService, never()).calculateDiscount(any(), anyInt());
        verify(bookService, times(1)).getBookInfos(anyList());
        verify(orderCreateOrchestrator, times(1)).processCreateOrder(anyLong(), any(Order.class));
        assertThat(orderResponse.totalPrice()).isEqualTo(40500);
    }

    @Test
    @DisplayName("주문 생성 - 성공: 배송비 부과")
    void createOrder_Success_WithDeliveryFee() {
        UserInfo userInfo = new UserInfo(1L, "MEMBER");

        // given
        mockSuccessPath(); // 성공 경로에 필요한 공통 Mock 설정
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(new DeliveryPolicy(1L, 5000, 40000)));
        when(couponService.calculateDiscount(anyLong(), anyInt())).thenReturn(5000);

        // when
        OrderResponse orderResponse = orderServiceImpl.createOrder(userInfo, defaultOrderRequest);

        // then
        verify(bookService, times(1)).getBookInfos(anyList());
        verify(orderCreateOrchestrator, times(1)).processCreateOrder(anyLong(), any(Order.class));
        assertThat(orderResponse.deliveryFee()).isEqualTo(5000);
        assertThat(orderResponse.totalPrice()).isEqualTo(39500);
    }

    @Test
    @DisplayName("주문 생성 - 성공: 비회원")
    void createOrder_Success_NonMember() {
        // given
        mockSuccessPath(); // 성공 경로에 필요한 공통 Mock 설정
        when(deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()).thenReturn(Optional.of(new DeliveryPolicy(1L, 5000, 30000)));
        String rawPassword = "test-password-1234";
        String encodedPassword = "encoded-password-!@#$";
        OrderCreateRequest nonMemberRequest = new OrderCreateRequest(
                "비회원", "010-0000-0000", defaultOrderRequest.deliveryDate(),
                "받는사람", "010-1111-2222", "비회원 주소", "99999",
                rawPassword, 0, null, defaultOrderRequest.orderItems()
        );
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        // when
        OrderResponse orderResponse = orderServiceImpl.createOrder(null, nonMemberRequest);

        // then
        verify(passwordEncoder, times(1)).encode(rawPassword);
        verify(orderCreateService, times(1)).createInitialOrder(isNull(), eq(encodedPassword), any(), any(), any());
        verify(bookService, times(1)).getBookInfos(anyList());
        verify(orderCreateOrchestrator, times(1)).processCreateOrder(isNull(), any(Order.class));
        assertThat(orderResponse.totalPrice()).isEqualTo(40500);
    }

    // ParameterizedTest에 시나리오 목록을 제공하는 정적 메소드
    private static Stream<Arguments> sagaFailureScenarios() {
        return Stream.of(
                Arguments.of("재고 부족", new RuntimeException("재고 부족 시뮬레이션")),
                Arguments.of("쿠폰 사용 불가", new RuntimeException("쿠폰 사용 시뮬레이션")),
                Arguments.of("포인트 부족", new RuntimeException("포인트 부족 시뮬레이션"))
        );
    }

    @DisplayName("주문 생성 - 실패: 사가 프로세스 실패")
    @ParameterizedTest(name = "[{index}] 시나리오: {0}")
    @MethodSource("sagaFailureScenarios")
    void createOrder_Failure_SagaFails(String scenarioName, RuntimeException exceptionToThrow) {
        UserInfo userInfo = new UserInfo(1L, "MEMBER");

        // given
        // 실패 경로는 성공 경로와 Mock 설정이 다르므로, mockSuccessPath()를 호출하지 않음.
        // 최소한의 설정만 진행
        OrderDetails detailsForInitialOrder = OrderDetails.createInitial(
                defaultOrderRequest.receiverPostCode(), defaultOrderRequest.deliveryDate(),
                defaultOrderRequest.pointUsage(), defaultOrderRequest.couponId()
        );
        Order initialOrder = Order.createInitial(1L, null, new OrdererInfo(null, null), new ReceiverInfo(null, null, null), detailsForInitialOrder);
        when(orderCreateService.createInitialOrder(any(), any(), any(), any(), any())).thenReturn(initialOrder);

        // 2. 각 시나리오에 맞는 예외를 던지도록 설정
        doThrow(exceptionToThrow).when(orderCreateOrchestrator).processCreateOrder(anyLong(), any(Order.class));

        // when & then
        // 1. createOrder를 실행하면, 설정된 예외가 그대로 전파되어야 함
        assertThrows(RuntimeException.class, () -> {
            orderServiceImpl.createOrder(userInfo, defaultOrderRequest);
        });

        // 2. 추가 검증: 보상 트랜잭션이 올바르게 실행되었는지 확인
        // 성공 경로인 completeOrder는 호출되지 않아야 함
        verify(orderCreateService, never()).completeOrder(any(), anyInt(), anyInt(), anyInt(), any());
        // 실패 경로인 createFailureOrder는 반드시 1번 호출되어야 함
        verify(orderCreateService, times(1)).createFailureOrder(any(Order.class));
        // 실패 테스트에서는 아래 Mock들이 호출되지 않으므로 UnnecessaryStubbingException이 발생하지 않음
        verify(bookService, never()).getBookInfos(anyList());
    }
}
