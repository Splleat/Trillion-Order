package com.nhnacademy.order.order.controller;

import com.nhnacademy.order.order.dto.NonMemberGetRequest;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.service.OrderService;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RequiredArgsConstructor
@RestController
public class OrderController {
    private final OrderService orderService;

    // 주문 전체 조회 (관리자)
    @GetMapping("/orders/admin")
    public ResponseEntity<Page<OrderResponse>> getAllOrderByAdmin(Pageable pageable) {
        Page<OrderResponse> response = orderService.findAllOrders(pageable);

        return ResponseEntity.ok(response);
    }

    // 주문 전체 조회 (회원)
    @GetMapping("/orders")
    public ResponseEntity<Page<OrderResponse>> getAllOrderByCustomer(Pageable pageable) {
        Long memberId = 1L; // TODO: JWT로 memberId 받기

        Page<OrderResponse> response = orderService.findAllOrderByMemberId(pageable, memberId);

        return ResponseEntity.ok(response);
    }

    // 주문 생성
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderCreateRequest request) {
        long memberId = 1L; // TODO: JWT로 memberId 받기, 비회원일 경우 null 처리

        Long createdOrderId = orderService.createOrder(memberId, request);

        URI locationUri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(createdOrderId)
                .toUri();

        return ResponseEntity.created(locationUri).build();
    }

    // 주문 단건 조회 (회원)
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<OrderResponse> getOrderByCustomer(@PathVariable Long orderId) {
        long memberId = 1L; // TODO: JWT로 memberId 받기

        OrderResponse response = orderService.findOrderByCustomer(memberId, orderId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/orders/{orderId}/items/{orderItemId}")
    public ResponseEntity<OrderResponse> patchOrderItemStatus(@PathVariable Long orderId,
                                                              @PathVariable Long orderItemId,
                                                              @RequestBody OrderItemStatusPatchRequest request) {
        Long memberId = 1L; // TODO: JWT로 memberId 받기

        orderService.patchOrderItemStatus(memberId, orderId, orderItemId, request);

        return ResponseEntity.ok().build();
    }

    // GET 메서드에는 본문이 없는 것이 일반적 -> POST 메서드로 변경
    @PostMapping("/orders/non-members/")
    public ResponseEntity<OrderResponse> getOrderForNonMember(@RequestBody @Valid NonMemberGetRequest request) {
        OrderResponse response = orderService.findOrderByOrderNumber(request.orderNumber(), request.nonMemberPassword());

        return ResponseEntity.ok(response);
    }
}
