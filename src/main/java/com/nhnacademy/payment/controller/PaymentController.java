package com.nhnacademy.payment.controller;

import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.reqeust.PaymentCancelRequestDto;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentResponse;
import com.nhnacademy.payment.entity.Payment;
import com.nhnacademy.payment.service.PaymentService;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payment")
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentFlowService paymentFlowService;


    // 사용자 결제 승인(회원)
    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPayment(PaymentUser user, @RequestBody PaymentRequestDto request) {

        Payment payment = paymentFlowService.confirmPayment(user,request);

        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    // 사용자 결제 전체 조회(회원)
    @GetMapping
    public ResponseEntity<?> getMemberPayments(PaymentUser user,
                                              Pageable pageable){
        if (!user.isMember()) {
            // 필요시 403 Forbidden 등으로 처리 가능
            throw new IllegalArgumentException("비회원은 결제 내역 목록을 조회할 수 없습니다.");
        }

        // 회원이면 memberId를 꺼내서 조회
        Page<Payment> payments = paymentService.getAllMemberPayments(user.memberId(), pageable);
        Page<PaymentResponse> responses = payments.map(PaymentResponse::from);

        return ResponseEntity.ok(responses);

    }


    //사용자 결제 취소/부분취소(회원)
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelPayment(PaymentUser user,
            @RequestBody PaymentCancelRequestDto request) {

        paymentFlowService.cancelPaymentByMember(
                request.orderNumber(),
                request.cancelReason(),
                request.cancelAmount(),
                user// null이면 서비스가 알아서 전액 취소로 처리함
        );

        return ResponseEntity.ok("결제 취소 요청이 정상적으로 처리되었습니다.");
    }

    //사용자 결제 내역 단건 조회(회원) by orderNumber
    @GetMapping("/{orderNumber}")
    public ResponseEntity<?> getPayment(PaymentUser user,
                                            @PathVariable String orderNumber){
        Payment payment = paymentService.getMemberPaymentByOrderNumber(user,orderNumber);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

 }
