package com.nhnacademy.payment.controller.docs;

import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.reqeust.PaymentCancelRequestDto;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Payment API", description = "결제 관련 API (회원용)")
public interface PaymentControllerDocs {

    @Operation(summary = "결제 승인", description = "사용자의 결제를 승인합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 승인 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청")
    })
    ResponseEntity<?> confirmPayment(@Parameter(hidden = true) PaymentUser user,
                                     @Parameter(description = "결제 승인 요청 정보", required = true) @RequestBody PaymentRequestDto request);

    @Operation(summary = "내 결제 내역 조회", description = "회원의 모든 결제 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 내역 조회 성공"),
            @ApiResponse(responseCode = "500", description = "권한 없음 (비회원 접근 시)")
    })
    ResponseEntity<?> getMemberPayments(@Parameter(hidden = true) PaymentUser user,
                                        @Parameter(description = "페이지네이션 정보") Pageable pageable);

    @Operation(summary = "결제 취소", description = "결제를 취소하거나 부분 취소합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 취소 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "409", description = "이미 취소되었거나 승인되지 않은 결제")
    })
    ResponseEntity<?> cancelPayment(@Parameter(hidden = true) PaymentUser user,
                                    @Parameter(description = "결제 취소 요청 정보", required = true) @RequestBody PaymentCancelRequestDto request);

    @Operation(summary = "결제 내역 단건 조회", description = "주문 번호로 특정 결제 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 내역 조회 성공"),
            @ApiResponse(responseCode = "404", description = "결제 내역을 찾을 수 없음")
    })
    ResponseEntity<?> getPayment(@Parameter(hidden = true) PaymentUser user,
                                 @Parameter(description = "주문 번호", required = true) @PathVariable("order-number") String orderNumber);
}
