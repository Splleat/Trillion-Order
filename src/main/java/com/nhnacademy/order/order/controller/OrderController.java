package com.nhnacademy.order.order.controller;

import com.nhnacademy.order.order.dto.GuestOrderGetRequest;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.service.OrderService;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
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

    @GetMapping("/orders/admin")
    public ResponseEntity<Page<OrderResponse>> getOrderByAdmin(Pageable pageable) {
        Page<OrderResponse> response = orderService.findAllOrders(pageable);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderCreateRequest request) {
        long memberId = 1L; // TODO: JWT로 memberId 받기

        Long createdOrderId = orderService.createOrder(memberId, request);

        URI locationUri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{orderId}")
                .buildAndExpand(createdOrderId)
                .toUri();

        return ResponseEntity.created(locationUri).build();
    }

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

    @PostMapping("/orders/non-members/")
    public ResponseEntity<OrderResponse> getOrderForNonMember(@RequestBody GuestOrderGetRequest request) {
        OrderResponse response = orderService.findOrderByOrderNumber(request.orderNumber(), request.nonMemberPassword());

        return ResponseEntity.ok(response);
    }
}
