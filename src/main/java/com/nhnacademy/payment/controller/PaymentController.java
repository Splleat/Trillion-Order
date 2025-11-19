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

    @PostMapping("/{orderId}")
    public ResponseEntity<?> createPayment(@PathVariable Long orderId) {
        Payment savedPayment = paymentService.createPendingPayment(orderId);
        return ResponseEntity.ok().body(savedPayment);
    }

    @GetMapping("/success")
    public ResponseEntity<?> createPaymentSuccess(@RequestParam String paymentKey,
                                                  @RequestParam("orderId") String saleId) {
        try{
            Payment confirmPayment = paymentService.ConfirmPayment(paymentKey, saleId);
            return ResponseEntity.ok().body("결제 성공 : "+ confirmPayment.getPaymentId());
        }catch(Exception ex){
            return ResponseEntity.badRequest().body("결제 실패 " + ex.getMessage());
        }
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
