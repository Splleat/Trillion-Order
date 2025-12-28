package com.nhnacademy.payment.config;

import com.nhnacademy.payment.dto.response.PaymentApiResponse;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime; // [필수] 날짜 생성을 위해 추가
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class TossPaymentClient implements PaymentGateway {
    private final WebClient tossWebClient;

    public TossPaymentClient(WebClient paymentWebClient) {
        this.tossWebClient = paymentWebClient.mutate()
                .baseUrl("https://api.tosspayments.com/v1")
                .build();
    }

    @Value("${toss.secret-key}")
    private String tossSecretKey;


    @Override
    public boolean supports(String providerName) {
        return "TOSS".equalsIgnoreCase(providerName);
    }

    @Override
    public PaymentApiResponse confirm(String paymentKey, String orderNumber, Integer amount) {
        // [수정] 0원 결제 확인 로직
        // 금액이 0원이거나 키가 "ZERO_"로 시작하는 경우 (프론트에서 보낸 유니크 키)
        if (amount == 0 || (paymentKey != null && paymentKey.startsWith("ZERO_"))) {
            return PaymentApiResponse.builder()
                    .paymentKey(paymentKey) // 프론트에서 생성한 유니크 키 사용 (DB 중복 방지)
                    .orderId(orderNumber)
                    .totalAmount(0)
                    .status("DONE") // 결제 완료 상태
                    .requestedAt(OffsetDateTime.now().toString())
                    .approvedAt(OffsetDateTime.now().toString())
                    .receiptUrl("0원 결제") // [중요] DB Not Null 에러 방지용 문자열
                    .provider("TOSS")
                    .build();
        }

        // 일반 결제 로직 (토스 호출)
        return tossWebClient.post()
                .uri("/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, getBasicAuthHeader())
                .bodyValue(Map.of(
                        "paymentKey", paymentKey,
                        "orderId", orderNumber,
                        "amount", amount
                ))
                .retrieve()
                .onStatus(status ->
                        status.isError(), response -> response.bodyToMono(String.class)
                        .flatMap(error -> Mono.error(new RuntimeException("TossAPI 오류 " + error)))
                ).bodyToMono(TossPaymentResponseDto.class)
                .map(toss -> PaymentApiResponse.builder()
                        .paymentKey(toss.getPaymentKey())
                        .orderId(toss.getOrderId())
                        .totalAmount(toss.getTotalAmount())
                        .status(toss.getStatus())
                        .requestedAt(toss.getRequestedAt())
                        .approvedAt(toss.getApprovedAt())
                        .receiptUrl(toss.getReceipt().getUrl())
                        .provider("TOSS")
                        .build())
                .block();
    }

    @Override
    public PaymentApiResponse cancel(String paymentKey, String cancelReason, Integer cancelAmount) {
        // [수정] 0원 결제 취소 로직 (PG사 호출 생략)
        if (paymentKey != null && paymentKey.startsWith("ZERO_")) {
            return PaymentApiResponse.builder()
                    .paymentKey(paymentKey)
                    .status("CANCELED")
                    .totalAmount(0)
                    .provider("TOSS")
                    .build();
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("cancelReason", cancelReason);

        if (cancelAmount != null) {
            requestBody.put("cancelAmount", cancelAmount);
        }

        return tossWebClient.post()
                .uri("/payments/" + paymentKey + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, getBasicAuthHeader())
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> Mono.error(new RuntimeException("Toss 결제 취소 오류 " + error)))
                )
                .bodyToMono(TossPaymentResponseDto.class)
                .map(toss -> {
                    int actualCancelAmount = 0;
                    if (toss.getCancels() != null && !toss.getCancels().isEmpty()) {
                        actualCancelAmount = toss.getCancels()
                                .get(toss.getCancels().size() - 1)
                                .getCancelAmount();
                    }

                    return PaymentApiResponse.builder()
                            .paymentKey(toss.getPaymentKey())
                            .orderId(toss.getOrderId())
                            .status(toss.getStatus())
                            .totalAmount(actualCancelAmount)
                            .receiptUrl(toss.getReceipt().getUrl())
                            .provider("TOSS")
                            .approvedAt(toss.getApprovedAt())
                            .build();

                })
                .block();
    }

    private String getBasicAuthHeader() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));
    }
}