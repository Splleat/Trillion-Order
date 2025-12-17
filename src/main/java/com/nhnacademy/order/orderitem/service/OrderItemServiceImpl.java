package com.nhnacademy.order.orderitem.service;

import com.nhnacademy.order.common.aop.AuthRole;
import com.nhnacademy.order.common.aop.CheckAuth;
import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import com.nhnacademy.order.orderitem.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderItemServiceImpl implements OrderItemService {
    private final OrderItemRepository orderItemRepository;

    @Override
    public List<Long> getTopNSellingBookIds(int limit) {
        List<OrderItemStatus> excludeStatuses = List.of(OrderItemStatus.CANCELED, OrderItemStatus.RETURNED);

        // Pageable을 사용하여 효율적인 쿼리 수행
        Pageable topN = PageRequest.of(0, limit);

        return orderItemRepository.findTopNSellingBookIds(excludeStatuses, topN);
    }

    @Override
    @CheckAuth(role = AuthRole.MEMBER)
    @Transactional(readOnly = true)
    public Page<OrderItemResponse> findRefundedOrderItemsByMemberId(UserInfo userInfo, Pageable pageable) {
        List<OrderItemStatus> refundedStatuses = List.of(
                OrderItemStatus.RETURNED,
                OrderItemStatus.RETURN_REQUESTED_CHANGE_OF_MIND,
                OrderItemStatus.RETURN_REQUESTED_DAMAGED
        );
        return orderItemRepository.findAllByOrder_MemberIdAndOrderItemStatusIn(userInfo.userId(), refundedStatuses, pageable);
    }
}
