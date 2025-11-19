package com.nhnacademy.payment.service.impl;


import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
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
@Transactional
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final WebClient webClient;


    @Value("${toss.secret-key}")
    private String tossSecretKey;

    //결제 대기 상태 생성 -> 결제 처리전의 주문상태-> 결제하기는 눌렀는데 결제 방법 및 다른 결제사의 승인을 받기 전
    @Override
    public Payment createPendingPayment(PaymentRequestDto request) {
        //주문 가져오기 by 식별자로
        Order findOrders = orderRepository.findById(request.orderId()).orElseThrow(
                () -> new OrderNotFoundException("Order not found with id: " + request.orderId())
        );
        //todo : 타입 고쳐야 할듯 Long으로
        //주문의 총액을 가져오게 됨. -> 주문 db의 값을 넘겨 받음
        Integer amount = findOrders.getOrderDetails().totalPrice();

        //클라이언트로부터 받은 금액 view로부터 받은 금액과 db에 저장된 값이 일치하는지 체크가 필요한가?
        if(!request.amount().equals(amount)) {
            throw new IllegalArgumentException("Amount must be equal to request amount");
        }

        //결제 대기 상태 생성 null인 필드들은 추후에 결제 승인하면 toss-api에서 받아온 값들이 채워줌.
        Payment payment = Payment.builder()
                .orders(findOrders)
                .amount(amount)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentRequestAt(LocalDateTime.now())
                .build();

        return paymentRepository.save(payment);
    }


    //결제 승인
    @Override
    public Payment ConfirmPayment(String paymentKey, String orderNumber, Integer amount) {
        Payment payment = paymentRepository.findByOrder_OrderNumberAndPaymentStatus(orderNumber,PaymentStatus.PENDING)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getAmount().longValue() != amount) {
            payment.cancelPayment(); // [수정] cancel -> fail
            throw new IllegalStateException("결제 금액이 일치하지 않습니다.");
        }
        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        try{
            TossPaymentResponseDto response = webClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, "Basic "+encodedSecretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "paymentKey",paymentKey,
                            "orderId",orderNumber,
                            "amount",amount
                    ))
                    .retrieve()
                    .bodyToMono(TossPaymentResponseDto.class)
                    .block(); // 동기 ,, -> 나중에 비동기로 전환 예정 도움!

            if (response != null && "DONE".equals(response.getStatus())) {
                LocalDateTime approvedAt = LocalDateTime.parse(response.getApprovedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

                payment.approvePayment(
                        response.getPaymentKey(),
                        response.getReceipt().getUrl(),
                        approvedAt
                );
                return payment;
            } else {
                throw new RuntimeException("결제 승인 실패: 상태가 DONE이 아닙니다.");
            }

        }catch(Exception e){
            log.error("결제 승인 실패 : {}",e.getMessage());
            payment.cancelPayment();
            throw new RuntimeException(e.getMessage());
        }
    }

    //결제 조회
    @Override
    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findByPaymentId(paymentId);
    }


    //결제 취소
    @Override
    public void cancelPayment(Long paymentId, String cancelReason) {
        Payment payment = paymentRepository.findByPaymentId(paymentId);
        if(payment == null){
            throw new IllegalArgumentException("Payment not found");
        }

        String paymentKey = payment.getPaymentKey();

        String encodedSecretKey = Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));

        try{
            TossPaymentResponseDto response = webClient.post()
                    .uri("https://api.tosspayments.com/v1/payments/" + paymentKey + "/cancel")
                    .header(HttpHeaders.AUTHORIZATION, "Basic "+ encodedSecretKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("cancelReason", cancelReason))
                    .retrieve()
                    .bodyToMono(TossPaymentResponseDto.class)
                    .block();

            if(response != null && ("CANCELED".equals(response.getStatus()))) {
                payment.cancelPayment();
                log.info("결제 취소 완료 : paymentId={}, reason={}", paymentId, cancelReason);
            }else{
                throw new RuntimeException("토스 api 취소 실패");
            }
        }catch(Exception e){
            log.error("결제 취소 중 오류 : {}",e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
