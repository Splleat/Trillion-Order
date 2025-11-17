package com.nhnacademy.payment.service.impl;

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
    private final WebClient webClient;


    @Value("${toss.secret-key}")
    private String tossSecretKey;

    @Override
    public Payment createPendingPayment(PaymentRequestDto request) {
        Payment payment = Payment.builder()
                .saleId(request.saleId())
                .amount(request.amount())
                .paymentStatus(PaymentStatus.PENDING)
                .paymentRequestAt(LocalDateTime.now())
                .build();

        return paymentRepository.save(payment);
    }

    @Override
    public Payment ConfirmPayment(String paymentKey, Long saleId, Long amount) {
        Payment payment = paymentRepository.findBySaleIdAndPaymentStatus(saleId,PaymentStatus.PENDING)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if(!payment.getAmount().equals(amount)) {
            payment.cancelPayment();
            throw new IllegalStateException("Payment not found");
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
                            "orderId",String.valueOf(saleId),
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

    @Override
    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findByPaymentId(paymentId);
    }
}
