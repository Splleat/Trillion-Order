package com.nhnacademy.order.order.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderCreateFailureException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceCreateTest {

    @InjectMocks
    private OrderServiceImpl orderServiceImpl;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderCreateService orderCreateService;
    @Mock
    private OrderFinalizerService orderFinalizerService;
    @Mock
    private OrderCreateOrchestrator orderCreateOrchestrator;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserInfo userInfo;
    private OrderCreateRequest orderCreateRequest;
    private Order initialOrder;

    @BeforeEach
    void setUp() {
        userInfo = new UserInfo(1L, "MEMBER");

        List<OrderItemCreateRequest> itemRequests = List.of(
            new OrderItemCreateRequest(1L, 2, 101L, 1L, LocalDateTime.now().plusDays(3)),
            new OrderItemCreateRequest(2L, 1, null, 2L, LocalDateTime.now().plusDays(3))
        );

        orderCreateRequest = new OrderCreateRequest(
            "홍길동", "010-1234-5678", LocalDateTime.now().plusDays(1),
            "이순신", "010-9876-5432", "서울시 강남구", "12345",
            null, 1000, 101L, itemRequests
        );

        // createInitialOrder가 반환할 기본 Order 객체 설정
        initialOrder = Order.createInitial(
            userInfo.userId(),
            null,
            new OrdererInfo(orderCreateRequest.ordererName(), orderCreateRequest.ordererContact()),
            new ReceiverInfo(orderCreateRequest.receiverName(), orderCreateRequest.receiverContact(), orderCreateRequest.receiverAddress()),
            OrderDetails.createInitial(orderCreateRequest.receiverPostCode(), orderCreateRequest.deliveryDate(), orderCreateRequest.pointUsage(), orderCreateRequest.couponId())
        );
    }

    @Test
    @DisplayName("주문 생성 - 성공")
    void createOrder_Success() {
        // given
        // 초기 주문 생성 Mock
        when(orderCreateService.createInitialOrder(any(), any(), any(), any(), any(), any())).thenReturn(initialOrder);
        // 사가 프로세스 성공 Mock
        doNothing().when(orderCreateOrchestrator).processCreateOrder(any(OrderCreateSaga.class), any(Order.class));
        // 최종 처리 성공 Mock
        doNothing().when(orderFinalizerService).finalizeOrderCreation(any(Order.class), any(OrderCreateSaga.class));

        // when
        OrderResponse orderResponse = orderServiceImpl.createOrder(userInfo, orderCreateRequest);

        // then
        // 1. 초기 주문이 생성되었는가?
        verify(orderCreateService, times(1)).createInitialOrder(
            eq(userInfo.userId()),
            isNull(),
            any(OrdererInfo.class),
            any(ReceiverInfo.class),
            any(OrderDetails.class),
            eq(orderCreateRequest.orderItems())
        );

        // 2. 사가 프로세스가 올바르게 호출되었는가?
        verify(orderCreateOrchestrator, times(1)).processCreateOrder(any(OrderCreateSaga.class), eq(initialOrder));

        // 3. 최종 처리 서비스가 올바르게 호출되었는가?
        verify(orderFinalizerService, times(1)).finalizeOrderCreation(eq(initialOrder), any(OrderCreateSaga.class));

        // 4. 응답이 정상적으로 생성되었는가?
        assertThat(orderResponse).isNotNull();
        assertThat(orderResponse.ordererInfo().ordererName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("주문 생성 - 실패: 사가 프로세스 실패 시 보상 로직 호출")
    void createOrder_Failure_SagaFails() {
        // given
        // 초기 주문 생성 Mock
        when(orderCreateService.createInitialOrder(any(), any(), any(), any(), any(), any())).thenReturn(initialOrder);

        // 사가 프로세스가 실패하도록 설정
        RuntimeException sagaException = new OrderCreateFailureException("외부 서비스 호출 실패");
        doThrow(sagaException).when(orderCreateOrchestrator).processCreateOrder(any(OrderCreateSaga.class), any(Order.class));

        // 보상 로직 Mock
        doNothing().when(orderCreateOrchestrator).compensate(any(OrderCreateSaga.class), any(Order.class));

        // when & then
        // 1. createOrder를 실행하면, 설정된 예외가 그대로 전파되어야 함
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            orderServiceImpl.createOrder(userInfo, orderCreateRequest);
        });
        assertThat(thrown).isSameAs(sagaException);

        // 2. 보상(compensate) 오케스트레이터가 호출되었는지 검증
        verify(orderCreateOrchestrator, times(1)).compensate(any(OrderCreateSaga.class), eq(initialOrder));

        // 3. 주문 상태가 'CREATION_FAILED'로 변경되고 저장되었는지 검증
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, times(1)).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getOrderStatus()).isEqualTo(OrderStatus.CREATION_FAILED);
        
                // 4. 성공 경로인 최종 처리 서비스는 호출되지 않아야 함
        
                verify(orderFinalizerService, never()).finalizeOrderCreation(any(), any());
        
            }
        
        
        
            @Test
        
            @DisplayName("비회원 주문 생성 - 성공")
        
            void createOrder_NonMember_Success() {
        
                // given
        
                // 비회원(userInfo = null) 시나리오
        
                UserInfo nonMemberInfo = null;
        
        
        
                // 비회원용 요청 데이터 (쿠폰 ID와 포인트 사용 없음)
        
                OrderCreateRequest nonMemberRequest = new OrderCreateRequest(
        
                    "비회원", "010-0000-0000", LocalDateTime.now().plusDays(1),
        
                    "수령인", "010-1111-2222", "서울시 종로구", "54321",
        
                    "password123", 0, null, orderCreateRequest.orderItems() // nonMemberPassword 설정, pointUsage=0, couponId=null
        
                );
        
        
        
                // 비회원용 초기 Order 객체
        
                Order nonMemberInitialOrder = Order.createInitial(
        
                    null, // 비회원이므로 userId는 null
        
                    "encodedPassword", // nonMemberPassword는 인코딩됨
        
                    new OrdererInfo(nonMemberRequest.ordererName(), nonMemberRequest.ordererContact()),
        
                    new ReceiverInfo(nonMemberRequest.receiverName(), nonMemberRequest.receiverContact(), nonMemberRequest.receiverAddress()),
        
                    OrderDetails.createInitial(nonMemberRequest.receiverPostCode(), nonMemberRequest.deliveryDate(), nonMemberRequest.pointUsage(), nonMemberRequest.couponId())
        
                );
        
        
        
                // Mock 설정
        
                when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        
                when(orderCreateService.createInitialOrder(any(), any(), any(), any(), any(), any())).thenReturn(nonMemberInitialOrder);
        
                doNothing().when(orderCreateOrchestrator).processCreateOrder(any(OrderCreateSaga.class), any(Order.class));
        
                doNothing().when(orderFinalizerService).finalizeOrderCreation(any(Order.class), any(OrderCreateSaga.class));
        
        
        
        
        
                // when
        
                OrderResponse orderResponse = orderServiceImpl.createOrder(nonMemberInfo, nonMemberRequest);
        
        
        
                // then
        
                // 1. 초기 주문 생성 시 userId가 null로, nonMemberPassword가 인코딩된 값으로 전달되었는지 검증
        
                verify(orderCreateService, times(1)).createInitialOrder(
        
                    isNull(),
        
                    eq("encodedPassword"),
        
                    any(OrdererInfo.class),
        
                    any(ReceiverInfo.class),
        
                    any(OrderDetails.class),
        
                    eq(nonMemberRequest.orderItems())
        
                );
        
        
        
                // 2. 사가 프로세스가 올바르게 호출되었는가?
        
                verify(orderCreateOrchestrator, times(1)).processCreateOrder(any(OrderCreateSaga.class), eq(nonMemberInitialOrder));
        
        
        
                // 3. 최종 처리 서비스가 올바르게 호출되었는가?
        
                verify(orderFinalizerService, times(1)).finalizeOrderCreation(eq(nonMemberInitialOrder), any(OrderCreateSaga.class));
        
        
        
                // 4. 응답이 정상적으로 생성되었는가?
        
                assertThat(orderResponse).isNotNull();
        
                assertThat(orderResponse.ordererInfo().ordererName()).isEqualTo("비회원");
        
            }
        
        }
        
        