package com.nhnacademy.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.reqeust.PaymentCancelRequestDto;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPaymentController.class)
class AdminPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private PaymentFlowService paymentFlowService;

    // JPA Auditing 오류 방지용 Mock
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // 관리자 권한을 가진 Mock User
    private static PaymentUser mockAdminUser;

    private Payment payment;
    private final String ORDER_NUMBER = "ORD-123";
    private final Long PAYMENT_ID = 1L;

    // Mock User를 컨트롤러에 주입하기 위한 설정
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
                    return mockAdminUser;
                }
            });
        }
    }

    @BeforeEach
    void setUp() {
        // 관리자 권한 설정
        mockAdminUser = mock(PaymentUser.class);
        given(mockAdminUser.role()).willReturn("ROLE_ADMIN");

        Order order = new Order();
        ReflectionTestUtils.setField(order, "orderNumber", ORDER_NUMBER);

        // OrderDetails Mocking (NullPointerException 및 생성자 문제 방지)
        OrderDetails orderDetails = mock(OrderDetails.class);
        given(orderDetails.totalPrice()).willReturn(50000);
        ReflectionTestUtils.setField(order, "orderDetails", orderDetails);

        // Payment Entity 생성
        payment = Payment.builder()
                .paymentKey("test_key")
                .paymentStatus(PaymentStatus.DONE)
                .order(order)
                .totalAmount(50000)
                .paymentRequestAt(LocalDateTime.now())
                .paymentApprovedAt(LocalDateTime.now())
                .provider(PaymentProvider.TOSS) // Provider 설정 필요
                .build();

        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
    }

    @Test
    @DisplayName("관리자 결제 전체 조회 (페이징) GET - /admin/payments")
    void getPayments() throws Exception {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Payment> paymentPage = new PageImpl<>(List.of(payment), pageable, 1);

        given(paymentService.getAllPayments(any(Pageable.class))).willReturn(paymentPage);

        // when & then
        mockMvc.perform(get("/admin/payments")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.content[0].paymentKey").value("test_key"))
                .andExpect(jsonPath("$.content[0].orderNumber").value(ORDER_NUMBER));
    }

    @Test
    @DisplayName("관리자 결제 단건 조회 (ID) GET - /admin/payments/{paymentId}")
    void getPaymentById() throws Exception {
        // given
        given(paymentService.getPaymentById(PAYMENT_ID)).willReturn(payment);

        // when & then
        mockMvc.perform(get("/admin/payments/{paymentId}", PAYMENT_ID))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(PAYMENT_ID))
                .andExpect(jsonPath("$.orderNumber").value(ORDER_NUMBER));
    }

    @Test
    @DisplayName("관리자 결제 취소 POST - /admin/payments/cancel")
    void cancelPayment() throws Exception {
        // given
        PaymentCancelRequestDto cancelRequest = new PaymentCancelRequestDto(ORDER_NUMBER, "관리자 직권 취소", 50000);

        // when & then
        mockMvc.perform(post("/admin/payments/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andDo(print())
                .andExpect(status().isNoContent()); // 204 No Content

        // verify: 관리자 User가 서비스 메서드에 전달되었는지 확인
        verify(paymentFlowService).cancelPaymentByMember(
                eq(ORDER_NUMBER),
                eq("관리자 직권 취소"),
                eq(50000),
                eq(mockAdminUser)
        );
    }
}