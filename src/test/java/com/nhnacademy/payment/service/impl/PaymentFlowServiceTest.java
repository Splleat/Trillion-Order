package com.nhnacademy.payment.service.impl;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.config.PaymentGateway;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentApiResponse;
import com.nhnacademy.payment.entity.Payment;
import com.nhnacademy.payment.entity.PaymentProvider;
import com.nhnacademy.payment.exception.*;
import com.nhnacademy.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentFlowServiceTest {

    @InjectMocks
    private PaymentFlowService paymentFlowService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentGatewayRoutingService routingService;

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private OrderRepository orderRepository;

    private PaymentRequestDto request;
    private Order order;
    private OrderDetails orderDetails;
    private PaymentApiResponse response;

    // Mock Users
    private PaymentUser memberUser;
    private PaymentUser otherMemberUser;
    private PaymentUser guestUser;
    private PaymentUser adminUser;

    @BeforeEach
    void setup() {
        // Request 설정
        request = new PaymentRequestDto("test_paymentKey", "ORD_test", 50000, "TOSS");

        // Order 설정
        order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber", "ORD_test");
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.PENDING);
        ReflectionTestUtils.setField(order, "memberId", 1L); // 기본: 회원 주문

        orderDetails = mock(OrderDetails.class);
        lenient().when(orderDetails.totalPrice()).thenReturn(50000);
        ReflectionTestUtils.setField(order, "orderDetails", orderDetails);

        // Response 설정 (Record Builder 사용)
        response = PaymentApiResponse.builder()
                .paymentKey("test_paymentKey")
                .orderId("ORD_test")
                .status("DONE")
                .totalAmount(50000)
                .provider("TOSS")
                .build();

        // User Mocks 설정
        memberUser = mock(PaymentUser.class);
        lenient().when(memberUser.memberId()).thenReturn(1L);
        lenient().when(memberUser.isMember()).thenReturn(true);
        lenient().when(memberUser.role()).thenReturn("ROLE_MEMBER");

        otherMemberUser = mock(PaymentUser.class);
        lenient().when(otherMemberUser.memberId()).thenReturn(2L);
        lenient().when(otherMemberUser.isMember()).thenReturn(true);
        lenient().when(otherMemberUser.role()).thenReturn("ROLE_MEMBER");

        guestUser = mock(PaymentUser.class);
        lenient().when(guestUser.memberId()).thenReturn(null);
        lenient().when(guestUser.isMember()).thenReturn(false);
        lenient().when(guestUser.role()).thenReturn("ROLE_USER");

        adminUser = mock(PaymentUser.class);
        lenient().when(adminUser.role()).thenReturn("ROLE_ADMIN");
    }

    // ==========================================
    // confirmPayment 테스트
    // ==========================================

    @Test
    @DisplayName("결제 승인 성공 - 회원 본인 주문")
    void confirmPayment_Success_Member() {
        // given
        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        // Routing & Gateway Mocking
        given(routingService.getGateway("TOSS")).willReturn(paymentGateway);
        given(paymentGateway.confirm(any(), any(), any())).willReturn(response);

        Payment expectPayment = mock(Payment.class);
        given(paymentService.savePayment(response, order)).willReturn(expectPayment);

        // when
        Payment result = paymentFlowService.confirmPayment(memberUser, request);

        // then
        assertEquals(expectPayment, result);
        verify(paymentGateway).confirm("test_paymentKey", "ORD_test", 50000);
        verify(paymentService).savePayment(response, order);
    }

    @Test
    @DisplayName("결제 승인 성공 - 비회원 주문")
    void confirmPayment_Success_Guest() {
        // given
        ReflectionTestUtils.setField(order, "memberId", null); // 비회원 주문으로 변경

        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        given(routingService.getGateway("TOSS")).willReturn(paymentGateway);
        given(paymentGateway.confirm(any(), any(), any())).willReturn(response);

        Payment expectPayment = mock(Payment.class);
        given(paymentService.savePayment(response, order)).willReturn(expectPayment);

        // when
        Payment result = paymentFlowService.confirmPayment(guestUser, request);

        // then
        assertEquals(expectPayment, result);
    }

    @Test
    @DisplayName("결제 승인 실패 - 타 회원의 주문 접근")
    void confirmPayment_Failure_OtherMember() {
        // given: 주문자는 memberId=1, 요청자는 memberId=2
        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentFlowService.confirmPayment(otherMemberUser, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("회원 주문 정보가 일치하지 않습니다.");

        verify(paymentGateway, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("결제 승인 실패 - 비회원이 회원 주문 접근")
    void confirmPayment_Failure_GuestAccessMemberOrder() {
        // given: 주문에는 memberId가 있는데, 비회원이 요청
        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        // when & then
        assertThatThrownBy(() -> paymentFlowService.confirmPayment(guestUser, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비회원은 회원의 주문에 접근할 수 없습니다.");
    }

    @Test
    @DisplayName("결제 승인 실패 - 주문 정보 없음")
    void confirmPayment_Failure_OrderNotFound() {
        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> paymentFlowService.confirmPayment(memberUser, request));
    }

    @Test
    @DisplayName("결제 승인 실패 - 주문 금액 불일치")
    void confirmPayment_Failure_AmountMismatch() {
        given(orderDetails.totalPrice()).willReturn(40000); // 40000 != 50000
        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        assertThrows(PaymentAmountMissMatchException.class,
                () -> paymentFlowService.confirmPayment(memberUser, request));
    }
    @Test
    @DisplayName("결제 승인 실패 - 이미 결제가 승인된 주문 건")
    void confirmPayment_Failure_AlreadyApproved() {
        // given
        // 1. 주문 조회 성공 설정
        given(orderRepository.findOrderWithItemsByOrderNumber(request.orderNumber()))
                .willReturn(Optional.of(order));

        // 2. 주문 상태를 이미 '완료(COMPLETED)'된 상태로 설정 [핵심]
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);

        // when & then
        // 3. confirmPayment 호출 시 PaymentAlreadyApprovedException이 발생하는지 검증
        assertThrows(PaymentAlreadyApprovedException.class,
                () -> paymentFlowService.confirmPayment(memberUser, request));

        // 4. 검증: 이미 승인된 건이므로 Gateway(PG사)로 요청을 보내면 안 됨
        verify(routingService, never()).getGateway(any());
        verify(paymentGateway, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("결제 승인 실패 - DB 저장 오류로 인한 롤백")
    void confirmPayment_Failure_DbSaveError() {
        // given
        given(orderRepository.findOrderWithItemsByOrderNumber(request.orderNumber())).willReturn(Optional.of(order));
        given(routingService.getGateway("TOSS")).willReturn(paymentGateway);
        given(paymentGateway.confirm(any(), any(), any())).willReturn(response);

        // DB 저장 시 예외 발생
        given(paymentService.savePayment(any(), any())).willThrow(new RuntimeException("DB Error"));

        // when & then
        assertThrows(PaymentSaveFailException.class, () -> paymentFlowService.confirmPayment(memberUser, request));

        // 롤백 로직 검증: 결제 취소가 호출되었는지
        verify(paymentGateway).cancel(request.paymentKey(), "서버 데이터베이스 저장 오류", response.totalAmount());
        // 주문 상태가 CANCELED로 변경 후 저장되었는지
        verify(orderRepository).save(order);
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
    }

    // ==========================================
    // cancelPaymentByMember 테스트
    // ==========================================

    @Test
    @DisplayName("결제 취소 성공 - 회원 본인")
    void cancelPaymentByMember_Success() {
        // given
        String cancelReason = "단순 변심";
        Integer cancelAmount = 50000;

        Payment payment = mock(Payment.class);
        given(payment.getOrder()).willReturn(order); // order memberId = 1
        given(payment.getPaymentKey()).willReturn("test_paymentKey");
        given(payment.getProvider()).willReturn(PaymentProvider.TOSS);

        // 상태 통과 조건 설정
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);
        given(routingService.getGateway("TOSS")).willReturn(paymentGateway);

        PaymentApiResponse cancelResponse = PaymentApiResponse.builder()
                .status("CANCELED")
                .build();
        given(paymentGateway.cancel(any(), any(), any())).willReturn(cancelResponse);

        // when
        paymentFlowService.cancelPaymentByMember(request.orderNumber(), cancelReason, cancelAmount, memberUser);

        // then
        verify(paymentGateway).cancel("test_paymentKey", cancelReason, cancelAmount);
        verify(paymentService).updatePaymentCanceledStatus(payment, cancelAmount);
    }

    @Test
    @DisplayName("결제 취소 성공 - 관리자 (권한 무시)")
    void cancelPaymentByMember_Success_Admin() {
        // given
        String cancelReason = "관리자 직권 취소";

        Payment payment = mock(Payment.class);
        given(payment.getOrder()).willReturn(order);
        given(payment.getPaymentKey()).willReturn("test_paymentKey");
        given(payment.getProvider()).willReturn(PaymentProvider.TOSS);
        given(payment.getBalanceAmount()).willReturn(50000); // 전체 취소 가정

        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);
        given(routingService.getGateway("TOSS")).willReturn(paymentGateway);

        PaymentApiResponse cancelResponse = PaymentApiResponse.builder().status("CANCELED").build();
        given(paymentGateway.cancel(any(), any(), any())).willReturn(cancelResponse);

        // when
        paymentFlowService.cancelPaymentByMember(request.orderNumber(), cancelReason, null, adminUser);

        // then
        verify(paymentGateway).cancel(any(), any(), any());
    }

    @Test
    @DisplayName("결제 취소 실패 - 권한 없음(타인 주문)")
    void cancelPaymentByMember_Failure_Permission() {
        // given
        Payment payment = mock(Payment.class);
        given(payment.getOrder()).willReturn(order); // order owner = 1

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);

        // when & then
        assertThatThrownBy(() ->
                paymentFlowService.cancelPaymentByMember(request.orderNumber(), "취소", 50000, otherMemberUser)
        ).isInstanceOf(IllegalArgumentException.class);

        verify(paymentGateway, never()).cancel(any(), any(), any());
    }

    @Test
    @DisplayName("결제 취소 실패 - 이미 취소된 주문")
    void cancelPaymentByMember_Failure_AlreadyCanceled() {
        // given
        Payment payment = mock(Payment.class);
        given(payment.getOrder()).willReturn(order);
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.CANCELED); // 이미 취소됨

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);

        // when & then
        assertThrows(PaymentAlreadyCanceledException.class,
                () -> paymentFlowService.cancelPaymentByMember(request.orderNumber(), "취소", 50000, memberUser));
    }


    @Test
    @DisplayName("결제 취소 실패 - 아직 결제가 승인되지 않은건에 대한 주문 결제 취소시")
    void cancelPaymentByMember_Failure_Pending() {
        // given
        Payment payment = mock(Payment.class);
        given(payment.getOrder()).willReturn(order);
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.PENDING); // 이미 취소됨

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);

        // when & then
        assertThrows(PaymentNotApprovedException.class,
                () -> paymentFlowService.cancelPaymentByMember(request.orderNumber(), "취소", 50000, memberUser));
    }


    @Test
    @DisplayName("결제 취소 실패 - 토스 응답 상태 이상")
    void cancelPayment_Failure_GatewayStatusMismatch() {
        // given
        Payment payment = mock(Payment.class);
        given(payment.getOrder()).willReturn(order);
        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);
        given(payment.getPaymentKey()).willReturn("key");
        given(payment.getProvider()).willReturn(PaymentProvider.TOSS);

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);
        given(routingService.getGateway("TOSS")).willReturn(paymentGateway);

        PaymentApiResponse failResponse = PaymentApiResponse.builder()
                .status("FAILED") // CANCELED 아님
                .build();
        given(paymentGateway.cancel(any(), any(), any())).willReturn(failResponse);

        // when
        paymentFlowService.cancelPaymentByMember(request.orderNumber(), "취소", 50000, memberUser);

        // then
        // DB 상태 변경 로직이 호출되지 않아야 함
        verify(paymentService, never()).updatePaymentCanceledStatus(any(), any());
    }
}