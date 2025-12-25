package com.nhnacademy.order.order.service;

import com.nhnacademy.order.common.aop.AuthRole;
import com.nhnacademy.order.common.aop.CheckAuth;
import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderCreateFailureException;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.orderitem.service.OrderItemService;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderService {
    // Repository
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // Service
    private final OrderInitialCreateService orderInitialCreateService;
    private final OrderFinalizerCreateService orderFinalizerCreateService;
    private final OrderItemService orderItemService;

    // 사가 패턴
    private final OrderCreateOrchestrator orderCreateOrchestrator;
    private final OrderCancelOrchestrator orderCancelOrchestrator;

    // 비회원 주문 비밀번호 인코딩
    private final PasswordEncoder passwordEncoder;

    private static final String ORDER_NOT_FOUND_MESSAGE = "존재하지 않는 주문 ID: ";

    // 초기 주문 생성
    private Order createInitialOrder(UserInfo userInfo, OrderCreateRequest request) {
        String nonMemberPassword = Optional.ofNullable(request.nonMemberPassword())
                .map(passwordEncoder::encode)
                .orElse(null);
        OrdererInfo ordererInfo = new OrdererInfo(request.ordererName(), request.ordererContact(), request.ordererEmail());
        ReceiverInfo receiverInfo = new ReceiverInfo(request.receiverName(), request.receiverContact(), request.receiverAddress());
        OrderDetails initialOrderDetails = OrderDetails.createInitial(request.receiverPostCode(), request.deliveryDate(), request.pointUsage());
        OrderCoupon initialOrderCoupon = null;

        if (request.couponId() != null) {
            initialOrderCoupon = OrderCoupon.createInitial(request.couponId());
        }

        // 비회원인 경우 userId가 null
        Long userId = (userInfo != null) ? userInfo.userId() : null;

        return orderInitialCreateService.createInitialOrder(userId, nonMemberPassword, ordererInfo, receiverInfo, initialOrderDetails, initialOrderCoupon, request.orderItems());
    }

    // 전체 주문 조회
    @CheckAuth(role = AuthRole.ADMIN)
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrders(UserInfo userInfo, Pageable pageable) {
        // 1. 주문 기본 정보 조회 (N+1 문제 방지를 위해 2번에 나누어 조회)
        Page<OrderBaseResponse> orderBaseResponses = orderRepository.findAllBaseOrder(pageable);

        // 2. 주문 ID 목록 추출
        List<Long> orderIds = orderBaseResponses.stream()
                .map(OrderBaseResponse::orderId)
                .toList();

        // 3. 주문 ID목록으로 배치 조회
        Map<Long, List<OrderItemResponse>> orderItemResponses = orderItemRepository.findAllByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemResponse::orderId));

        // 4. 주문 기본 정보와 주문 상품 정보를 결합하여 OrderResponse 생성
        return orderBaseResponses.map(orderBaseResponse -> {
            List<OrderItemResponse> orderItems = orderItemResponses.getOrDefault(orderBaseResponse.orderId(), Collections.emptyList());

            return OrderResponse.create(orderBaseResponse, orderItems);
        });
    }

    // 주문 생성
    public OrderResponse createOrder(UserInfo userInfo, OrderCreateRequest request) {
        // 1. 불완전한 초기 Order 생성 (OrderStatus: CREATING)
        Order order = createInitialOrder(userInfo, request);

        UUID sagaId = UUID.randomUUID();
        OrderCreateSaga saga = OrderCreateSaga.create(sagaId, order.getOrderId());

        try {
            // 2. 오케스트레이션 사가 시작 (재고 감소 -> 쿠폰 사용 -> 포인트 사용)
            orderCreateOrchestrator.processCreateOrder(saga, order);

            // 3. 최종 처리 실행 (OrderStatus: CREATING -> PENDING)
            orderFinalizerCreateService.finalizeOrderCreation(order, saga);
        } catch (OrderCreateFailureException e) {
            // 주문 생성 중 문제 발생 시 무조건 보상 트랜잭션 시작
            // 기본적으로 외부 API와 2번 통신 재시도 -> 실패 시 OrderCreateFailureException이 던져짐
            log.error("주문 ID: {} - 생성 실패: {}", order.getOrderId(), e.getMessage(), e);
            orderCreateOrchestrator.compensate(saga, order);

            order.setOrderStatus(OrderStatus.CREATION_FAILED);
            orderRepository.save(order);
            throw e; // 주문 생성 실패는 사용자에게 알려야 함
        }

        return OrderResponse.create(order);
    }

    // 주문 ID로 주문 찾기
    @CheckAuth(role = AuthRole.MEMBER, checkOrderOwner = true)
    @Transactional(readOnly = true)
    public OrderResponse findOrderByOrderId(UserInfo userInfo, Long orderId) {
        Optional<OrderBaseResponse> orderBaseResponseOptional = orderRepository.findBaseOrderById(orderId);

        return orderBaseResponseOptional.map( orderBaseResponse -> {
            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(orderId);

            return OrderResponse.create(orderBaseResponse, orderItems);
        }).orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));
    }

    // 자신의 회원 ID로 주문 목록 찾기
    @CheckAuth(role = AuthRole.MEMBER)
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrderByMemberId(UserInfo userInfo, Pageable pageable) {
        // 1. 주문 기본 정보 조회 (N+1 문제 방지를 위해 2번에 나누어 조회)
        Page<OrderBaseResponse> orderBaseResponses = orderRepository.findAllBaseOrderByMemberIdAndOrderStatus(pageable, userInfo.userId(), OrderStatus.COMPLETED);

        // 2. 주문 ID 목록 추출
        List<Long> orderIds = orderBaseResponses.stream()
                .map(OrderBaseResponse::orderId)
                .toList();

        // 3. 주문 ID목록으로 배치 조회
        Map<Long, List<OrderItemResponse>> orderItemResponses;

        // 주문이 없는 경우 빈 맵 할당
        if (!orderIds.isEmpty()) {
            orderItemResponses = orderItemRepository.findAllByOrderIds(orderIds).stream()
                    .collect(Collectors.groupingBy(OrderItemResponse::orderId));
        } else {
            orderItemResponses = Collections.emptyMap();
        }

        return orderBaseResponses.map(orderBaseResponse -> {

            List<OrderItemResponse> items = orderItemResponses.getOrDefault(orderBaseResponse.orderId(), Collections.emptyList());

            return OrderResponse.create(orderBaseResponse, items);
        });
    }

    // 자신의 ID로 취소된 주문 목록 찾기
    @CheckAuth(role = AuthRole.MEMBER)
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllCanceledOrderByMemberId(UserInfo userInfo, Pageable pageable) {
        // 1. 주문 기본 정보 조회 (N+1 문제 방지를 위해 2번에 나누어 조회)
        Page<OrderBaseResponse> orderBaseResponses = orderRepository.findAllBaseOrderByMemberIdAndOrderStatus(pageable, userInfo.userId(), OrderStatus.CANCELED);

        // 2. 주문 ID 목록 추출
        List<Long> orderIds = orderBaseResponses.stream()
                .map(OrderBaseResponse::orderId)
                .toList();

        // 3. 주문 ID목록으로 배치 조회
        Map<Long, List<OrderItemResponse>> orderItemResponses;

        // 주문이 없는 경우 빈 맵 할당
        if (!orderIds.isEmpty()) {
            orderItemResponses = orderItemRepository.findAllByOrderIds(orderIds).stream()
                    .collect(Collectors.groupingBy(OrderItemResponse::orderId));
        } else {
            orderItemResponses = Collections.emptyMap();
        }

        return orderBaseResponses.map(orderBaseResponse -> {

            List<OrderItemResponse> items = orderItemResponses.getOrDefault(orderBaseResponse.orderId(), Collections.emptyList());

            return OrderResponse.create(orderBaseResponse, items);
        });
    }

    // 주문 상품 상태 변경 (회원 & 관리자)
    @CheckAuth(role = AuthRole.MEMBER, checkOrderOwner = true)
    public OrderResponse patchOrderItemStatus(UserInfo userInfo, Long orderId, Long orderItemId, OrderItemStatusPatchRequest request) {

        // 1. 주문 조회
        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        OrderItem orderItem = order.findOrderItemInOrder(orderItemId);

        // 2. 주문 상품 상태 변경
        orderItemService.updateOrderItemStatus(userInfo, order, orderItem, request.status());

        return OrderResponse.create(order);
    }

    // 주문 전체 취소
    @CheckAuth(role = AuthRole.MEMBER, checkOrderOwner = true)
    public void cancelOrder(UserInfo userInfo, Long orderId) {
        // 1. 주문 조회
        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        // 2. 주문이 취소 가능한 상태인지 확인 (모든 주문 상품이 PREPARING 상태여야 함)
        boolean orderCancellable = order.getOrderItems().stream()
                .allMatch(orderItem -> orderItem.getOrderItemStatus() == OrderItemStatus.PREPARING);

        // 3. 취소 불가능한 상태의 상품이 포함된 경우 예외 발생
        if (!orderCancellable) {
            throw new OrderStatusTransitionException("주문 취소가 불가능한 상태의 상품이 포함되어 있음");
        }

        try {
            // 4. 주문 취소 사가 진행
            orderCancelOrchestrator.processCancelOrder(userInfo.userId(), order);
        } catch (Exception e) {
            log.error("주문 ID: {} - 취소 실패: {}", order.getOrderId(), e.getMessage(), e);
            // 사가 시작과 동시에 주문 상태가 '취소 처리 중'으로 변경
            // 만약 실패하더라도 스케줄러가 실패한 사가를 재시도
            // 사용자에게는 이미 '취소 처리 중'으로 보이므로 별도의 예외를 던지지 않아도 됨
        }
    }


}
