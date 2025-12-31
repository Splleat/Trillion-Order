package com.nhnacademy.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.cart.common.annotation.GuestOnly;
import com.nhnacademy.cart.common.resolver.CartHolder;
import com.nhnacademy.cart.dto.*;
import com.nhnacademy.cart.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CartController.class)
@Import(CartControllerTest.TestWebConfig.class) // 테스트용 리졸버 주입
class CartControllerTest {
    @MockitoBean //Jpa 없어도 실행 가능하게 모킹
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CartService cartService;

    // 테스트용 고정 CartHolder
    private static final CartHolder TEST_MEMBER_HOLDER = CartHolder.member(100L);
    private static final CartHolder TEST_GUEST_HOLDER = CartHolder.guest("guest-123");

    /**
     * [테스트 설정]
     * 실제 Resolver 로직을 가져오는 대신,
     * 테스트에서는 무조건 '회원 Holder'나 '비회원 Holder'를 주입하는 Mock Resolver를 사용
     * 이렇게 해야 리졸버 내부 로직과 상관없이 '컨트롤러 로직'만 100% 검증 가
     */
    @TestConfiguration
    static class TestWebConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterType().equals(CartHolder.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                    // @GuestOnly 어노테이션이 있으면 비회원 리턴, 없으면 회원 리턴 (mergeCart 테스트용)
                    if (parameter.hasParameterAnnotation(GuestOnly.class)) {
                        return TEST_GUEST_HOLDER;
                    }
                    return TEST_MEMBER_HOLDER;
                }
            });
        }
    }

    @Test
    @DisplayName("POST /carts - 장바구니 담기 성공")
    void addCartItem() throws Exception {
        // given
        CartCreateRequestDto request = new CartCreateRequestDto(1L, 2); // bookId=1, qty=2
        String json = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/carts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNoContent()) // 204
                .andDo(print());

        // verify
        verify(cartService).addCartItem(eq(TEST_MEMBER_HOLDER), any(CartDto.class));
    }

    @Test
    @DisplayName("POST /carts - 유효성 검증 실패 (수량 0)")
    void addCartItem_ValidationFail() throws Exception {
        // given
        CartCreateRequestDto request = new CartCreateRequestDto(1L, 0); // Invalid Qty
        String json = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/carts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest()) // 400 (Validator 동작 확인)
                .andDo(print());
    }

    @Test
    @DisplayName("PUT /carts/{book-id} - 수량 변경 성공")
    void updateCartItem() throws Exception {
        // given
        Long bookId = 10L;
        CartUpdateRequestDto request = new CartUpdateRequestDto(5); // qty=5
        String json = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(put("/carts/{book-id}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isNoContent())
                .andDo(print());

        verify(cartService).updateCartItem(TEST_MEMBER_HOLDER, bookId, 5);
    }

    @Test
    @DisplayName("DELETE /carts/{book-id} - 상품 삭제 성공")
    void removeCartItem() throws Exception {
        // given
        Long bookId = 10L;

        // when & then
        mockMvc.perform(delete("/carts/{book-id}", bookId))
                .andExpect(status().isNoContent())
                .andDo(print());

        verify(cartService).removeCartItem(TEST_MEMBER_HOLDER, bookId);
    }

    @Test
    @DisplayName("DELETE /carts - 장바구니 비우기 성공")
    void clearCart() throws Exception {
        // when & then
        mockMvc.perform(delete("/carts"))
                .andExpect(status().isNoContent())
                .andDo(print());

        verify(cartService).clearCart(TEST_MEMBER_HOLDER);
    }

    @Test
    @DisplayName("GET /carts - 장바구니 목록 조회 (정렬 로직 검증)")
    void getCartItems() throws Exception {
        // given
        // 서비스는 시간 순서가 섞인 데이터를 리턴한다고 가정
        CartDto item1 = new CartDto(1L, 1, LocalDateTime.now().minusHours(1)); // 1시간 전
        CartDto item2 = new CartDto(2L, 2, LocalDateTime.now());               // 지금 (더 최신)

        given(cartService.getCartItems(any())).willReturn(List.of(item1, item2));

        // when & then
        mockMvc.perform(get("/carts"))
                .andExpect(status().isOk())
                .andDo(print())
                // 컨트롤러에서 최신순(역순) 정렬을 수행하는지 검증
                // item2가 더 최신이므로 첫 번째로 나와야 함
                .andExpect(jsonPath("$[0].bookId").value(2L))
                .andExpect(jsonPath("$[1].bookId").value(1L));
    }

    @Test
    @DisplayName("GET /carts/summary - 요약 정보 조회")
    void getCartSummary() throws Exception {
        // given
        CartSummaryDto summaryDto = new CartSummaryDto(3L, 10L); // 3종류, 10개
        given(cartService.getCartSummary(any())).willReturn(summaryDto);

        // when & then
        mockMvc.perform(get("/carts/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(3))
                .andExpect(jsonPath("$.totalQuantity").value(10))
                .andDo(print());
    }

    @Test
    @DisplayName("POST /carts/merge - 장바구니 병합")
    void mergeCart() throws Exception {
        // when & then
        mockMvc.perform(post("/carts/merge"))
                .andExpect(status().isNoContent())
                .andDo(print());

        // TestWebConfig에서 설정한대로
        // 첫 번째 인자(memberHolder)는 TEST_MEMBER_HOLDER
        // 두 번째 인자(guestHolder, @GuestOnly)는 TEST_GUEST_HOLDER가 주입되었는지 확인
        verify(cartService).mergeCart(TEST_MEMBER_HOLDER, TEST_GUEST_HOLDER);
    }
}