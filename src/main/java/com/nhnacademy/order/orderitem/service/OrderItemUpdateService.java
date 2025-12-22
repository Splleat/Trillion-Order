package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.client.member.service.MemberService;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 트랜잭션 분리를 위한 주문 상품 상태 변경 서비스
@Service
@RequiredArgsConstructor
public class OrderItemUpdateService {
    private final MemberService memberService;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public void updateOrderItemStatus(Order order, OrderItem orderItem, OrderItemStatusUpdateStrategy strategy) {
        strategy.updateStatus(order, orderItem);

        orderItemRepository.save(orderItem);
    }

    // 주문 상품 구매 확정 시 호출될 포인트 적립 메서드
    public void accumulatePoint(Order order, OrderItem orderItem) {
        // 멱등성을 위한 키는 주문 ID와 주문 상품 ID를 기반으로 고유하게 생성함
        String idempotencySource = order.getOrderId() + ":" + orderItem.getOrderItemId();
        UUID idempotencyKey = UUID.nameUUIDFromBytes(idempotencySource.getBytes());

        Long memberId = order.getMemberId();
        Long orderId = order.getOrderId();
        int paymentAmount = orderItem.getPrice() * orderItem.getQuantity() - orderItem.getCouponDiscountAmount();

        memberService.accumulatePoint(idempotencyKey, memberId, orderId, paymentAmount);
    }
}
