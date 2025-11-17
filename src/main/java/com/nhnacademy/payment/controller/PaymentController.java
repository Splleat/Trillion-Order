package com.nhnacademy.payment.controller;

import com.nhnacademy.payment.domain.Payment;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/ready")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequestDto request) {
        Payment savedPayment = paymentService.createPendingPayment(request);
        return ResponseEntity.ok().body(savedPayment);
    }

    @GetMapping("/success")
    public ResponseEntity<?> createPaymentSuccess(@RequestParam String paymentKey,
                                                  @RequestParam("orderId") Long saleId,
                                                  @RequestParam Long amount) {
        try{
            Payment confirmPayment = paymentService.ConfirmPayment(paymentKey, saleId, amount);
            return ResponseEntity.ok().body("결제 성공 : "+ confirmPayment.getPaymentId());
        }catch(Exception ex){
            return ResponseEntity.badRequest().body("결제 실패 " + ex.getMessage());
        }
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPaymentById(@PathVariable("paymentId") Long paymentId) {
        return ResponseEntity.ok().body(paymentService.getPaymentById(paymentId));
    }
 }
