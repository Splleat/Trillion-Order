package com.nhnacademy.order.order.service;

import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.exception.PolicyNotConfiguredException;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.exception.OrderPasswordMismatchException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.order.service.strategy.OrderItemStatusUpdateStrategy;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.dto.NonMemberOrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.packaging.exception.PackagingNotFoundException;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeliveryPolicyRepository deliveryPolicyRepository;
    private final PackagingRepository packagingRepository;

    @Override
    public Page<OrderResponse> findAllOrders(Pageable pageable) {
        Page<Orders> orders = orderRepository.findAll(pageable);

        return orders.map(order -> {
            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(order.getOrderId());

            return OrderResponse.create(
                order,
                orderItems
            );
        });
    }

    @Transactional
    public Long createOrder(Long memberId, OrderCreateRequest request) {

        // TODO 1: 도서 API -> 재고 확인 및 차감, 가격 검증
        // TODO 2: 쿠폰 API -> 쿠폰 사용 처리
        // TODO 3: 포인트 API -> 포인트 사용 처리
        // TODO 4: 최종 결제 정보 생성

        // 1. OrderItemCreateRequest -> OrderItem (연관 관계 매핑은 아직 안 함)
        List<OrderItem> orderItems = request.orderItems().stream()
                .map(itemReq -> {
                    int packagingPrice = 0;
                    if (itemReq.packagingId() != null) {
                        packagingPrice = packagingRepository.findById(itemReq.packagingId())
                                .orElseThrow(() -> new PackagingNotFoundException("포장 정보를 찾을 수 없음: " + itemReq.packagingId()))
                                .getPackagingPrice();
                    }
                    return OrderItem.create(null, itemReq.bookId(), itemReq.quantity(), itemReq.price(), packagingPrice, itemReq.couponId());
                })
                .toList();

        // 2. 포장비 총합 계산
        int totalPackagingPrice = orderItems.stream().mapToInt(OrderItem::getPackagingPrice).sum();

        // 3. 최종 주문 비용 계산
        int finalTotalPrice = request.getTotalPrice() + totalPackagingPrice;

        // 4. 배송비 결정
        DeliveryPolicy deliveryPolicy = deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()
                .orElseThrow(() -> new PolicyNotConfiguredException("배송 정책이 설정되지 않음"));

        int deliveryFee = (finalTotalPrice >= deliveryPolicy.getDeliveryPolicyThreshold())
                ? 0
                : deliveryPolicy.getDeliveryPolicyFee();
        // 5. Order 객체 생성
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
                finalTotalPrice
        );

        Orders order = Orders.create(
                memberId,
                Optional.ofNullable(request.nonMemberPassword())
                        .map(passwordEncoder::encode)
                        .orElse(null),
                ordererInfo,
                receiverInfo,
                orderDetails
        );

        // 6. OrderItem 리스트를 Order에 추가
        orderItems.forEach(order::addOrderItem);

        Orders savedOrder = orderRepository.save(order);

        return savedOrder.getOrderId();
    }

    @Override
    public OrderResponse findOrderByCustomer(Long memberId, Long orderId) {
        Optional<OrderBaseResponse> orderBaseResponseOptional = orderRepository.findBaseOrderById(orderId);

        return orderBaseResponseOptional.map( orderBaseResponse -> {
            if (!orderBaseResponse.memberId().equals(memberId)) {
                throw new AccessDeniedException("주문 접근 권한 없음: " + orderId);
            }

            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(orderId);

            return OrderResponse.create(orderBaseResponse, orderItems);
        }).orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문 ID: " + orderId));
    }

    @Override
    public OrderResponse findOrderByOrderId(Long orderId) {
        Optional<OrderBaseResponse> orderBaseResponseOptional = orderRepository.findBaseOrderById(orderId);

        return orderBaseResponseOptional.map( orderBaseResponse -> {
            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(orderId);

            return OrderResponse.create(orderBaseResponse, orderItems);
        }).orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문 ID: " + orderId));
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
        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문 ID: " + orderId));

        // TODO: 관리자 권한도 고려해야 함
        // 반품 요청, 주문 취소 -> 회원 / 비회원 가능
        // 배송중, 배송 완료, 반품 완료 -> 관리자만 가능
        if (!order.getMemberId().equals(memberId)) {
            throw new AccessDeniedException("주문 접근 권한 없음: " + orderId);
        }

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(request.status());

        strategy.updateStatus(order, orderItemId);
    }

    @Override
    @Transactional
    public void patchOrderItemStatusForNonMember(Long orderId, Long orderItemId, NonMemberOrderItemStatusPatchRequest request) {

        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문 ID: " + orderId));

        if (!passwordEncoder.matches(request.nonMemberPassword(), order.getNonMemberPassword())) {
            throw new OrderPasswordMismatchException("비회원 주문 비밀번호 불일치");
        }

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(request.status());

        strategy.updateStatus(order, orderItemId);
    }

    @Override
    public OrderResponse findOrderByOrderNumber(String orderNumber, String nonMemberPassword) {
        Optional<Orders> orderOptional = orderRepository.findByOrderNumber(orderNumber);

        return orderOptional.map(order -> {
            if (!passwordEncoder.matches(nonMemberPassword, order.getNonMemberPassword())) {
                throw new OrderPasswordMismatchException("비회원 주문 비밀번호 불일치");
            }

            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(order.getOrderId());

            return OrderResponse.create(
                order,
                orderItems
            );
        }).orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문 번호: " + orderNumber));
    }
}
