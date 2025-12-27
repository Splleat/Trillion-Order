package com.nhnacademy.order.order.repository;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.dto.NonMemberOrderBaseResponse;
import com.nhnacademy.order.order.dto.OrderBaseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    String ORDER_BASE_DTO_CONSTRUCTOR = "new com.nhnacademy.order.order.dto.OrderBaseResponse(" +
            "o.orderId, " +
            "o.memberId, " +
            "o.orderNumber, " +
            "o.orderDetails.orderDate, " +
            "o.orderStatus, " +
            "o.orderDetails.originPrice, " +
            "o.orderDetails.totalPrice, " +
            "o.orderDetails.deliveryFee, " +
            "o.orderDetails.pointUsage, " +
            "o.orderDetails.couponDiscountAmount, " +
            "o.ordererInfo, " +
            "o.receiverInfo" +
            ")";

    @Query("""
        SELECT o.memberId
        FROM Order o
        WHERE o.orderId = :orderId
    """)
    Optional<Long> findMemberIdByOrderId(@Param("orderId") Long orderId);

    @Query("""
        SELECT o
        FROM Order o
        LEFT JOIN FETCH o.orderItems
        LEFT JOIN FETCH o.orderCoupons
        WHERE o.orderId = :orderId
    """)
    Optional<Order> findOrderWithItemsByOrderId(@Param("orderId") Long orderId);

    @Query("""
        SELECT o
        FROM Order o
        JOIN FETCH o.orderItems
        WHERE o.orderNumber = :orderNumber
    """)
    Optional<Order> findOrderWithItemsByOrderNumber(@Param("orderNumber") String orderNumber);

    @Query("SELECT " + ORDER_BASE_DTO_CONSTRUCTOR + " FROM Order o")
    Page<OrderBaseResponse> findAllBaseOrder(Pageable pageable);

    @Query("SELECT " + ORDER_BASE_DTO_CONSTRUCTOR + " FROM Order o WHERE o.orderId = :orderId")
    Optional<OrderBaseResponse> findBaseOrderById(@Param("orderId") Long orderId);

    @Query("SELECT " + ORDER_BASE_DTO_CONSTRUCTOR + " FROM Order o WHERE o.memberId = :memberId")
    Page<OrderBaseResponse> findAllBaseOrderByMemberId(Pageable pageable, @Param("memberId") Long memberId);

    @Query("SELECT " + ORDER_BASE_DTO_CONSTRUCTOR + " FROM Order o WHERE o.memberId = :memberId AND o.orderStatus IN :orderStatuses")
    Page<OrderBaseResponse> findAllBaseOrderByMemberIdAndOrderStatusIn(Pageable pageable, @Param("memberId") Long memberId, @Param("orderStatuses") List<OrderStatus> orderStatuses);

    @Query("""
        SELECT new com.nhnacademy.order.order.dto.NonMemberOrderBaseResponse(
            o.orderId,
            o.nonMemberPassword,
            o.memberId,
            o.orderNumber,
            o.orderDetails.orderDate,
            o.orderStatus,
            o.orderDetails.originPrice,
            o.orderDetails.totalPrice,
            o.orderDetails.deliveryFee,
            o.ordererInfo,
            o.receiverInfo
        )
        FROM Order o
        WHERE o.orderNumber = :orderNumber
    """)
    Optional<NonMemberOrderBaseResponse> findNonMemberOrderByOrderNumber(@Param("orderNumber") String orderNumber);

    @Query("""
        SELECT o
        FROM Order o
        LEFT JOIN FETCH o.orderItems
        LEFT JOIN FETCH o.orderCoupons
        WHERE o.orderStatus = :status AND o.updatedAt < :cutOffTime
    """)
    List<Order> findAllOrderStatusAndUpdatedAtBefore(OrderStatus status, LocalDateTime cutOffTime);
}
