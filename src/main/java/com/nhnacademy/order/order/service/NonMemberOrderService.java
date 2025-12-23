package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.dto.NonMemberOrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.exception.OrderPasswordMismatchException;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.orderitem.service.OrderItemService;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NonMemberOrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderCancelOrchestrator orderCancelOrchestrator;
    private final OrderItemService orderItemService;
    private final PasswordEncoder passwordEncoder;

    // 비회원 주문 비밀번호 확인
    private void nonMemberPasswordCheck(String nonMemberPassword, String orderPassword) {
        if (nonMemberPassword == null || !passwordEncoder.matches(nonMemberPassword, orderPassword)) {
            throw new OrderPasswordMismatchException("비회원 주문번호 불일치");
        }
    }

    private static final String ORDER_NOT_FOUND_MESSAGE = "존재하지 않는 주문 ID: ";

    // 주문 번호로 주문 찾기
    @Transactional(readOnly = true)
    public OrderResponse findOrderByOrderNumber(String orderNumber, String nonMemberPassword) {
        // 1. 비회원 주문 기본 정보 조회
        Optional<NonMemberOrderBaseResponse> nonMemberBaseResponseOptional = orderRepository.findNonMemberOrderByOrderNumber(orderNumber);

        // 2. 비회원 비밀번호 확인 및 OrderResponse 생성
        return nonMemberBaseResponseOptional.map(nonMemberOrderBaseResponse -> {
            nonMemberPasswordCheck(nonMemberPassword, nonMemberOrderBaseResponse.nonMemberPassword());

            List<OrderItemResponse> items = orderItemRepository.findOrderItemByOrder_OrderId(nonMemberOrderBaseResponse.orderId());

            return OrderResponse.create(nonMemberOrderBaseResponse.toOrderBaseResponse(), items);
        }).orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderNumber));
    }

    // 비회원 주문 상품 상태 변경 (주문 취소, 환불 요청)
    public OrderResponse patchOrderItemStatusForNonMember(Long orderId, Long orderItemId, NonMemberOrderItemStatusPatchRequest request) {
        // 1. 주문 조회
        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        OrderItem orderItem = order.findOrderItemInOrder(orderItemId);

        // 2. 비회원 비밀번호 확인
        nonMemberPasswordCheck(request.nonMemberPassword(), order.getNonMemberPassword());

        // 3. 주문 상품 상태 변경
        orderItemService.updateOrderItemStatus(null, order, orderItem, request.status());

        return OrderResponse.create(order);
    }

    // 비회원 주문 전체 취소
    public void cancelOrderForNonMember(Long orderId, String nonMemberPassword) {
        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        nonMemberPasswordCheck(nonMemberPassword, order.getNonMemberPassword());

        // 1. 주문이 취소 가능한 상태인지 확인
        boolean orderCancellable = order.getOrderItems().stream()
                .allMatch(orderItem -> orderItem.getOrderItemStatus() == OrderItemStatus.PREPARING);

        if (!orderCancellable) {
            throw new OrderStatusTransitionException("주문 취소가 불가능한 상태의 상품이 포함되어 있음");
        }

        try {
            // 2. 주문 취소 사가 진행
            // 이제 주문 취소 사가가 완료된 주문에 대해, 주문 상태 변경 및 브릿징까지 동기적으로 처리
            orderCancelOrchestrator.processCancelOrder(null, order);

            // 사가 완료 전에 서버가 종료되면 스케줄러가 사가 재시도
        } catch (Exception e) {
            log.error("주문 ID: {} - 취소 실패: {}", order.getOrderId(), e.getMessage(), e);
        }
    }
}
