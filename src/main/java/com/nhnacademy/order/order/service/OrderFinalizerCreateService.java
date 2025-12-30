package com.nhnacademy.order.order.service;

import com.nhnacademy.order.client.book.dto.BookResponse;
import com.nhnacademy.order.client.book.service.BookService;
import com.nhnacademy.order.client.coupon.dto.CouponCalculationResponse;
import com.nhnacademy.order.client.coupon.dto.DiscountBookResponse;
import com.nhnacademy.order.client.coupon.service.CouponService;
import com.nhnacademy.order.delivery.domain.DeliveryPolicy;
import com.nhnacademy.order.delivery.exception.PolicyNotConfiguredException;
import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.order.ordercoupon.domain.OrderCoupon;
import com.nhnacademy.order.orderitem.domain.OrderItem;
import com.nhnacademy.order.ordersaga.creation.domain.OrderCreateSaga;
import com.nhnacademy.order.ordersaga.creation.repository.OrderCreateSagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderFinalizerCreateService {
    private final DeliveryPolicyRepository deliveryPolicyRepository;
    private final BookService bookService;
    private final CouponService couponService;
    private final OrderRepository orderRepository;
    private final OrderCreateSagaRepository orderCreateSagaRepository;

    @Transactional
    public void finalizeOrderCreation(Order order, OrderCreateSaga saga) {
        if (order.getOrderStatus() == OrderStatus.PENDING) {
            return;
        }

        Long memberId = order.getMemberId();
        Set<OrderItem> orderItems = order.getOrderItems();
        List<Long> bookIds = orderItems.stream().map(OrderItem::getBookId).toList();
        Map<Long, BookResponse> bookResponseMap = bookService.getBookInfos(bookIds);

        // 1. OrderItem 정보 완성 (도서 이름, 이미지, 가격, 쿠폰 할인 전 초기화)
        orderItems.forEach(orderItem -> {
            BookResponse bookResponse = bookResponseMap.get(orderItem.getBookId());
            orderItem.completeOrderItem(bookResponse.bookName(), bookResponse.imageUrl(), bookResponse.price(), 0);
        });


        // 2. 원가 계산 (도서 가격 * 수량 + 포장비 * 수량)
        int originPrice = orderItems.stream()
                .mapToInt(item -> (item.getPrice() + item.getPackagingInfo().packagingPrice()) * item.getQuantity())
                .sum();
        OrderDetails currentOrderDetails = order.getOrderDetails();

        // 3. 쿠폰 사용 처리 (현재 비즈니스 로직 상 1개의 쿠폰만 사용 가능)
        Set<OrderCoupon> orderCoupons = order.getOrderCoupons();

        if (memberId != null) {
            orderCoupons.forEach(orderCoupon -> {
                // 3-1. 쿠폰 할인 계산 요청
                List<Long> bookIdsForCoupon = orderItems.stream().map(OrderItem::getBookId).toList();
                List<Long> quantitiesForCoupon = orderItems.stream().map(item -> (long) item.getQuantity()).toList();

                // 3-2. 쿠폰 할인 계산 응답
                CouponCalculationResponse couponResponse = couponService.calculateDiscount(orderCoupon.getCouponId(), memberId, bookIdsForCoupon, quantitiesForCoupon);

                int couponDiscount = couponResponse.totalDiscountPrice();

                // 3-3. OrderCoupon 완성
                orderCoupon.completeOrderCoupon(couponDiscount, couponResponse.targetId());

                // 3-4. 각 OrderItem에 쿠폰 할인 금액 반영
                Map<Long, Integer> itemDiscountMap = couponResponse.discountBooks().stream()
                        .collect(Collectors.toMap(DiscountBookResponse::bookId, response -> response.discountValue().intValue()));
                orderItems.forEach(item -> {
                    int discount = itemDiscountMap.getOrDefault(item.getBookId(), 0);
                    item.setCouponDiscountAmount(discount);
                });
            });
        }

        int totalCouponDiscount = orderItems.stream()
                .mapToInt(OrderItem::getCouponDiscountAmount)
                .sum();

        // 4. 최종 가격 계산
        int pointUsage = currentOrderDetails.pointUsage();

        // 4-1. 포인트 안분 로직 (상품 가격 + 포장비 비율로 분배)
        if (pointUsage > 0 && originPrice > 0) {
            int remainingPoint = pointUsage;
            int processedItemCount = 0;
            int totalItems = orderItems.size();

            // 순서를 보장하기 위해 bookId로 정렬
            List<OrderItem> sortedOrderItems = orderItems.stream()
                    .sorted(java.util.Comparator.comparing(OrderItem::getBookId))
                    .toList();

            for (OrderItem item : sortedOrderItems) {
                processedItemCount++;
                // 포장비 포함한 해당 상품 라인의 총 금액
                int itemTotalPrice = (item.getPrice() + item.getPackagingInfo().packagingPrice()) * item.getQuantity();

                if (processedItemCount == totalItems) {
                    // 마지막 상품에 자투리 포인트 몰아주기
                    item.setPaymentPoint(remainingPoint);
                } else {
                    // (개별금액 * 총포인트) / 총금액 -> long 캐스팅으로 오버플로우 방지
                    int distributedPoint = (int) (((long) itemTotalPrice * pointUsage) / originPrice);
                    item.setPaymentPoint(distributedPoint);
                    remainingPoint -= distributedPoint;
                }
            }
        }

        int deliveryFee = determineDeliveryFee(originPrice - totalCouponDiscount);
        int totalPrice = originPrice - totalCouponDiscount - pointUsage + deliveryFee;
        totalPrice = Math.max(0, totalPrice);

        // 5. 계산된 최종 값들을 Order 및 OrderDetails에 반영
        OrderDetails finalOrderDetails = currentOrderDetails.withCouponDiscount(totalCouponDiscount);
        order.updateOrderDetails(finalOrderDetails);
        order.completeOrder(originPrice, totalPrice, deliveryFee);
        order.setOrderStatus(OrderStatus.PENDING);

        // 6. 변경된 Order 저장 및 사가 브릿징
        orderRepository.save(order);
        saga.setBridged(true);
        orderCreateSagaRepository.save(saga);
    }

    private int determineDeliveryFee(int priceAfterCoupon) {
        DeliveryPolicy deliveryPolicy = deliveryPolicyRepository.findFirstByOrderByDeliveryPolicyIdAsc()
                .orElseThrow(() -> new PolicyNotConfiguredException("배송 정책이 설정되지 않음"));
        return (priceAfterCoupon >= deliveryPolicy.getDeliveryPolicyThreshold()) ? 0 : deliveryPolicy.getDeliveryPolicyFee();
    }
}
