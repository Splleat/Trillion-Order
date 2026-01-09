package com.nhnacademy.order.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.order.order.domain.OrdererInfo;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.domain.ReceiverInfo;
import com.nhnacademy.order.order.dto.NonMemberOrderGetRequest;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.service.NonMemberOrderService;
import com.nhnacademy.order.order.service.OrderService;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.order.dto.NonMemberOrderCancelRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OrderController.class, properties = "toss.secret-key=test-key")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private NonMemberOrderService nonMemberOrderService;

    @MockitoBean
    private com.nhnacademy.order.order.service.EmailService emailService;

    @MockitoBean
    private org.springframework.mail.javamail.JavaMailSender javaMailSender;

    private OrderResponse orderResponse;
    private Page<OrderResponse> orderResponsePage;

    @BeforeEach
    void setUp() {
        OrdererInfo ordererInfo = new OrdererInfo("홍길동", "010-1234-5678", "test@email.com");
        ReceiverInfo receiverInfo = new ReceiverInfo("이순신", "010-9876-5432", "서울시 강남구");

        orderResponse = new OrderResponse(
                1L,
                1L,
                "ORD-20251202-12345",
                LocalDateTime.now(),
                OrderStatus.PENDING,
                50000,
                48500,
                2500,
                0,
                0, // totalCouponDiscount
                ordererInfo,
                receiverInfo,
                Collections.emptyList()
        );

        orderResponsePage = new PageImpl<>(List.of(orderResponse), PageRequest.of(0, 10), 1);
    }

    @Test
    @DisplayName("관리자 주문 전체 조회 - GET /orders/admin")
    void getAllOrderByAdmin_Success() throws Exception {
        given(orderService.findAllOrders(any(), any(Pageable.class))).willReturn(orderResponsePage);

        mockMvc.perform(get("/orders/admin")
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "ADMIN")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(1L))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("회원 주문 전체 조회 - GET /orders")
    void getAllOrderByCustomer_Success() throws Exception {
        given(orderService.findAllOrderByMemberId(any(), any(Pageable.class))).willReturn(orderResponsePage);

        mockMvc.perform(get("/orders")
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "MEMBER")
                        .param("page", "0")
                        .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value(1L))
                .andExpect(jsonPath("$.content[0].memberId").value(1L));
    }

    @Test
    @DisplayName("회원 주문 생성 - POST /orders")
    void createMemberOrder_Success() throws Exception {
        OrderItemCreateRequest orderItem = new OrderItemCreateRequest(1L, 2, null, 1L, null);
        OrderCreateRequest createRequest = new OrderCreateRequest("홍길동", "010-1234-5678", "test@email.com", LocalDateTime.now().plusDays(1), "이순신", "010-9876-5432", "서울시 강남구", "12345", null, 1000, 1L, List.of(orderItem));
        given(orderService.createOrder(any(), any(OrderCreateRequest.class))).willReturn(orderResponse);

        mockMvc.perform(post("/orders")
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "MEMBER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/orders/1"))
                .andExpect(jsonPath("$.orderId").value(1L));
    }

    @Test
    @DisplayName("회원 주문 단건 조회 - GET /orders/{orderId}")
    void getOrderByCustomer_Success() throws Exception {
        given(orderService.findOrderByOrderId(any(), eq(1L))).willReturn(orderResponse);

        mockMvc.perform(get("/orders/{orderId}", 1L)
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "MEMBER"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1L));
    }

    @Test
    @DisplayName("비회원 주문 단건 조회 - POST /orders/non-members/")
    void getOrderForNonMember_Success() throws Exception {
        NonMemberOrderGetRequest request = new NonMemberOrderGetRequest("ORD-20251202-12345", "password123");
        given(nonMemberOrderService.findOrderByOrderNumber(eq("ORD-20251202-12345"), eq("password123"))).willReturn(orderResponse);

        mockMvc.perform(post("/orders/non-members/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-20251202-12345"));
    }

    @Test
    @DisplayName("회원 주문 상품 상태 변경 - PATCH /orders/{orderId}/items/{orderItemId}")
    void patchOrderItemStatusByCustomer_Success() throws Exception {
        OrderItemStatusPatchRequest request = new OrderItemStatusPatchRequest(OrderItemStatus.SHIPPED);
        given(orderService.patchOrderItemStatus(any(), anyLong(), anyLong(), any(OrderItemStatusPatchRequest.class))).willReturn(orderResponse);

        mockMvc.perform(patch("/orders/{orderId}/items/{orderItemId}", 1L, 1L)
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "MEMBER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(orderService).patchOrderItemStatus(any(), eq(1L), eq(1L), any(OrderItemStatusPatchRequest.class));
    }

    @Test
    @DisplayName("비회원 주문 상품 상태 변경 - PATCH /orders/non-members/{orderId}/items/{orderItemId}")
    void patchOrderItemStatusForNonMember_Success() throws Exception {
        NonMemberOrderItemStatusPatchRequest request = new NonMemberOrderItemStatusPatchRequest("password123", OrderItemStatus.CANCELED);
        given(nonMemberOrderService.patchOrderItemStatusForNonMember(anyLong(), anyLong(), any(NonMemberOrderItemStatusPatchRequest.class))).willReturn(orderResponse);

        mockMvc.perform(patch("/orders/non-members/{orderId}/items/{orderItemId}", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());

        verify(nonMemberOrderService).patchOrderItemStatusForNonMember(eq(1L), eq(1L), any(NonMemberOrderItemStatusPatchRequest.class));
    }

    @Test
    @DisplayName("회원 주문 취소 - DELETE /orders/{orderId}")
    void cancelOrder_Success() throws Exception {
        doNothing().when(orderService).cancelOrder(any(), anyLong());

        mockMvc.perform(delete("/orders/{orderId}", 1L)
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "MEMBER"))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(orderService).cancelOrder(any(), eq(1L));
    }

    @Test
    @DisplayName("비회원 주문 취소 - DELETE /orders/non-members/{orderId}")
    void cancelOrderForNonMember_Success() throws Exception {
        NonMemberOrderCancelRequest cancelRequest = new NonMemberOrderCancelRequest("password123");
        doNothing().when(nonMemberOrderService).cancelOrderForNonMember(anyLong(), anyString());

        mockMvc.perform(delete("/orders/non-members/{orderId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andDo(print())
                .andExpect(status().isNoContent());

        verify(nonMemberOrderService).cancelOrderForNonMember(1L, "password123");
    }

    @Test
    @DisplayName("주문 생성 실패 - 유효하지 않은 입력")
    void createMemberOrder_Fail_InvalidInput() throws Exception {
        // ordererName is blank
        OrderCreateRequest createRequest = new OrderCreateRequest("", "010-1234-5678", "test@email.com", LocalDateTime.now().plusDays(1), "이순신", "010-9876-5432", "서울시 강남구", "12345", null, 1000, 1L, List.of(new OrderItemCreateRequest(1L, 1, null, null, null)));

        mockMvc.perform(post("/orders")
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "MEMBER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비회원 주문 조회 실패 - 유효하지 않은 입력")
    void getOrderForNonMember_Fail_InvalidInput() throws Exception {
        // orderNumber is blank
        NonMemberOrderGetRequest request = new NonMemberOrderGetRequest("", "password123");

        mockMvc.perform(post("/orders/non-members/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 상품 상태 변경 실패 - 유효하지 않은 상태")
    void patchOrderItemStatusByCustomer_Fail_InvalidInput() throws Exception {
        // status is null
        OrderItemStatusPatchRequest request = new OrderItemStatusPatchRequest(null);

        mockMvc.perform(patch("/orders/{orderId}/items/{orderItemId}", 1L, 1L)
                        .header("X-USER-ID", "1")
                        .header("X-USER-ROLE", "MEMBER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비회원 주문 상품 상태 변경 실패 - 유효하지 않은 입력")
    void patchOrderItemStatusForNonMember_Fail_InvalidInput() throws Exception {
        // password is blank
        NonMemberOrderItemStatusPatchRequest request = new NonMemberOrderItemStatusPatchRequest("", OrderItemStatus.CANCELED);

        mockMvc.perform(patch("/orders/non-members/{orderId}/items/{orderItemId}", 1L, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비회원 주문 취소 실패 - 유효하지 않은 입력")
    void cancelOrderForNonMember_Fail_InvalidInput() throws Exception {
        // password is blank
        NonMemberOrderCancelRequest cancelRequest = new NonMemberOrderCancelRequest("");

        mockMvc.perform(delete("/orders/non-members/{orderId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }
}
