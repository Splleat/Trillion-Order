package com.nhnacademy.order.order.dto;

import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;

public record OrderCreateRequest(
    @NotBlank(message = "주문자 이름은 필수입니다.")
    String ordererName,
    @NotBlank(message = "주문자 연락처는 필수입니다.")
    String ordererContact,
    @Future(message = "배송일은 미래 날짜여야 합니다.")
    LocalDateTime deliveryDate,

    @NotBlank(message = "수령인 이름은 필수입니다.")
    String receiverName,
    @NotBlank(message = "수령인 연락처는 필수입니다.")
    String receiverContact,
    @NotBlank(message = "수령인 주소는 필수입니다.")
    String receiverAddress,
    @NotBlank(message = "수령인 우편번호는 필수입니다.")
    String receiverPostCode,

    String nonMemberPassword,

    @Min(value = 0, message = "포인트 사용량은 0 이상이어야 합니다.")
    int pointUsage,

    Long couponId,

    @NotEmpty(message = "주문 상품 목록은 비어 있을 수 없습니다.")
    List<@Valid OrderItemCreateRequest> orderItems
) {}
