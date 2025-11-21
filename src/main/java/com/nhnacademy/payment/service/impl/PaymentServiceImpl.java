package com.nhnacademy.payment.service.impl;


import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentResponse;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import com.nhnacademy.payment.repository.PaymentRepository;
import com.nhnacademy.payment.service.PaymentService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final WebClient webClient;


    @Value("${toss.secret-key}")
    private String tossSecretKey;

    //결제 승인
    @Override
    @Transactional
    public PaymentResponse ConfirmPayment(PaymentRequestDto request) {

        Order order = orderRepository.findByOrderNumber(request.orderNumber())
                .orElseThrow(() -> new OrderNotFoundException(request.orderNumber()));


        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        TossPaymentResponseDto response;

        try {
             response = webClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedSecretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "paymentKey", request.paymentKey(),
                            "orderId", request.orderNumber(),
                            "amount", request.amount()
                    ))
                    .retrieve()
                    .bodyToMono(TossPaymentResponseDto.class)
                    .block();
        }catch(Exception e) {
            log.error("TossApi 연동 실페 : {} ", e.getMessage());
            throw new RuntimeException("결제 승인 중 오류 발생");
        }
            if (response != null && "DONE".equals(response.getStatus())) {

                LocalDateTime approvedAt = LocalDateTime.parse(response.getApprovedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                LocalDateTime requestedAt = LocalDateTime.parse(response.getRequestedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                Payment payment = Payment.builder()
                        .paymentKey(response.getPaymentKey())
                        .paymentStatus(PaymentStatus.DONE)
                        .paymentRequestAt(requestedAt) // 혹은 response.requestedAt
                        .paymentApprovedAt(approvedAt)
                        .paymentReceipt(response.getReceipt().getUrl())
                        .order(order)
                        .build();
                try{
                    paymentRepository.save(payment);
                    //todo 주문의 결제 상태값 변환하기
                    payment.getOrder().setPaymentStatus(com.nhnacademy.order.order.domain.PaymentStatus.COMPLETED);
                    return PaymentResponse.builder()
                            .paymentId(payment.getPaymentId())
                            .orderNumber(payment.getOrder().getOrderNumber())
                            .status(payment.getPaymentStatus().toString())
                            .requestedAt(payment.getPaymentRequestAt())
                            .approvedAt(payment.getPaymentApprovedAt())
                            .receiptUrl(payment.getPaymentReceipt())
                            .build();

                }catch(Exception e) {
                    log.error("결제 중 오류 발생, orderId :{}, paymentKey : {}",request.orderNumber(),request.paymentKey());
                    //todo 결제 취소 호출
                    cancelPayment(request.orderNumber(), request.paymentKey());
                    throw new RuntimeException("결제는 승인도중 오류가 발새앟여 롤백");
                }
            }else{
                throw new RuntimeException("결제 실패 알수 없는 오류");
            }

    }

    //결제 조회
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("결제 정보 없음"));


        return PaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderNumber(payment.getOrder().getOrderNumber())
                .status(payment.getPaymentStatus().toString())
                .totalAmount(payment.getOrder().getOrderDetails().totalPrice())
                .status(payment.getPaymentStatus().toString())
                .requestedAt(payment.getPaymentRequestAt())
                .approvedAt(payment.getPaymentApprovedAt())
                .receiptUrl(payment.getPaymentReceipt())
                .build();
    }


    //결제 취소
    @Override
    @Transactional
    public void cancelPayment(String orderNumber,String cancelReason    ) {
        Payment payment = paymentRepository.findByOrder_OrderNumber(orderNumber);

        Order findOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow(
                () -> new OrderNotFoundException(orderNumber)
        );

        //해당 주문에 대한 결제 키를 가져옴
        String paymentKey = payment.getPaymentKey();

        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        try{
            TossPaymentResponseDto response = webClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/" + paymentKey + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Basic "+ encodedSecretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("cancelReason",cancelReason))
                    .retrieve()
                    .bodyToMono(TossPaymentResponseDto.class)
                    .block();

            if(response != null && ("CANCELED".equals(response.getStatus()))) {
                payment.cancelPayment();
                findOrder.setPaymentStatus(com.nhnacademy.order.order.domain.PaymentStatus.CANCELED);

                log.info("결제 취소 완료 : paymentId={}, reason={}", orderNumber, cancelReason);
            }else{
                throw new RuntimeException("토스 api 취소 실패");
            }
        }catch(Exception e){
            log.error("결제 취소 중 오류 : {}",e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
