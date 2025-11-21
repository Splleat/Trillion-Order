package com.nhnacademy.payment.controller;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.domain.PaymentStatus;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;


    @GetMapping("/success")
    public ResponseEntity<?> createPaymentSuccess(@RequestParam("paymentKey") String paymentKey,
                                                  @RequestParam("orderId") String orderId, // Toss는 'orderId'로 보냄
                                                  @RequestParam("amount") Integer amount) {


            PaymentRequestDto requestDto = new PaymentRequestDto(paymentKey, orderId, amount);

            Payment payment = paymentService.ConfirmPayment(requestDto);

            return ResponseEntity.ok(payment);

    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPaymentById(@PathVariable("paymentId") Long paymentId) {
        return ResponseEntity.ok().body(paymentService.getPaymentById(paymentId));
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<?> cancelPayment(@PathVariable("paymentId") Long paymentId,
                                           @RequestBody Map<String, String> requestBody) {
        String cancelReason = requestBody.getOrDefault("cancelReason","단순 변심");

        paymentService.cancelPayment(paymentId, cancelReason);

        return ResponseEntity.ok("결제 취소 완료");
    }

 }
