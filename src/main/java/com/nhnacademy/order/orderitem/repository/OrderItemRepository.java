package com.nhnacademy.order.orderitem.repository;

import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.orderitem.domain.OrderItemStatus;
import com.nhnacademy.order.orderitem.dto.OrderItemResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("""
        SELECT new com.nhnacademy.order.orderitem.dto.OrderItemResponse(
            oi.orderItemId,
            oi.order.orderId,
            oi.bookId,
            oi.bookName,
            oi.bookImage,
            oi.quantity,
            coalesce(oi.price, 0),
            (coalesce(oi.price, 0) + coalesce(oi.packagingInfo.packagingPrice, 0)) * oi.quantity,
            coalesce(oi.couponDiscountAmount, 0),
            ((coalesce(oi.price, 0) + coalesce(oi.packagingInfo.packagingPrice, 0)) * oi.quantity - coalesce(oi.couponDiscountAmount, 0)),
            coalesce(oi.packagingInfo.packagingPrice, 0),
            coalesce(oi.refundPrice, 0),
            oi.orderItemStatus
        )
        FROM OrderItem oi
        WHERE oi.order.orderId = :orderId
    """)
    List<OrderItemResponse> findOrderItemByOrder_OrderId(@Param("orderId") Long orderId);

    @Query("""
        SELECT new com.nhnacademy.order.orderitem.dto.OrderItemResponse(
            oi.orderItemId,
            oi.order.orderId,
            oi.bookId,
            oi.bookName,
            oi.bookImage,
            oi.quantity,
            coalesce(oi.price, 0),
            (coalesce(oi.price, 0) + coalesce(oi.packagingInfo.packagingPrice, 0)) * oi.quantity,
            coalesce(oi.couponDiscountAmount, 0),
            ((coalesce(oi.price, 0) + coalesce(oi.packagingInfo.packagingPrice, 0)) * oi.quantity - coalesce(oi.couponDiscountAmount, 0)),
            coalesce(oi.packagingInfo.packagingPrice, 0),
            coalesce(oi.refundPrice, 0),
            oi.orderItemStatus
        )
        FROM OrderItem oi
        WHERE oi.order.orderId IN :orderIds
    """)
    List<OrderItemResponse> findAllByOrderIds(@Param("orderIds") List<Long> orderIds);


    @Query("""
        SELECT oi.bookId
        FROM OrderItem oi
        WHERE oi.orderItemStatus NOT IN :excludeStatuses AND oi.quantity > 0
        GROUP BY oi.bookId
        ORDER BY SUM(oi.quantity) DESC
    """)
    List<Long> findTopNSellingBookIds(@Param("excludeStatuses") List<OrderItemStatus> excludeStatuses, Pageable pageable);

    @Query("""
        SELECT new com.nhnacademy.order.orderitem.dto.OrderItemResponse(
            oi.orderItemId,
            oi.order.orderId,
            oi.bookId,
            oi.bookName,
            oi.bookImage,
            oi.quantity,
            coalesce(oi.price, 0),
            (coalesce(oi.price, 0) + coalesce(oi.packagingInfo.packagingPrice, 0)) * oi.quantity,
            coalesce(oi.couponDiscountAmount, 0),
            ((coalesce(oi.price, 0) + coalesce(oi.packagingInfo.packagingPrice, 0)) * oi.quantity - coalesce(oi.couponDiscountAmount, 0)),
            coalesce(oi.packagingInfo.packagingPrice, 0),
            coalesce(oi.refundPrice, 0),
            oi.orderItemStatus
        )
        FROM OrderItem oi
        JOIN oi.order o
        WHERE o.memberId = :memberId
        AND oi.orderItemStatus IN :statuses
    """)
    Page<OrderItemResponse> findAllByOrder_MemberIdAndOrderItemStatusIn(@Param("memberId") Long memberId, @Param("statuses") List<OrderItemStatus> statuses, Pageable pageable);
}
