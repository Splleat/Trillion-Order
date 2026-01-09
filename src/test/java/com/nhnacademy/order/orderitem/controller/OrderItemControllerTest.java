package com.nhnacademy.order.orderitem.controller;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.common.resolver.UserInfoArgumentResolver;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.service.OrderItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderItemControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderItemService orderItemService;

    @InjectMocks
    private OrderItemController orderItemController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderItemController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(), // Pageable 지원
                        new UserInfoArgumentResolver() // UserInfo 지원
                )
                .build();
    }

    @DisplayName("상위 N개 판매 도서 ID 조회 성공")
    @Test
    void getTopNSellingBookIds_Success() throws Exception {
        // given
        int limit = 5;
        List<Long> bookIds = List.of(1L, 2L, 3L, 4L, 5L);
        given(orderItemService.getTopNSellingBookIds(limit)).willReturn(bookIds);

        // when & then
        mockMvc.perform(get("/order-items/top-selling")
                        .param("limit", String.valueOf(limit))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0]").value(1L));
    }

    @DisplayName("회원 환불/반품 요청 목록 조회 성공")
    @Test
    void getRefundedOrderItemsByMemberId_Success() throws Exception {
        // given
        long memberId = 123L;
        String role = "MEMBER";

        Page<OrderItemResponse> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);

        // UserInfo 검증은 서비스가 호출될 때 전달되는 인자를 통해 간접적으로 확인 가능하지만,
        // 여기서는 서비스 메서드가 호출되는지만 확인합니다.
        given(orderItemService.findRefundedOrderItemsByMemberId(any(UserInfo.class), any(Pageable.class)))
                .willReturn(page);

        // when & then
        mockMvc.perform(get("/order-items/refunds")
                        .header("X-Member-Id", String.valueOf(memberId))
                        .header("X-Member-Role", role)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
