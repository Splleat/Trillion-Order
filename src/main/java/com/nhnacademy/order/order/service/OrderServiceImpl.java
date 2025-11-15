package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderCreateRequest;
import com.nhnacademy.order.order.dto.OrderResponse;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.order.service.strategy.OrderItemStatusUpdateStrategy;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.dto.OrderItemStatusPatchRequest;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional
    public Long createOrder(Long memberId, OrderCreateRequest request) {
        String orderNumber = UUID.randomUUID().toString();

        OrdererInfo ordererInfo = new OrdererInfo( request.ordererName(), request.ordererContact());
        ReceiverInfo receiverInfo = new ReceiverInfo(request.receiverName(), request.receiverContact(), request.receiverAddress());
        OrderDetails orderDetails = new OrderDetails(
            LocalDateTime.now(),
            null, // 출고일 - 관리자 페이지에서
            request.receiverPostCode(),
            null, // 배송일 - 관리자 페이지에서
            5000, // 배송비 - 일단 하드코딩
            request.pointUsage(),
            request.getTotalPrice()
        );

        Orders order = Orders.create(
            orderNumber,
            memberId,
            request.nonMemberPassword(),
            ordererInfo,
            receiverInfo,
            orderDetails
        );

        for (OrderItemCreateRequest itemCreateRequest : request.orderItems()) {
            OrderItem orderItem = OrderItem.create(
                order,
                itemCreateRequest.bookId(),
                itemCreateRequest.quantity(),
                itemCreateRequest.price(),
                itemCreateRequest.couponId(),
                itemCreateRequest.packagingId()
            );

            order.addOrderItem(orderItem);
        }

        Orders savedOrder = orderRepository.save(order);

        return savedOrder.getOrderId();
    }

    @Override
    public OrderResponse findOrderByOrderId(Long orderId) {
        Optional<OrderBaseResponse> orderBaseResponseOptional = orderRepository.findBaseOrderById(orderId);

        return orderBaseResponseOptional.map( orderBaseResponse -> {
            List<OrderItemResponse> orderItems = orderItemRepository.findOrderItemByOrder_OrderId(orderId);

            return OrderResponse.create(orderBaseResponse, orderItems);
        }).orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문: " + orderId));
    }

    @Override
    public List<OrderResponse> findAllOrderByMemberId(Long memberId) {
        List<OrderBaseResponse> orderBaseResponses = orderRepository.findAllBaseOrderByMemberId(memberId);

        List<Long> orderIds = orderBaseResponses.stream()
                .map(OrderBaseResponse::orderId)
                .toList();

        Map<Long, List<OrderItemResponse>> orderItemResponses = orderItemRepository.findAllByOrderIds(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItemResponse::orderId));

        return orderBaseResponses.stream()
                .map(orderBaseResponse -> OrderResponse.create(orderBaseResponse, orderItemResponses.getOrDefault(orderBaseResponse.orderId(), Collections.emptyList())))
                .toList();
    }

    @Override
    @Transactional
    public void patchOrderItemStatus(Long memberId, Long orderId, Long orderItemId, OrderItemStatusPatchRequest request) {
        Orders order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("존재하지 않는 주문: " + orderId));

        if (!order.getMemberId().equals(memberId)) {
            // TODO: 예외 발생!
        }

        OrderItemStatusUpdateStrategy strategy = OrderItemStatusUpdateStrategy.from(request.status());

        strategy.updateStatus(order, orderItemId);
    }
}
