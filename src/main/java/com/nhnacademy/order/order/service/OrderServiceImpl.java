package com.nhnacademy.order.order.service;

import com.nhnacademy.order.client.BookClient;
import com.nhnacademy.order.client.CouponClient;
import com.nhnacademy.order.client.MemberClient;
import com.nhnacademy.order.client.dto.BookResponse;
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
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.packaging.domain.Packaging;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {
    // FeignClient
    private final BookClient bookClient;
    private final MemberClient memberClient;
    private final CouponClient couponClient;

    // Repository
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DeliveryPolicyRepository deliveryPolicyRepository;
    private final PackagingRepository packagingRepository;

    // 비회원 주문 비밀번호 인코딩
    private final PasswordEncoder passwordEncoder;

    private static final String ORDER_NOT_FOUND_MESSAGE = "존재하지 않는 주문 ID: ";

    // List<OrderItemCreateRequest> (DTO) -> List<OrderItem> (Entity)
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

    // 배송비 결정 로직
    private int determineDeliveryFee(int finalTotalPrice) {
        DeliveryPolicy deliveryPolicy = deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()
                .orElseThrow(() -> new PolicyNotConfiguredException("배송 정책이 설정되지 않음"));

        return (finalTotalPrice >= deliveryPolicy.getDeliveryPolicyThreshold())
                ? 0
                : deliveryPolicy.getDeliveryPolicyFee();
    }

    // 전체 주문 조회
    @Override
    public Page<OrderResponse> findAllOrders(Pageable pageable) {
        Page<Order> orders = orderRepository.findAll(pageable);

        return orders.map(OrderResponse::create);
    }

    // 주문 생성
    @Transactional
    public OrderResponse createOrder(Long memberId, OrderCreateRequest request) {

        // 1. 주문 상품 ID 리스트 추출
        List<Long> orderBookIds = request.orderItems().stream()
                .map(OrderItemCreateRequest::bookId)
                .toList();

        // 2. 주문 상품 수량 맵 추출
        Map<Long, Integer> orderQuantityMap = request.orderItems().stream()
                .collect(Collectors.toMap(OrderItemCreateRequest::bookId, OrderItemCreateRequest::quantity));

        // 3. 도서 API에 재고 감소 요청 전송
        bookClient.decreaseStock(orderQuantityMap);

        // 4. 도서 API에서 도서 정보 리스트 받아오기
        List<BookResponse> bookResponseList = bookClient.getOrderBookInfos(orderBookIds);

        // 5. 도서 정보 리스트로 도서 정보 맵 생성
         Map<Long, BookResponse> bookInfoMap = bookResponseList.stream()
                 .collect(Collectors.toMap(BookResponse::bookId, Function.identity()));

        // 6. OrderItemCreateRequest (DTO) -> OrderItem (Entity) (연관 관계 매핑은 아직 안 함)
         List<OrderItem> orderItems = buildOrderItems(request.orderItems(), bookInfoMap);

        // 7. 할인 전 결제 금액 (도서 + 포장비)
        int originPrice = orderItems.stream()
                .mapToInt(orderItem1 -> orderItem1.getPrice() + orderItem1.getPackagingPrice())
                .sum();

        int finalTotalPrice = originPrice;

        // TODO: 쿠폰 적용

        // 8. 배송비 결정
        int deliveryFee = determineDeliveryFee(finalTotalPrice);

        finalTotalPrice += deliveryFee;

        // 9. 멤버 API에서 포인트 사용 처리
        int pointUsage = request.pointUsage();
        memberClient.usePoint(pointUsage);

        finalTotalPrice -= pointUsage;

        // 10. Order 객체 생성
        OrdererInfo ordererInfo = new OrdererInfo(
                request.ordererName(),
                request.ordererContact()
        );

        ReceiverInfo receiverInfo = new ReceiverInfo(
                request.receiverName(),
                request.receiverContact(),
                request.receiverAddress()
        );

        OrderDetails orderDetails = OrderDetails.create(
                request.receiverPostCode(),
                request.deliveryDate(),
                deliveryFee,
                request.pointUsage(),
                originPrice,
                finalTotalPrice,
                request.couponId()
        );

        Order order = Order.create(
                memberId,
                Optional.ofNullable(request.nonMemberPassword())
                        .map(passwordEncoder::encode)
                        .orElse(null),
                ordererInfo,
                receiverInfo,
                orderDetails
        );

        orderItems.forEach(order::addOrderItem);

        Order savedOrder = orderRepository.save(order);

        return OrderResponse.create(savedOrder);
    }

    @Override
    public OrderResponse findOrderByOrderId(Long orderId) {
        Optional<OrderBaseResponse> orderBaseResponseOptional = orderRepository.findBaseOrderById(orderId);

        return orderBaseResponseOptional.map( orderBaseResponse -> {
            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(orderId);

            return OrderResponse.create(orderBaseResponse, orderItems);
        }).orElseThrow(() -> new OrderNotFoundException(ORDER_NOT_FOUND_MESSAGE + orderId));
    }

    @Override
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

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(request.status());

        strategy.updateStatus(order, orderItemId);
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
}
