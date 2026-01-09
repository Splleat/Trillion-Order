package com.nhnacademy.order.orderitem.controller.docs;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Order Item API", description = "주문 상품 관련 API")
public interface OrderItemControllerDocs {
    @Operation(summary = "상위 N개 판매 도서 ID 조회", description = "판매량이 가장 높은 상위 N개의 도서 ID를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공적으로 상위 N개 판매 도서 ID를 조회했습니다."),
            @ApiResponse(responseCode = "400", description = "잘못된 요청입니다.")
    })
    ResponseEntity<List<Long>> getTopNSellingBookIds(@RequestParam @Min(1) int limit);

    @Operation(summary = "환불/반품 요청된 주문 상품 목록 조회 (회원용)", description = "로그인한 회원의 주문 상품 중 환불되었거나 환불/반품 요청 상태인 상품 목록을 페이지별로 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "성공적으로 주문 상품 목록을 조회했습니다."),
            @ApiResponse(responseCode = "403", description = "요청에 필요한 권한이 없습니다.")
    })
    ResponseEntity<Page<OrderItemResponse>> getRefundedOrderItemsByMemberId(
            @Parameter(description = "페이지네이션 정보(예: ?page=0&size=10)", required = true) Pageable pageable,
            UserInfo userInfo);
}
