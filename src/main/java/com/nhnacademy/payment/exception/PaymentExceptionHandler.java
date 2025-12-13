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

package com.nhnacademy.payment.exception;

import com.nhnacademy.payment.dto.response.PaymentErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@Slf4j
public class PaymentExceptionHandler {

    //view에서 넘어온 가격과 서버에서 넘어온 가격이 다를 경우.
    @ExceptionHandler({BalanceShortageException.class, PaymentAmountMissMatchException.class})
    public ResponseEntity<PaymentErrorResponse> handlePaymentSaveException(Exception e) {
        log.warn("유효하지 않은 요청입니다 : {} ",e.getMessage());
        PaymentErrorResponse response = PaymentErrorResponse.of(
                "Balance",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    //결제 상태에 관한 예외 처리
    @ExceptionHandler({PaymentAlreadyApprovedException.class,
    PaymentAlreadyCanceledException.class, PaymentNotApprovedException.class})
    public ResponseEntity<PaymentErrorResponse> handlePaymentStateConflictException(Exception e) {
        log.info("결제 상태 충돌 : {}",e.getMessage());
        PaymentErrorResponse response = PaymentErrorResponse.of(
                "PAYMENT_STATE_CONFLICT",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<PaymentErrorResponse> handlePaymentNotFoundException(PaymentNotFoundException e){
        log.info("결제 정보를 찾을 수 없음 : {} ", e.getMessage());
        PaymentErrorResponse response = PaymentErrorResponse.of(
                "PAYMENT_NOT_FOUND",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(PaymentSaveFailException.class)
    public ResponseEntity<PaymentErrorResponse> handlePaymentSaveFailException(PaymentSaveFailException e) {
        log.error("결제 승인은 완료 db 저장에 실패 롤백 주문 건 : {} ", e.getOrderNumber());
        PaymentErrorResponse response = PaymentErrorResponse.of(
                "PAYMENT_SAVE_FAILED",
                e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}
