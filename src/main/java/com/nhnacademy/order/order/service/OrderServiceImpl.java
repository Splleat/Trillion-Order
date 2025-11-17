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
import com.nhnacademy.order.order.exception.OrderAccessDeniedException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.order.service.strategy.OrderItemStatusUpdateStrategy;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
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

    @Override
    public Page<OrderResponse> findAllOrders(Pageable pageable) {
        // TODO: 관리자 권한 체크

        Page<Orders> orders = orderRepository.findAll(pageable);

        return orders.map(order -> {
            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(order.getOrderId());

            return OrderResponse.create(
                order,
                orderItems
            );
        });
    }

    @Override
    @Transactional
    public Long createOrder(Long memberId, OrderCreateRequest request) {
        DeliveryPolicy deliveryPolicy = deliveryPolicyRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new PolicyNotConfiguredException("배송 정책이 설정되지 않음"));

        int deliveryFee = (request.getTotalPrice() >= deliveryPolicy.getFreeDeliveryThreshold())
                ? 0
                : deliveryPolicy.getFee();

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
            request.getTotalPrice()
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

        request.orderItems().stream()
                .map(orderItem -> OrderItem.create(
                    order,
                    orderItem.bookId(),
                    orderItem.quantity(),
                    orderItem.price(),
                    orderItem.couponId(),
                    orderItem.packagingId()
                ))
                .forEach(order::addOrderItem);

        Orders savedOrder = orderRepository.save(order);

        return savedOrder.getOrderId();
    }

    @Override
    public OrderResponse findOrderByCustomer(Long memberId, Long orderId) {
        Optional<OrderBaseResponse> orderBaseResponseOptional = orderRepository.findBaseOrderById(orderId);

        return orderBaseResponseOptional.map( orderBaseResponse -> {
            if (!orderBaseResponse.memberId().equals(memberId)) {
                throw new OrderAccessDeniedException("주문 접근 권한 없음: " + orderId);
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
            throw new OrderAccessDeniedException("주문 접근 권한 없음: " + orderId);
        }

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(request.status());

        strategy.updateStatus(order, orderItemId);
    }

    @Override
    public OrderResponse findOrderByOrderNumber(String orderNumber, String nonMemberPassword) {
        Optional<Orders> orderOptional = orderRepository.findByOrderNumber(orderNumber);

        return orderOptional.map(order -> {
            if (!passwordEncoder.matches(nonMemberPassword, order.getNonMemberPassword())) {
                throw new OrderPasswordMismatchException("비회원 주문 비밀번호 불일치: " + orderNumber);
            }

            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(order.getOrderId());

            return OrderResponse.create(
                order,
                orderItems
            );
        }).orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문 번호: " + orderNumber));
    }
}
