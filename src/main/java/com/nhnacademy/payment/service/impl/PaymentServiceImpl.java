package com.nhnacademy.payment.service.impl;


import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import com.nhnacademy.payment.exception.PaymentAlreadyApprovedException;
import com.nhnacademy.payment.exception.PaymentNotFoundException;
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
import java.util.function.LongToIntFunction;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final WebClient webClient;


    @Value("${toss.secret-key}")
    private String tossSecretKey;

    //결제 대기 상태 생성 -> 결제 처리전의 주문상태-> 결제하기는 눌렀는데 결제 방법 및 다른 결제사의 승인을 받기 전
    //todo -> 이 메서드는 그냥 여기 서비스에서만 사용할거 같은데 그냥 다른 private메서드로 빼내줘야 할듯
    //todo -> 그리고 내가 볼때는 진짜 pending상태가 필요 없음 -> 여기 필드에는 그냥 Completed Canceld만


    //결제 승인
    @Override
    @Transactional
    public Payment ConfirmPayment(PaymentRequestDto request) {

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

                Payment payment = Payment.builder()
                        .paymentKey(response.getPaymentKey())
                        .paymentStatus(PaymentStatus.COMPLETED)
                        .paymentRequestAt(LocalDateTime.now()) // 혹은 response.requestedAt
                        .paymentApprovedAt(approvedAt)
                        .paymentReceipt(response.getReceipt().getUrl())
                        .order(order)
                        .build();
                try{
                    Payment savedPayment =  paymentRepository.save(payment);

                    //todo 주문의 결제 상태값 변환하기
                    return savedPayment;
                }catch(Exception e) {
                    log.error("결제 중 오류 발생, orderId :{}, paymentKey : {}",request.orderNumber(),request.paymentKey());
                    //todo 결제 취소 호출
                    throw new RuntimeException("결제는 승인도중 오류가 발새앟여 롤백");
                }
            }else{
                throw new RuntimeException("결제 실패 알수 없는 오류");
            }

    }

    //결제 조회
    @Override
    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findByPaymentId(paymentId);
    }


    //결제 취소
    @Override
    @Transactional
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
                //todo 여기도 마찬가지

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
