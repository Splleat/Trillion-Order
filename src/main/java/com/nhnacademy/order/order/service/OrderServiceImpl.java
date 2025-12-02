package com.nhnacademy.order.order.service;

import com.nhnacademy.order.client.dto.BookResponse;
import com.nhnacademy.order.client.service.BookService;
import com.nhnacademy.order.client.service.CouponService;
import com.nhnacademy.order.common.aop.AuthRole;
import com.nhnacademy.order.common.aop.CheckAuth;
import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.exception.PolicyNotConfiguredException;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.dto.NonMemberBaseResponse;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.exception.OrderPasswordMismatchException;
import com.nhnacademy.order.order.exception.OrderStatusTransitionException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.ordersaga.itemrefund.service.NonMemberOrderItemRefundOrchestrator;
import com.nhnacademy.order.ordersaga.itemrefund.service.OrderItemRefundOrchestrator;
import com.nhnacademy.order.packaging.domain.Packaging;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import com.nhnacademy.order.ordersaga.cancellation.service.OrderCancelOrchestrator;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {
    // Repository
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PackagingRepository packagingRepository;
    private final DeliveryPolicyRepository deliveryPolicyRepository;

    // Client
    private final BookService bookService;
    private final CouponService couponService;

    // Service
    private final OrderCreateService orderCreateService;
    private final OrderCancelService orderCancelService;

    // 사가 패턴
    private final OrderCreateOrchestrator orderCreateOrchestrator;
    private final OrderCancelOrchestrator orderCancelOrchestrator;
    private final OrderItemRefundOrchestrator orderItemRefundOrchestrator;
    private final NonMemberOrderItemRefundOrchestrator nonMemberOrderItemRefundOrchestrator;

    // 비회원 주문 비밀번호 인코딩
    private final PasswordEncoder passwordEncoder;

    private static final String ORDER_NOT_FOUND_MESSAGE = "존재하지 않는 주문 ID: ";

    // DTO (List<OrderItemCreateRequest>) -> Entity (List<OrderItem>)
    private List<OrderItem> buildOrderItems(List<OrderItemCreateRequest> requests, Map<Long, BookResponse> bookInfoMap) {
        List<Long> orderPackagingIds = requests.stream()
                .map(OrderItemCreateRequest::packagingId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Integer> packagingPriceMap = packagingRepository.findAllById(orderPackagingIds).stream()
                .collect(Collectors.toMap(Packaging::getPackagingId, Packaging::getPackagingPrice));

        return requests.stream()
                .map(request -> {
                    int bookPrice = bookInfoMap.get(request.bookId()).price();
                    int packagingPrice = 0;

                    if (request.packagingId() != null) {
                        packagingPrice = packagingPriceMap.getOrDefault(request.packagingId(), 0);
                    }

                    return OrderItem.create(null, request.bookId(), request.quantity(), bookPrice, packagingPrice);
                })
                .toList();
    }

    // 배송비 결정
    private int determineDeliveryFee(int finalTotalPrice) {
        DeliveryPolicy deliveryPolicy = deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()
                .orElseThrow(() -> new PolicyNotConfiguredException("배송 정책이 설정되지 않음"));

        return (finalTotalPrice >= deliveryPolicy.getDeliveryPolicyThreshold())
                ? 0
                : deliveryPolicy.getDeliveryPolicyFee();
    }

    // 비회원 주문 비밀번호 확인
    private void nonMemberPasswordCheck(String nonMemberPassword, String orderPassword) {
        if (!passwordEncoder.matches(nonMemberPassword, orderPassword)) {
            throw new OrderPasswordMismatchException("비회원 주문번호 불일치");
        }
    }

    // 주문 상품 상태 변경
    private void updateOrderItemStatus(UserInfo userInfo, Order order, Long orderItemId, OrderItemStatus status) {

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(status);

        String role = (userInfo != null) ? userInfo.role() : null;

        if (!strategy.hasPermission(role)) {
            throw new AccessDeniedException("주문 상품 상태 변경 권한이 없음");
        }

        // TODO: 여기도 수정 필요함. updateStatus()가 된 이후에 서버가 종료된다면??

        strategy.updateStatus(order, orderItemId);

        // 단건 환불
        if (strategy == OrderItemStatusUpdateStrategy.RETURNED) {
            OrderItem orderItem = order.findOrderItemInOrder(orderItemId);

            int deliveryFee = determineDeliveryFee(0);

            // 회원
            if (userInfo != null) {
                orderItemRefundOrchestrator.processItemRefund(userInfo.userId(), order, orderItem, deliveryFee);
            } else {
                // 비회원
                nonMemberOrderItemRefundOrchestrator.processNonMemberItemRefund(order, orderItem, deliveryFee);
            }
        }
    }

    // 전체 주문 조회
    @Override
    @CheckAuth(role = AuthRole.ADMIN)
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrders(UserInfo userInfo, Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);

        return orders.map(OrderResponse::create);
    }

    // 주문 생성
    @Override
    public OrderResponse createOrder(UserInfo userInfo, OrderCreateRequest request) {
        // 1. OrderDetails 값이 불완전한 초기 Order 생성
        String nonMemberPassword = Optional.ofNullable(request.nonMemberPassword())
                .map(passwordEncoder::encode)
                .orElse(null);
        OrdererInfo ordererInfo = new OrdererInfo(request.ordererName(), request.ordererContact());
        ReceiverInfo receiverInfo = new ReceiverInfo(request.receiverName(), request.receiverContact(), request.receiverAddress());
        OrderDetails initialOrderDetails = OrderDetails.createInitial(request.receiverPostCode(), request.deliveryDate(), request.pointUsage(), request.couponId());

        // 비회원인 경우 userId가 null
        Long userId = (userInfo != null) ? userInfo.userId() : null;

        Order order = orderCreateService.createInitialOrder(userId, nonMemberPassword, ordererInfo, receiverInfo, initialOrderDetails);

        try {
            // 2. 오케스트레이션 사가 시작 (재고 감소 -> 쿠폰 사용 -> 포인트 사용)
            orderCreateOrchestrator.processCreateOrder(userId, order);

            // TODO: 사가가 성공한 이후에 서버가 종료된다면?

            // 3. 초기 결제 금액, 최종 결제 금액, 배송비 연산
            List<Long> bookIds = request.orderItems().stream()
                    .map(OrderItemCreateRequest::bookId)
                    .toList();

            Map<Long, BookResponse> bookResponseMap = bookService.getBookInfos(bookIds);

            List<OrderItem> orderItems = buildOrderItems(request.orderItems(), bookResponseMap);

            // 순수 금액 (도서 * 재고 + 포장비)
            int originPrice = orderItems.stream()
                    .mapToInt(orderItem -> orderItem.getPrice() * orderItem.getQuantity() + orderItem.getPackagingPrice())
                    .sum();

            // 최종 결제 금액 (originPrice - (쿠폰 할인액 + 사용 포인트) + 배송비)
            int totalPrice = originPrice;

            int couponDiscount = 0;

            if (request.couponId() != null) {
                couponDiscount = couponService.calculateDiscount(request.couponId(), totalPrice);
            }

            totalPrice -= couponDiscount;

            int deliveryFee = determineDeliveryFee(totalPrice);

            totalPrice += deliveryFee;

            int pointUsage = request.pointUsage();

            totalPrice -= pointUsage;

            // 최종 금액이 음수가 되지 않도록 설정
            totalPrice = Math.max(0, totalPrice);

            // 4. 연산한 값들을 최종적으로 Order에 반영 후 저장
            orderCreateService.completeOrder(order, originPrice, totalPrice, deliveryFee, orderItems);
        } catch (Exception e) {
            // 5. 주문 생성 실패 (사가 패턴 실패 or 주문 생성 중 오류 발생)
            log.error("주문 ID: {} - 생성 실패: {}", order.getOrderId(), e.getMessage(), e);
            orderCreateService.createFailureOrder(order);
            throw e;
        }

        return OrderResponse.create(order);
    }

    // 주문 ID로 주문 찾기
    @Override
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
    @Override
    @CheckAuth(role = AuthRole.MEMBER)
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrderByMemberId(UserInfo userInfo, Pageable pageable) {
        Page<OrderBaseResponse> orderBaseResponses = orderRepository.findAllBaseOrderByMemberId(pageable, userInfo.userId());

        List<Long> orderIds = orderBaseResponses.stream()
                .map(OrderBaseResponse::orderId)
                .toList();

        Map<Long, List<OrderItemResponse>> orderItemResponses;

        // 주문 ID가 하나라도 존재해야 주문 상품 목록 조회
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
    @Override
    @CheckAuth(role = AuthRole.MEMBER)
    @Transactional
    public void patchOrderItemStatus(UserInfo userInfo, Long orderId, Long orderItemId, OrderItemStatusPatchRequest request) {

        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        updateOrderItemStatus(userInfo, order, orderItemId, request.status());
    }

    // 비회원 주문 상품 상태 변경 (주문 취소, 환불 요청)
    @Override
    @Transactional
    public void patchOrderItemStatusForNonMember(Long orderId, Long orderItemId, NonMemberOrderItemStatusPatchRequest request) {
        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        nonMemberPasswordCheck(request.nonMemberPassword(), order.getNonMemberPassword());

        updateOrderItemStatus(null, order, orderItemId, request.status());
    }

    // 주문 번호로 주문 찾기
    @Override
    @Transactional(readOnly = true)
    public OrderResponse findOrderByOrderNumber(String orderNumber, String nonMemberPassword) {
        Optional<NonMemberBaseResponse> nonMemberBaseResponseOptional = orderRepository.findNonMemberOrderByOrderNumber(orderNumber);

        return nonMemberBaseResponseOptional.map(nonMemberBaseResponse -> {
            nonMemberPasswordCheck(nonMemberPassword, nonMemberBaseResponse.nonMemberPassword());

            List<OrderItemResponse> items = orderItemRepository.findOrderItemByOrder_OrderId(nonMemberBaseResponse.orderId());

            return OrderResponse.create(nonMemberBaseResponse.toOrderBaseResponse(), items);
        }).orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderNumber));
    }

    // 주문 전체 취소
    @Override
    @PreAuthorize("@securityService.isOrderOwner(#userInfo, #orderId)")
    @CheckAuth(role = AuthRole.MEMBER, checkOrderOwner = true)
    public void cancelOrder(UserInfo userInfo, Long orderId) {
        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        boolean orderCancellable = order.getOrderItems().stream()
                .allMatch(orderItem -> orderItem.getOrderItemStatus() == OrderItemStatus.PREPARING);

        if (!orderCancellable) {
            throw new OrderStatusTransitionException("주문 취소가 불가능한 상태의 상품이 포함되어 있음");
        }

        try {
            orderCancelOrchestrator.processCancelOrder(userInfo.userId(), order);

            // TODO: 사가가 완료된 이후 서버가 종료된다면?

            orderCancelService.completeOrder(order);
        } catch (Exception e) {
            log.error("주문 ID: {} - 취소 실패: {}", order.getOrderId(), e.getMessage(), e);
            // 오케스트레이터 내부에서 이미 FAILED 처리 및 로깅 되었음.
            // completeOrder 실패 -> 아마 스케줄러나 배치 서버로 처리해야 할듯?
            // throw e; -> 사용자는 실패 여부를 알 필요 없음. 재시도를 통해 반드시 달성
        }
    }

    @Override
    public void cancelOrderForNonMember(Long orderId, String nonMemberPassword) {
        Order order = orderRepository.findOrderWithItemsByOrderId(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        nonMemberPasswordCheck(nonMemberPassword, order.getNonMemberPassword());

        try {
            orderCancelOrchestrator.processCancelOrder(null, order);

            // TODO: 사가가 완료된 이후 서버가 종료된다면?

            orderCancelService.completeOrder(order);
        } catch (Exception e) {
            log.error("주문 ID: {} - 취소 실패: {}", order.getOrderId(), e.getMessage(), e);
        }
    }
}
