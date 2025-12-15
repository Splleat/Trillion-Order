package com.nhnacademy.order.orderitem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "Order Item API", description = "주문 상품 관련 API")
public interface OrderItemController {
    @Operation(summary = "상위 N개 판매 도서 ID 조회", description = "판매량이 가장 높은 상위 N개의 도서 ID를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공적으로 상위 N개 판매 도서 ID를 조회했습니다."),
            @ApiResponse(responseCode = "400", description = "잘못된 요청입니다.")
    })
    ResponseEntity<List<Long>> getTopNSellingBookIds(int limit);
}
