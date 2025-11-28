package com.nhnacademy.payment.service.impl;


import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.response.PaymentResponse;
import com.nhnacademy.payment.dto.response.TossPaymentResponseDto;
import com.nhnacademy.payment.exception.PaymentNotFoundException;
import com.nhnacademy.payment.repository.PaymentRepository;
import com.nhnacademy.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;

    //DB에 담는 시점에서만 Transactional 호출하면 됨.
    @Override
    @Transactional
    public PaymentResponse savePayment(TossPaymentResponseDto response, Order order) {

        Payment savePayment = Payment.builder()
                .paymentKey(response.getPaymentKey())
                .paymentStatus(PaymentStatus.DONE)
                .paymentRequestAt(LocalDateTime.parse(response.getRequestedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .paymentApprovedAt(LocalDateTime.parse(response.getApprovedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .paymentReceipt(response.getReceipt().getUrl())
                .order(order)
                .build();

        paymentRepository.save(savePayment);

        order.setOrderStatus(OrderStatus.COMPLETED);
        return PaymentResponse.from(savePayment);
    }

    @Override
    @Transactional
    public void updatePaymentCanceledStatus(Payment payment) {
        payment.cancelPayment();
        payment.getOrder().setOrderStatus(OrderStatus.CANCELED);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentByOrderNumber(String orderNumber) {
        Payment payment =  paymentRepository.findByOrder_OrderNumber(orderNumber);
        if(payment==null){
            throw new PaymentNotFoundException("결제 정보가 존재하지 않습니다.");
        }
        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(
                () -> new PaymentNotFoundException("결제 정보가 존재하지 않습니다.")
        );

        return PaymentResponse.from(payment);
    }
}
