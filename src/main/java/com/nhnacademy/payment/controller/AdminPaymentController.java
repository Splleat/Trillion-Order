/*
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 * + Copyright 2025. NHN Academy Corp. All rights reserved.
 * + * While every precaution has been taken in the preparation of this resource,  assumes no
 * + responsibility for errors or omissions, or for damages resulting from the use of the information
 * + contained herein
 * + No part of this resource may be reproduced, stored in a retrieval system, or transmitted, in any
 * + form or by any means, electronic, mechanical, photocopying, recording, or otherwise, without the
 * + prior written permission.
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */

package com.nhnacademy.payment.controller;

import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.reqeust.PaymentCancelRequestDto;
import com.nhnacademy.payment.dto.response.AdminPaymentResponse;
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
@RequestMapping("/admin/payments")
public class AdminPaymentController {
    private final PaymentService paymentService;
    private final PaymentFlowService paymentFlowService;

    //관리자 결제내역 전체 조회
    @GetMapping
    public ResponseEntity<Page<AdminPaymentResponse>> getPayments(PaymentUser user, Pageable pageable) {
        validateAdminRole(user);

        Page<Payment> payments = paymentService.getAllPayments(pageable);
        Page<AdminPaymentResponse> responses = payments.map(AdminPaymentResponse::from);
        return ResponseEntity.ok(responses);
    }

    //관리자 결제 내역 단건 조회
    @GetMapping({"/{paymentId}"})
    public ResponseEntity<?> getPayment(PaymentUser user, @PathVariable Long paymentId) {

        validateAdminRole(user);
        Payment payment = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(AdminPaymentResponse.from(payment));
    }

    //관리자 결제 취소.
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelPayment(PaymentUser user,  @RequestBody PaymentCancelRequestDto request) {
        paymentFlowService.cancelPaymentByMember(
                request.orderNumber(),
                request.cancelReason(),
                request.cancelAmount(),
                user
        );

        return ResponseEntity.noContent().build();
    }

    private void validateAdminRole(PaymentUser user) {
        if (!"ROLE_ADMIN".equals(user.role())) {
            throw new IllegalArgumentException("관리자 권한이 필요합니다.");
        }
    }
}
