package com.nhnacademy.payment.service.impl;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.config.TossPaymentClient;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentResponse;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import com.nhnacademy.payment.exception.PaymentSaveFailException;
import com.nhnacademy.payment.exception.PaymentStateConflictException;
import com.nhnacademy.payment.exception.PaymentValidationException;
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
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
    private TossPaymentClient tossPaymentClient;

    @Mock
    private OrderRepository orderRepository;

    private PaymentRequestDto request;
    private Order order;
    private OrderDetails orderDetails;
    private TossPaymentResponseDto response;

    @BeforeEach
    void setup(){
        request = new PaymentRequestDto("test_paymentKey","ORD_test",50000);

        order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber","ORD_test");
        ReflectionTestUtils.setField(order,"orderStatus", OrderStatus.PENDING);

        orderDetails = mock(OrderDetails.class);
        lenient().when(orderDetails.totalPrice()).thenReturn(50000);
        ReflectionTestUtils.setField(order, "orderDetails", orderDetails);

        response = new TossPaymentResponseDto();ReflectionTestUtils.setField(response, "paymentKey", "test_paymentKey");
        ReflectionTestUtils.setField(response, "status", "DONE");
        ReflectionTestUtils.setField(response, "totalAmount", 50000);

    }

    @Test
    @DisplayName("결제 승인 성공  - 정상 호출")
    void confirmPayment_Success()
    {
        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        given(tossPaymentClient.confirm(any(),any(),any())).willReturn(response);

        PaymentResponse expectResponse = PaymentResponse.builder()
                .status("DONE")
                .build();

        given(paymentService.savePayment(response,order)).willReturn(expectResponse);

        PaymentResponse result = paymentFlowService.confirmPayment(request);

        assertEquals("DONE",result.status());

        verify(orderRepository).findOrderWithItemsByOrderNumber("ORD_test");
        verify(orderDetails).totalPrice();
        verify(tossPaymentClient).confirm("test_paymentKey","ORD_test",50000);
        verify(paymentService).savePayment(response,order);
    }

    @Test
    @DisplayName("결제 승인 실패 -(주문 정보 없음)")
    void confirmPayment_Failure()
    {
        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,()->paymentFlowService.confirmPayment(request));

        verify(tossPaymentClient, never()).confirm("test_paymentKey","ORD_test",50000);
    }

    @Test
    @DisplayName("결제 승인 실패 - (이미 결제가 승인된 주문 건)")
    void confirmPayment_Failure2(){

        ReflectionTestUtils.setField(order, "orderStatus", OrderStatus.COMPLETED);

        given(orderRepository.findOrderWithItemsByOrderNumber(request.orderNumber())).willReturn(Optional.of(order));

        assertThrows(PaymentStateConflictException.class,
                () -> paymentFlowService.confirmPayment(request));

        verify(tossPaymentClient,never()).confirm(any(),any(),any());
    }

    @Test
    @DisplayName("결제 승인 실패 - (외부 api 호출 실패)")
    void confirmPayment_Failure3(){
        given(orderRepository.findOrderWithItemsByOrderNumber(request.orderNumber())).willReturn(Optional.of(order));

        given(tossPaymentClient.confirm(any(),any(),any())).willThrow(
                new RuntimeException("Toss API Network Error")
        );

        assertThatThrownBy(() -> paymentFlowService.confirmPayment(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Toss API Network Error");

        verify(paymentService,never()).savePayment(any(),any());

    }

    @Test
    @DisplayName("결제 승인 실패 - (db 저장 오류 발생)")
     void confirmPayment_Failure4(){
        given(orderRepository.findOrderWithItemsByOrderNumber(request.orderNumber())).willReturn(Optional.of(order));

        given(tossPaymentClient.confirm(any(),any(),any())).willReturn(response);

        given(paymentService.savePayment(any(),any())).willThrow(
                new RuntimeException("DB Error"));

        assertThrows(PaymentSaveFailException.class, () -> paymentFlowService.confirmPayment(request));

        verify(orderRepository, times(1)).save(order);
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());

    }

    @Test
    @DisplayName("결제 승인 실패 - (주문 금액 불일치)")
    void confirmPayment_Failure5(){
        given(orderDetails.totalPrice()).willReturn(40000);

        given(orderRepository.findOrderWithItemsByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        // when & then
        assertThrows(PaymentValidationException.class,
                () -> paymentFlowService.confirmPayment(request));

        verify(tossPaymentClient, never()).confirm(any(), any(), any());
    }

    @Test
    @DisplayName("결제 취소 성공")
    void cancelPayment_Success()
    {
        String cancelReason = "단순 변심";
        String paymentKey = "test_paymentKey";
        Integer cancelAmount = 50000;

        Payment payment = mock(Payment.class);
        given(payment.getPaymentKey()).willReturn(paymentKey);


        Order orderMock = mock(Order.class);
        given(payment.getOrder()).willReturn(orderMock);
        given(orderMock.getOrderStatus()).willReturn(OrderStatus.COMPLETED);

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);


        TossPaymentResponseDto cancelResponse = new TossPaymentResponseDto();
        ReflectionTestUtils.setField(cancelResponse, "status", "CANCELED");
        given(tossPaymentClient.cancel(paymentKey, cancelReason,cancelAmount)).willReturn(cancelResponse);

        // when
        paymentFlowService.cancelPayment(request.orderNumber(), cancelReason, cancelAmount);

        // then
        verify(tossPaymentClient).cancel(paymentKey, cancelReason,cancelAmount);
        verify(paymentService).updatePaymentCanceledStatus(payment,cancelAmount);

    }

    @Test
    @DisplayName("결제 취소 실패 - 이미 취소된 (상태값)")
    void cancelPayment_Failure(){

        String cancelReason = "단순 변심";

        Payment payment = mock(Payment.class);
        Order orderMock = mock(Order.class);

        given(payment.getOrder()).willReturn(orderMock);
        given(orderMock.getOrderStatus()).willReturn(OrderStatus.CANCELED); // [핵심] 이미 취소됨

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);

        assertThrows(PaymentStateConflictException.class,
                () -> paymentFlowService.cancelPayment(request.orderNumber(), cancelReason,request.amount()));

        verify(tossPaymentClient, never()).cancel(any(), any(),any());

        verify(paymentService, never()).updatePaymentCanceledStatus(any(),any());

    }


    @Test
    @DisplayName("결제 취소 실패 - 결제 대기 상태 (승인 전)")
    void cancelPayment_Failure_Pending() {
        String cancelReason = "취소";

        Payment payment = mock(Payment.class);
        Order orderMock = mock(Order.class);

        given(payment.getOrder()).willReturn(orderMock);
        given(orderMock.getOrderStatus()).willReturn(OrderStatus.PENDING); // 아직 결제 안됨

        given(paymentService.getPaymentByOrderNumber(request.orderNumber())).willReturn(payment);

        assertThrows(PaymentStateConflictException.class,
                () -> paymentFlowService.cancelPayment(request.orderNumber(), cancelReason, request.amount()));
    }
}

