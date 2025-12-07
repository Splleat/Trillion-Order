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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.payment.dto.reqeust.PaymentCancelRequestDto;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentResponse;
import com.nhnacademy.payment.service.PaymentService;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "toss.secret-key=test-key")
@AutoConfigureMockMvc
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private PaymentFlowService paymentFlowService;

    private PaymentRequestDto request;
    private PaymentResponse response;
    private final String ORDER_NUMBER = "ORD-123";
    private final Long PAYMENT_ID = 1L;

    @BeforeEach
    void setUp() {
        request = new PaymentRequestDto("test_key",ORDER_NUMBER,50000);

        response = PaymentResponse.builder()
                .paymentId(PAYMENT_ID)
                .orderNumber(ORDER_NUMBER)
                .totalAmount(50000)
                .status("DONE")
                .build();

    }

    @Test
    @DisplayName("결제 승인 성공 테스트 POST - /payments/success")
    void createConfirmPaymentSuccess() throws Exception {
        given(paymentFlowService.confirmPayment(any(PaymentRequestDto.class)))
                .willReturn(response);

        mockMvc.perform(post("/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.totalAmount").value(50000));
    }

    @Test
    @DisplayName("결제 단건 조회 테스트 GET - /payments/{paymentId}")
    void getPaymentById()throws Exception {

        given(paymentService.getPaymentById(PAYMENT_ID))
                .willReturn(response);

        mockMvc.perform(get("/payments/{paymentId}", PAYMENT_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID));
    }

    @Test
    @DisplayName("결제 정상 취소 테스트 POST - /payments/cancel")
    void cancelPayment() throws Exception {
        PaymentCancelRequestDto cancelRequest = new PaymentCancelRequestDto(ORDER_NUMBER, "단순 변심", 30000);

        // when & then
        mockMvc.perform(post("/payments/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                // Controller의 실제 반환 문자열과 일치시켜야 함
                .andExpect(content().string("결제 취소 요청이 정상적으로 처리되었습니다."));

        // verify: 서비스가 올바른 파라미터로 호출되었는지 검증
        verify(paymentFlowService).cancelPayment(
                eq(ORDER_NUMBER),
                eq("단순 변심"),
                eq(30000)
        );
    }

    @Test
    @DisplayName("결제 전체 취소 테스트 (금액 null) POST - /payments/cancel")
    void cancelPaymentFull() throws Exception {
        // given
        // 금액이 null이면 전체 취소 로직
        PaymentCancelRequestDto cancelRequest = new PaymentCancelRequestDto(ORDER_NUMBER, "전체 취소", null);

        // when & then
        mockMvc.perform(post("/payments/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("결제 취소 요청이 정상적으로 처리되었습니다."));

        // verify: 금액이 null로 잘 넘어가는지 확인
        verify(paymentFlowService).cancelPayment(
                eq(ORDER_NUMBER),
                eq("전체 취소"),
                eq(null)
        );
    }

    @Test
    @DisplayName("결제 전체 조회 (페이징) GET - /payments")
    void getPayments() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentResponse> paymentPage = new PageImpl<>(List.of(response), pageable, 1);

        given(paymentService.getAllPayments(any(Pageable.class))).willReturn(paymentPage);

        // when & then
        mockMvc.perform(get("/payments")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                // Page 응답 구조 검증 (content 배열 안에 데이터가 있음)
                .andExpect(jsonPath("$.content[0].orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.content[0].totalAmount").value(50000))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

}