package com.nhnacademy.order.order.service;

import com.nhnacademy.order.order.domain.*;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.PackagingInfo;
import com.nhnacademy.order.orderitem.dto.OrderItemCreateRequest;
import com.nhnacademy.order.packaging.domain.Packaging;
import com.nhnacademy.order.packaging.repository.PackagingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderInitialCreateService {
    // Repository
    private final OrderRepository orderRepository;
    private final PackagingRepository packagingRepository;

    // 초기 주문 생성 - 보상 트랜잭션 도중 서버 종료 시 필요함
    @Transactional
    public Order createInitialOrder(Long memberId, String nonMemberPassword, OrdererInfo ordererInfo, ReceiverInfo receiverInfo, OrderDetails initialOrderDetails, OrderCoupon initialOrderCoupon, List<OrderItemCreateRequest> itemCreateRequests) {
        Order order = Order.createInitial(
                memberId,
                nonMemberPassword,
                ordererInfo,
                receiverInfo,
                initialOrderDetails
        );

        // 초기 쿠폰 생성
        if (initialOrderCoupon != null) {
            order.addOrderCoupon(initialOrderCoupon);
        }

        // 초기 주문 상품 목록 생성
        List<Long> orderPackagingIds = itemCreateRequests.stream()
                .map(OrderItemCreateRequest::packagingId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, PackagingInfo> packagingInfoMap = packagingRepository.findAllById(orderPackagingIds).stream()
                .collect(Collectors.toMap(Packaging::getPackagingId,
                        packaging -> PackagingInfo.create(packaging.getPackagingType(), packaging.getPackagingPrice())));

        itemCreateRequests.stream()
                .map(request ->
                        OrderItem.createInitial(order, request.bookId(), request.quantity(), request.shippingDate(), packagingInfoMap.get(request.packagingId())))
                .forEach(order::addOrderItem);

        return orderRepository.save(order);
    }
}
