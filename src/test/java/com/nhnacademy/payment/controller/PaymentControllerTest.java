package com.nhnacademy.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.reqeust.PaymentCancelRequestDto;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.entity.Payment;
import com.nhnacademy.payment.entity.PaymentProvider;
import com.nhnacademy.payment.entity.PaymentStatus;
import com.nhnacademy.payment.service.PaymentService;
import com.nhnacademy.payment.service.impl.PaymentFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private PaymentFlowService paymentFlowService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static PaymentUser mockUser;

    private PaymentRequestDto request;
    private Payment payment;
    private final String ORDER_NUMBER = "ORD-123";
    private final Long PAYMENT_ID = 1L;

    @TestConfiguration
    static class TestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return PaymentUser.class.isAssignableFrom(parameter.getParameterType());
                }

                @Override
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                    return mockUser;
                }
            });
        }
    }

    @BeforeEach
    void setUp() {
        mockUser = mock(PaymentUser.class);
        given(mockUser.memberId()).willReturn(1L);
        given(mockUser.isMember()).willReturn(true);
        given(mockUser.role()).willReturn("ROLE_MEMBER");

        request = new PaymentRequestDto("test_key", ORDER_NUMBER, 50000, "TOSS");

        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber", ORDER_NUMBER);

        // [중요] OrderDetails Mock 설정 및 totalPrice 반환값 설정
        // PaymentResponse.from()이 orderDetails.totalPrice()를 호출하기 때문입니다.
        OrderDetails orderDetails = mock(OrderDetails.class);
        given(orderDetails.totalPrice()).willReturn(50000);
        ReflectionTestUtils.setField(order, "orderDetails", orderDetails);

        payment = Payment.builder()
                .paymentKey("test_key")
                .paymentStatus(PaymentStatus.DONE)
                .order(order)
                .totalAmount(50000)
                .paymentRequestAt(LocalDateTime.now())
                .paymentApprovedAt(LocalDateTime.now())
                .provider(PaymentProvider.TOSS)
                .build();

        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
    }

    @Test
    @DisplayName("결제 승인 성공 테스트 POST - /payment/confirm")
    void confirmPaymentSuccess() throws Exception {
        // given
        given(paymentFlowService.confirmPayment(eq(mockUser), any(PaymentRequestDto.class)))
                .willReturn(payment);

        // when & then
        mockMvc.perform(post("/payment/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                // [수정 완료] orderId -> orderNumber
                .andExpect(jsonPath("$.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.totalAmount").value(50000));
    }

    @Test
    @DisplayName("결제 취소 요청 테스트 POST - /payment/cancel")
    void cancelPayment() throws Exception {
        // given
        PaymentCancelRequestDto cancelRequest = new PaymentCancelRequestDto(ORDER_NUMBER, "단순 변심", 30000);

        // when & then
        mockMvc.perform(post("/payment/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("결제 취소 요청이 정상적으로 처리되었습니다."));

        verify(paymentFlowService).cancelPaymentByMember(
                eq(ORDER_NUMBER),
                eq("단순 변심"),
                eq(30000),
                eq(mockUser)
        );
    }

    @Test
    @DisplayName("회원 결제 내역 단건 조회 GET - /payment/{orderNumber}")
    void getPaymentByOrderNumber() throws Exception {
        // given
        given(paymentService.getMemberPaymentByOrderNumber(eq(mockUser), eq(ORDER_NUMBER)))
                .willReturn(payment);

        // when & then
        mockMvc.perform(get("/payment/{orderNumber}", ORDER_NUMBER))
                .andDo(print())
                .andExpect(status().isOk())
                // [수정 완료] orderId -> orderNumber
                .andExpect(jsonPath("$.orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    @DisplayName("회원 결제 전체 조회 (페이징) GET - /payment")
    void getMemberPayments_Success() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Payment> paymentPage = new PageImpl<>(List.of(payment), pageable, 1);

        given(paymentService.getAllMemberPayments(eq(1L), any(Pageable.class)))
                .willReturn(paymentPage);

        // when & then
        mockMvc.perform(get("/payment")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                // [수정 완료] orderId -> orderNumber
                .andExpect(jsonPath("$.content[0].orderNumber").value(ORDER_NUMBER))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("회원 결제 전체 조회 실패 - 비회원 접근 시")
    void getMemberPayments_Fail_Guest() throws Exception {
        // given
        given(mockUser.isMember()).willReturn(false);

        // when & then
        mockMvc.perform(get("/payment")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof IllegalArgumentException))
                .andExpect(result -> assertTrue(result.getResolvedException().getMessage().contains("비회원은 결제 내역 목록을 조회할 수 없습니다.")));
    }
}