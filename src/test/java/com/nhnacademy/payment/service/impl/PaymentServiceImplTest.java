package com.nhnacademy.payment.service.impl;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import com.nhnacademy.payment.repository.PaymentRepository;
import com.nhnacademy.payment.service.PaymentService;
import com.nhnacademy.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentApplicationTests {
    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "tossSecretKey", "test-secret-key");
    }

//    @Test
//    @DisplayName("결제 대기 상태 (PENDIG)_성공 테스트")
//    void createPendingPayment(){
//        Long orderId = 1L;
//
//        Order mockOrder = mock(Order.class);
//
//        given(orderRepository.findById(orderId)).willReturn(Optional.of(mockOrder));
//
//        given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> {
//            Payment p = invocation.getArgument(0);
//            ReflectionTestUtils.setField(p, "paymentId", 1L); // ID 주입 시늉
//            return p;
//        });
//
//        Payment result = paymentService.createPendingPayment(orderId);
//
//        assertNotNull(result);
//        assertEquals(1L, result.getPaymentId());
//        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());
//        verify(orderRepository, times(1)).findById(orderId);
//        verify(paymentRepository, times(1)).save(any(Payment.class));
//    }
//
//    @Test
//    @DisplayName("결제 승인 성공 테스트")
//    void confirmPaymentSuccess(){
//        String paymentKey = "test_payment_key";
//        String orderNumber = "ORD-1234-5678";
//        Integer amount = 10000;
//
//        OrderDetails mockOrderDetails = mock(OrderDetails.class);
//        given(mockOrderDetails.totalPrice()).willReturn(amount);
//
//        Order mockOrder = mock(Order.class);
//        given(mockOrder.getOrderDetails()).willReturn(mockOrderDetails);
//
//        given(mockOrder.getOrderNumber()).willReturn(orderNumber);
//
//
//
//        Payment pendingPayment = spy(Payment.builder()
//                .orders(mockOrder)
//                .paymentStatus(PaymentStatus.PENDING)
//                .paymentRequestAt(LocalDateTime.now())
//                .build());
//
//        ReflectionTestUtils.setField(pendingPayment, "paymentId", 1L);
//
//
//        given(paymentRepository.findByOrder_OrderNumberAndPaymentStatus(orderNumber, PaymentStatus.PENDING))
//                .willReturn(Optional.of(pendingPayment));
//
//        TossPaymentResponseDto tossResponse = new TossPaymentResponseDto();
//
//        //여기서부터 -> 실제 토스 서버로 요청이 날아가 응답값을 받은것으로 가정.
//        ReflectionTestUtils.setField(tossResponse,"status","DONE");
//        ReflectionTestUtils.setField(tossResponse,"paymentKey",paymentKey);
//        ReflectionTestUtils.setField(tossResponse, "approvedAt", "2024-01-01T12:00:00+09:00");
//
//        TossPaymentResponseDto.Receipt mockReceipt = new TossPaymentResponseDto.Receipt();
//        ReflectionTestUtils.setField(mockReceipt, "url", "http://receipt.url");
//        ReflectionTestUtils.setField(tossResponse, "receipt", mockReceipt);
//
//        mockWebClientChain(tossResponse);
//
//        Payment result = paymentService.ConfirmPayment(paymentKey,orderNumber,amount);
//
//        assertNotNull(result);
//        assertEquals(paymentKey,result.getPaymentKey());
//        verify(pendingPayment).approvePayment(anyString(),anyString(),any(LocalDateTime.class));
//
//    }

//    @Test
//    @DisplayName("결제 승인 실패 case -> 결제 금액 불일치 시 ")
//    void confirmPaymentFailure(){
//        Long saleId = 1L;
//        Long amount = 10000L;
//        Long wrongAmount = 50000L;
//        Payment pendPayment = spy(Payment.builder()
//                .saleId(saleId)
//                .amount(amount)
//                .paymentStatus(PaymentStatus.PENDING)
//                .paymentRequestAt(LocalDateTime.now())
//                .build());
//
//        ReflectionTestUtils.setField(pendPayment,"paymentId",1L);
//
//        given(paymentRepository.findBySaleIdAndPaymentStatus(saleId,PaymentStatus.PENDING))
//                .willReturn(Optional.of(pendPayment));
//
//        IllegalStateException exception = assertThrows(IllegalStateException.class,
//                ()->paymentService.ConfirmPayment("key",saleId,wrongAmount));
//
//        assertEquals("Payment not found",exception.getMessage());
//        verify(pendPayment).cancelPayment();
//    }
//
//    @Test
//    @DisplayName("결제 승인 실패 -> 외부 api 연동 실패시")
//    void paymentFailureTest_API(){
//        Long saleId = 1L;
//        Long amount = 10000L;
//
//        Payment pendingPayment = spy(Payment.builder()
//                .saleId(saleId)
//                .amount(amount)
//                .paymentStatus(PaymentStatus.PENDING)
//                .paymentRequestAt(LocalDateTime.now())
//                .build()
//        );
//        given(paymentRepository.findBySaleIdAndPaymentStatus(saleId, PaymentStatus.PENDING))
//                .willReturn(Optional.of(pendingPayment));
//
//        given(webClient.post()).willReturn(requestBodyUriSpec);
//        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
//        given(requestBodySpec.header(any(), any())).willReturn(requestBodySpec);
//        given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
//        given(requestBodySpec.bodyValue(any(Object.class))).willReturn(requestHeadersSpec);
//        given(requestHeadersSpec.retrieve()).willThrow(new RuntimeException("API Error"));
//
//        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
//            paymentService.ConfirmPayment("key", saleId, amount);
//        });
//
//        assertEquals("API Error", exception.getMessage());
//        verify(pendingPayment).cancelPayment();
//    }

    private void mockWebClientChain(TossPaymentResponseDto responseDto) {
        given(webClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.header(any(), any())).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(any())).willReturn(requestBodySpec);
        given(requestBodySpec.bodyValue(any(Object.class))).willReturn(requestHeadersSpec);
        given(requestHeadersSpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.bodyToMono(TossPaymentResponseDto.class)).willReturn(Mono.just(responseDto));
    }

}
