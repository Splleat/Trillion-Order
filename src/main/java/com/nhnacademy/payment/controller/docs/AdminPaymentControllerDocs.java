package com.nhnacademy.payment.controller.docs;

import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.reqeust.PaymentCancelRequestDto;
import com.nhnacademy.payment.dto.response.AdminPaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Admin Payment API", description = "결제 관리 API (관리자용)")
public interface AdminPaymentControllerDocs {

    @Operation(summary = "모든 결제 내역 조회", description = "시스템의 모든 결제 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 내역 조회 성공"),
            @ApiResponse(responseCode = "500", description = "관리자 권한 필요")
    })
    ResponseEntity<Page<AdminPaymentResponse>> getPayments(@Parameter(hidden = true) PaymentUser user,
                                                           @Parameter(description = "페이지네이션 정보") Pageable pageable);

    @Operation(summary = "결제 내역 단건 조회", description = "결제 ID로 특정 결제 내역을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "결제 내역 조회 성공"),
            @ApiResponse(responseCode = "404", description = "결제 내역을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "관리자 권한 필요")
    })
    ResponseEntity<?> getPayment(@Parameter(hidden = true) PaymentUser user,
                                 @Parameter(description = "결제 ID", required = true) @PathVariable("payment-id") Long paymentId);

    @Operation(summary = "결제 취소", description = "관리자 권한으로 결제를 취소합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "결제 취소 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "관리자 권한 필요")
    })
    ResponseEntity<?> cancelPayment(@Parameter(hidden = true) PaymentUser user,
                                    @Parameter(description = "결제 취소 요청 정보", required = true) @RequestBody PaymentCancelRequestDto request);
}
