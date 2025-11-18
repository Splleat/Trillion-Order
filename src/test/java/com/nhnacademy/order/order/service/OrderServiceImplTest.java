package com.nhnacademy.order.order.service;

import com.nhnacademy.order.delivery.repository.DeliveryPolicyRepository;
import com.nhnacademy.order.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {
    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DeliveryPolicyRepository deliveryPolicyRepository;

    @Test
    @DisplayName("주문 생성 - 성공")
    void createOrder_Success() {

    }

    @Test
    @DisplayName("주문 생성 - 실패: 재고 부족")
    void createOrder_Failure_InsufficientStock() {

    }

    @Test
    @DisplayName("주문 생성 - 실패: 쿠폰 사용 불가")
    void createOrder_Failure_InvalidCoupon() {

    }

    @Test
    @DisplayName("주문 생성 - 실패: 포인트 부족")
    void createOrder_Failure_InsufficientPoints() {

    }

    @Test
    @DisplayName("주문 전체 조회 (관리자) - 성공")
    void findAllOrders_Success() {

    }

    @Test
    @DisplayName("주문 전체 조회 - 실패: 관리자 권한 없음")
    void findAllOrders_Failure_NoAdminRights() {

    }

    @Test
    @DisplayName("주문 전체 조회 (회원) - 성공")
    void findAllOrdersByMemberId_Success() {

    }

    @Test
    @DisplayName("주문 전체 조회 (회원) - 실패: 회원 아이디 없음")
    void findAllOrdersByMemberId_Failure_NoMemberId() {

    }

    @Test
    @DisplayName("주문 단건 조회 (회원) - 성공")
    void findOrderByMemberId_Success() {

    }

    @Test
    @DisplayName("주문 단건 조회 (회원) - 실패: 회원 아이디 불일치")
    void findOrderByMemberId_Failure_MemberIdMismatch() {

    }

    @Test
    @DisplayName("비회원 주문 조회 - 성공")
    void findOrderByNonMember_Success() {

    }

    @Test
    @DisplayName("비회원 주문 조회 - 실패: 주문번호 불일치")
    void findOrderByNonMember_Failure_OrderNumberMisMatch() {

    }

    @Test
    @DisplayName("비회원 주문 조회 - 실패: 주문 비밀번호 불일치")
    void findOrderByNonMember_Failure_OrderPasswordMisMatch() {

    }

    @Test
    @DisplayName("배송중으로 상태 변경 - 성공 ")
    void patchOrderItemShipped_Success() {

    }

    @Test
    @DisplayName("배송중으로 상태 변경 - 실패: 관리자 권한 없음")
    void patchOrderItemShipped_Failure_NoAdmin() {

    }

    @Test
    @DisplayName("주문 취소 - 성공")
    void patchOrderItemCancel_Success() {

    }

    @Test
    @DisplayName("주문 취소 - 실패: 주문 취소가 불가능한 상태")
    void patchOrderItemCancel_Failure_ConflictStatus() {

    }

    @Test
    @DisplayName("환불 요청 - 성공")
    void patchOrderItemReturnRequested_Success() {

    }

    @Test
    @DisplayName("환불 요청 - 실패: 환불 요청이 불가능한 상태")
    void patchOrderItemReturnRequested_Failure_ConflictStatus() {

    }

    @Test
    @DisplayName("환불 요청 - 실패: 출고일로부터 10일 경과")
    void patchOrderItemReturnRequested_Failure_Timeout() {

    }
}