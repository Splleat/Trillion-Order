package com.nhnacademy.order.order.service;

import com.nhnacademy.order.client.dto.BookResponse;
import com.nhnacademy.order.client.service.BookService;
import com.nhnacademy.order.client.service.CouponService;
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
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.exception.OrderItemNotFoundException;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.ordersaga.itemrefund.service.OrderItemRefundOrchestrator;
import com.nhnacademy.order.packaging.domain.Packaging;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import com.nhnacademy.order.ordersaga.cancelation.service.OrderCancelOrchestrator;
import com.nhnacademy.order.ordersaga.creation.service.OrderCreateOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

    // 전체 주문 조회
    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrders(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);

        return orders.map(OrderResponse::create);
    }

    // 주문 생성
    public OrderResponse createOrder(Long memberId, OrderCreateRequest request) {
        // 1. OrderDetails 값이 불완전한 초기 Order 생성
        String nonMemberPassword = Optional.ofNullable(request.nonMemberPassword())
                .map(passwordEncoder::encode)
                .orElse(null);
        OrdererInfo ordererInfo = new OrdererInfo(request.ordererName(), request.ordererContact());
        ReceiverInfo receiverInfo = new ReceiverInfo(request.receiverName(), request.receiverContact(), request.receiverAddress());
        OrderDetails initialOrderDetails = OrderDetails.createInitial(request.receiverPostCode(), request.deliveryDate(), request.pointUsage(), request.couponId());

        Order order = orderCreateService.createInitialOrder(memberId, nonMemberPassword, ordererInfo, receiverInfo, initialOrderDetails);

        try {
            // 2. 오케스트레이션 사가 시작 (재고 감소 -> 쿠폰 사용 -> 포인트 사용)
            orderCreateOrchestrator.processCreateOrder(memberId, order);

            // 3. 초기 결제 금액, 최종 결제 금액, 배송비 연산
            List<Long> bookIds = request.orderItems().stream()
                    .map(OrderItemCreateRequest::bookId)
                    .toList();

            Map<Long, BookResponse> bookResponseMap = bookService.getBookInfos(bookIds);

            List<OrderItem> orderItems = buildOrderItems(request.orderItems(), bookResponseMap);

            int originPrice = orderItems.stream()
                    .mapToInt(OrderItem::getPrice)
                    .sum();

            int totalPrice = originPrice;

            int couponDiscount = 0;

            if (Objects.nonNull(request.couponId())) {
                couponDiscount = couponService.calculateDiscount(request.couponId(), totalPrice);
            }

            totalPrice -= couponDiscount;

            int deliveryFee = determineDeliveryFee(totalPrice);

            int pointUsage = request.pointUsage();

            totalPrice -= pointUsage;

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

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findOrderByOrderId(Long orderId) {
        Optional<OrderBaseResponse> orderBaseResponseOptional = orderRepository.findBaseOrderById(orderId);

        return orderBaseResponseOptional.map( orderBaseResponse -> {
            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(orderId);

            return OrderResponse.create(orderBaseResponse, orderItems);
        }).orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAllOrderByMemberId(Pageable pageable, Long memberId) {
        Page<OrderBaseResponse> orderBaseResponses = orderRepository.findAllBaseOrderByMemberId(pageable, memberId);

        List<Long> orderIds = orderBaseResponses.stream()
                .map(OrderBaseResponse::orderId)
                .toList();

        Map<Long, List<OrderItemResponse>> orderItemResponses = orderItemRepository.findAllByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemResponse::orderId));

        return orderBaseResponses.map(orderBaseResponse -> {
            List<OrderItemResponse> items = orderItemResponses.getOrDefault(orderBaseResponse.orderId(), Collections.emptyList());

            return OrderResponse.create(orderBaseResponse, items);
        });

    }

    @Override
    @Transactional
    public void patchOrderItemStatus(Long memberId, Long orderId, Long orderItemId, OrderItemStatusPatchRequest request) {
        Order order = orderRepository.findOrderWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        OrderItem orderItem = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new OrderItemNotFoundException("존재하지 않는 주문 상품: " + orderItemId));

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(request.status());

        strategy.updateStatus(order, orderItemId);

        // TODO: 일단 만들어 두긴 했는데 리팩토링 필요할듯. 다른 메서드로 분리한다던가?
        // 단건 환불 승인
        if (strategy == OrderItemStatusUpdateStrategy.RETURNED) {

            int deliveryFee = determineDeliveryFee(0);

            orderItemRefundOrchestrator.processItemRefund(memberId, order, orderItem, deliveryFee);
        }

    }

    @Override
    @Transactional
    public void patchOrderItemStatusForNonMember(Long orderId, Long orderItemId, NonMemberOrderItemStatusPatchRequest request) {
        Order order = orderRepository.findOrderWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE));

        if (!passwordEncoder.matches(request.nonMemberPassword(), order.getNonMemberPassword())) {
            throw new OrderPasswordMismatchException("비회원 주문 비밀번호 불일치");
        }

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(request.status());

        strategy.updateStatus(order, orderItemId);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findOrderByOrderNumber(String orderNumber, String nonMemberPassword) {
        Optional<NonMemberBaseResponse> nonMemberBaseResponseOptional = orderRepository.findNonMemberOrderByOrderNumber(orderNumber);

        return nonMemberBaseResponseOptional.map(nonMemberBaseResponse -> {
            if (!passwordEncoder.matches(nonMemberPassword, nonMemberBaseResponse.nonMemberPassword())) {
                throw new OrderPasswordMismatchException("비회원 주문 비밀번호 불일치");
            }

            List<OrderItemResponse> items = orderItemRepository.findOrderItemByOrder_OrderId(nonMemberBaseResponse.orderId());

            return OrderResponse.create(nonMemberBaseResponse.toOrderBaseResponse(), items);
        }).orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderNumber));
    }

    @Override
    public void cancelOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findOrderWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));

        try {
            orderCancelOrchestrator.processCancelOrder(memberId, order);

            orderCancelService.completeOrder(order);
        } catch (Exception e) {
            log.error("주문 ID: {} - 취소 실패: {}", order.getOrderId(), e.getMessage(), e);
            // 오케스트레이터 내부에서 이미 FAILED 처리 및 로깅 되었음.
            // completeOrder 실패 -> 아마 스케줄러나 배치 서버로 처리해야 할듯?
            throw e;
        }
    }
}
