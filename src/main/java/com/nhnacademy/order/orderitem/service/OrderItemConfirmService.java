package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import com.nhnacademy.order.point.domain.PointAccumulationEvent;
import com.nhnacademy.order.point.repository.PointAccumulationEventRepository;
import com.nhnacademy.order.point.service.PointAccumulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 트랜잭션 분리를 위한 주문 상품 상태 변경 서비스
@Service
@RequiredArgsConstructor
public class OrderItemConfirmService {
    private final OrderItemUpdateService orderItemUpdateService;
    private final PointAccumulationEventRepository pointAccumulationEventRepository;

    @Transactional
    public PointAccumulationEvent confirmOrderItem(OrderItem orderItem) {
        orderItemUpdateService.updateOrderItemStatus(orderItem, OrderItemStatusUpdateStrategy.CONFIRMED);

        Order order = orderItem.getOrder();
        if (order.getMemberId() == null) {
            return null; // 비회원은 포인트 적립 대상이 아님
        }

        Long orderId = order.getOrderId();
        Long orderItemId = orderItem.getOrderItemId();
        Long memberId = order.getMemberId();

        int purchaseAmount = (orderItem.getPrice() * orderItem.getQuantity()) - orderItem.getCouponDiscountAmount();

        PointAccumulationEvent event = PointAccumulationEvent.create(memberId, orderId, orderItemId, purchaseAmount);
        return pointAccumulationEventRepository.save(event);
    }
}
