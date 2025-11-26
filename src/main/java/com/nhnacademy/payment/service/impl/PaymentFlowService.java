package com.nhnacademy.payment.service.impl;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.config.TossPaymentClient;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentResponse;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import com.nhnacademy.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFlowService {
    private final PaymentService paymentService;
    private final TossPaymentClient tossPaymentClient;
    private final OrderRepository orderRepository;

    public PaymentResponse ConfirmPayment(PaymentRequestDto request) {
        Order order = orderRepository.findOrderWithItemsByOrderNumber(request.orderNumber())
                .orElseThrow(() -> new OrderNotFoundException(request.orderNumber()));

        TossPaymentResponseDto response;
        try{
            response = tossPaymentClient.confirm(
                    request.paymentKey(),
                    request.orderNumber(),
                    request.amount()
            );
        }catch (Exception e){
            log.error("결제 승인 API 호출 도중 오류 발생 : {}" , e.getMessage());
            throw e;
        }
        try{
            return paymentService.savePayment(response, order);
        }catch (Exception e){
            log.error("결제 정보 저장 중 오류 발생 :{} ", request.orderNumber());
            tossPaymentClient.cancel(request.paymentKey(), request.orderNumber());
            throw new RuntimeException("결제 승인 실패");
        }
    }


    //결제 취소
    public void cancelPayment(String orderNumber,String cancelReason) {
        Payment payment = paymentService.getPaymentByOrderNumber(orderNumber);
        if (payment == null) throw new RuntimeException("취소할 결제 정보가 없습니다.");

        TossPaymentResponseDto response = tossPaymentClient.cancel(payment.getPaymentKey(), cancelReason);

        // 3. DB 상태 업데이트
        if ("CANCELED".equals(response.getStatus())) {
            paymentService.updatePaymentCanceledStatus(payment);
        }
    }
}
