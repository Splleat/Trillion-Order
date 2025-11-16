package com.nhnacademy.order.order.repository;

import com.nhnacademy.order.order.domain.Orders;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Orders, Long> {
    @Query("""
        SELECT new com.nhnacademy.order.order.dto.OrderBaseResponse(
            o.orderId,
            o.memberId,
            o.orderNumber,
            o.orderDetails.orderDate,
            o.paymentStatus,
            o.orderDetails.totalPrice,
            o.orderDetails.deliveryFee,
            o.ordererInfo,
            o.receiverInfo
        )
        FROM Orders o
        WHERE o.orderId = :orderId
    """)
    Optional<OrderBaseResponse> findBaseOrderById(Long orderId);

    @Query("""
        SELECT new com.nhnacademy.order.order.dto.OrderBaseResponse(
            o.orderId,
            o.memberId,
            o.orderNumber,
            o.orderDetails.orderDate,
            o.paymentStatus,
            o.orderDetails.totalPrice,
            o.orderDetails.deliveryFee,
            o.ordererInfo,
            o.receiverInfo
        )
        FROM Orders o
        WHERE o.memberId = :memberId
    """)
    List<OrderBaseResponse> findAllBaseOrderByMemberId(Long memberId);

    Optional<Orders> findByOrderNumber(String orderNumber);
}
