package com.nhnacademy.payment.service.impl;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.order.service.OrderService;
import com.nhnacademy.payment.config.TossPaymentClient;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentResponse;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import com.nhnacademy.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.management.relation.Relation;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    private TossPaymentResponseDto response;

    @BeforeEach
    void setup(){
        request = new PaymentRequestDto("test_paymentKey","ORD_test",50000);

        order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber","ORD_test");

        response = new TossPaymentResponseDto();
        ReflectionTestUtils.setField(response, "paymentKey","test_paymentKey");
        ReflectionTestUtils.setField(response,"status","DONE");

    }

    @Test
    @DisplayName("결제 승인 성공  - 정상 호출")
    void confirmPayment_Success()
    {
        given(orderRepository.findByOrderNumber("ORD_test")).willReturn(Optional.of(order));

        given(tossPaymentClient.confirm(any(),any(),any())).willReturn(response);

        PaymentResponse expectResponse = PaymentResponse.builder()
                .status("DONE")
                .build();

        given(paymentService.savePayment(response,order)).willReturn(expectResponse);

        PaymentResponse result = paymentFlowService.ConfirmPayment(request);

        assertEquals("DONE",result.status());

        verify(orderRepository).findByOrderNumber("ORD_test");
        verify(tossPaymentClient).confirm("test_paymentKey","ORD_test",50000);
        verify(paymentService).savePayment(response,order);
    }

    @Test
    @DisplayName("결제 승인 실패 -(주문 정보 없음)")
    void confirmPayment_Failure()
    {
        given(orderRepository.findByOrderNumber("ORD_test")).willReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,()->paymentFlowService.ConfirmPayment(request));

        verify(tossPaymentClient, never()).confirm("test_paymentKey","ORD_test",50000);
    }


}