package com.nhnacademy.order.orderitem.controller;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.service.OrderItemService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/order-items")
@Validated
public class OrderItemControllerImpl implements OrderItemController {
    private final OrderItemService orderItemService;

    @Override
    @GetMapping("/top-selling")
    public ResponseEntity<List<Long>> getTopNSellingBookIds(@RequestParam @Min(1) int limit) {
        List<Long> topSellingBookIds = orderItemService.getTopNSellingBookIds(limit);

        return ResponseEntity.ok(topSellingBookIds);
    }

    @Override
    @GetMapping("/refunds")
    public ResponseEntity<Page<OrderItemResponse>> getRefundedOrderItemsByMemberId(Pageable pageable, UserInfo userInfo) {
        Page<OrderItemResponse> refundedItems = orderItemService.findRefundedOrderItemsByMemberId(userInfo, pageable);
        return ResponseEntity.ok(refundedItems);
    }
}
