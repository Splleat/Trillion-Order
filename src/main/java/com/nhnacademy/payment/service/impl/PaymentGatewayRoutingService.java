package com.nhnacademy.payment.service.impl;

import com.nhnacademy.payment.config.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentGatewayRoutingService {
    private final List<PaymentGateway> gateways;

    //PaymentGateWay 중 supports가 True인 값을 반환 ex. TOSS 가 넘어 다면 TossPaymentClient를 찾아서 리턴
    public PaymentGateway getGateway(String providerName){
        return gateways.stream()
                .filter(gateway -> gateway.supports(providerName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 결제 수단."));
    }

}
