package com.nhnacademy.order.order.controller;

import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.service.OrderService;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RequiredArgsConstructor
@RestController
public class OrderController {
    private final OrderService orderService;

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
    public ResponseEntity<OrderResponse> getOrderByOrderId(@PathVariable Long orderId) {
        OrderResponse response = orderService.findOrderByOrderId(orderId);

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
}
