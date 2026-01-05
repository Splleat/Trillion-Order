package com.nhnacademy.cart.controller;

import com.nhnacademy.cart.common.annotation.GuestOnly;
import com.nhnacademy.cart.common.resolver.CartHolder;
import com.nhnacademy.cart.dto.*;
import com.nhnacademy.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
@Slf4j
public class CartController implements CartControllerDocs {

    private final CartService cartService;

    /**
     * - CartHolder: Resolver가 헤더(회원)/쿠키(비회원)를 판단해 주입
     */

    /**
     * [장바구니 담기]
     * POST /carts
     */
    @PostMapping
    public ResponseEntity<Void> addCartItem(
            CartHolder holder,
            @Valid @RequestBody CartCreateRequestDto request
    ) {
        // Request DTO -> Service DTO 변환
        CartDto serviceDto = CartDto.builder()
                .bookId(request.getBookId())
                .cartQuantity(request.getCartQuantity())
                .build();

        cartService.addCartItem(holder, serviceDto);

        return ResponseEntity.noContent().build();
    }

    /**
     * [수량 변경]
     * PUT /carts/{book-id}
     */
    @PutMapping("/{book-id}")
    public ResponseEntity<Void> updateCartItem(
            CartHolder holder,
            @PathVariable("book-id") Long bookId,
            @Valid @RequestBody CartUpdateRequestDto request
    ) {
        cartService.updateCartItem(holder, bookId, request.getCartQuantity());
        return ResponseEntity.noContent().build();
    }

    /**
     * [상품 삭제]
     * DELETE /carts/{book-id}
     */
    @DeleteMapping("/{book-id}")
    public ResponseEntity<Void> removeCartItem(CartHolder holder, @PathVariable("book-id")  Long bookId) {
        cartService.removeCartItem(holder, bookId);
        return ResponseEntity.noContent().build();
    }

    /**
     * [장바구니 비우기]
     * DELETE /carts
     */
    @DeleteMapping
    public ResponseEntity<Void> clearCart(CartHolder holder) {
        cartService.clearCart(holder);
        return ResponseEntity.noContent().build();
    }

    /**
     * [장바구니 목록 조회]
     * GET /carts
     */
    @GetMapping
    public ResponseEntity<List<CartResponseDto>> getCartItems(CartHolder holder) {
        List<CartDto> items = cartService.getCartItems(holder);

        List<CartResponseDto> responses = items.stream()
                .sorted(Comparator.comparing(CartDto::getCreatedAt).reversed())
                .map(dto -> CartResponseDto.of(dto, holder))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * [장바구니 요약 정보 조회]
     * GET /carts/summary
     * - 헤더 아이콘 배지 표시용
     */
    @GetMapping("/summary")
    public ResponseEntity<CartSummaryResponseDto> getCartSummary(CartHolder holder) {
        CartSummaryDto cartSummaryDto = cartService.getCartSummary(holder);

        return ResponseEntity.ok(CartSummaryResponseDto.of(cartSummaryDto));
    }

    /**
     * [장바구니 병합 (로그인 직후)]
     * POST /carts/merge
     * - memberHolder: 헤더(X-Member-Id)에서 파싱된 회원 정보
     * - guestHolder: 헤더(X-Guest-Id)에서 강제로 파싱된 비회원 정보 (@GuestOnly)
     */
    @PostMapping("/merge")
    public ResponseEntity<Void> mergeCart(
            CartHolder memberHolder,           // Resolver가 헤더 보고 주입
            @GuestOnly CartHolder guestHolder  // Resolver가 쿠키만 보고 주입
    ) {
        List<CartDto> mitems = cartService.getCartItems(memberHolder);
        List<CartDto> gitems = cartService.getCartItems(guestHolder);

        log.warn("장바구니 병합, 멤버 장바구니");
        for(CartDto cart: mitems){
            log.warn("책번호: {}",cart.getBookId());
        }

        log.warn("장바구니 병합, 비회원 장바구니");
        for(CartDto cart: gitems){
            log.warn("책번호: {}",cart.getBookId());
        }
        cartService.mergeCart(memberHolder, guestHolder);

        log.warn("장바구니 병합, 결과... 회원 장바구니");
        for(CartDto cart: mitems){
            log.warn("책번호: {}",cart.getBookId());
        }
        return ResponseEntity.noContent().build();
    }
}