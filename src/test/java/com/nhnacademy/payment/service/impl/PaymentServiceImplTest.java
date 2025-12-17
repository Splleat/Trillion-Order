package com.nhnacademy.payment.service.impl;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderDetails;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.response.PaymentApiResponse;
import com.nhnacademy.payment.entity.Payment;
import com.nhnacademy.payment.entity.PaymentProvider;
import com.nhnacademy.payment.entity.PaymentStatus;
import com.nhnacademy.payment.exception.PaymentAlreadyCanceledException;
import com.nhnacademy.payment.exception.PaymentNotFoundException;
import com.nhnacademy.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    private Order order;
    private PaymentApiResponse response;

    private PaymentUser memberUser;
    private PaymentUser guestUser;
    private PaymentUser otherMemberUser;

    @BeforeEach
    void setup(){
        // Order 설정
        order = new Order();
        ReflectionTestUtils.setField(order, "orderId", 1L);
        ReflectionTestUtils.setField(order, "orderNumber", "testOrderNumber");
        ReflectionTestUtils.setField(order, "memberId", 1L); // 기본: 회원 1
        order.setOrderStatus(OrderStatus.PENDING);

        OrderDetails mockOrderDetails = mock(OrderDetails.class);
        lenient().when(mockOrderDetails.totalPrice()).thenReturn(50000);
        ReflectionTestUtils.setField(order, "orderDetails", mockOrderDetails);

        // [수정] PaymentApiResponse 생성 (Record + Builder 패턴 사용)
        response = PaymentApiResponse.builder()
                .paymentKey("test_paymentKey")
                .orderId("testOrderNumber")
                .totalAmount(50000)
                .status("DONE")
                .requestedAt("2024-11-25T10:00:00+09:00")
                .approvedAt("2024-11-25T10:00:05+09:00")
                .receiptUrl("http://localhost/receipt")
                .provider("TOSS")
                .build();

        // Mock Users
        memberUser = mock(PaymentUser.class);
        lenient().when(memberUser.memberId()).thenReturn(1L);
        lenient().when(memberUser.isMember()).thenReturn(true);

        otherMemberUser = mock(PaymentUser.class);
        lenient().when(otherMemberUser.memberId()).thenReturn(2L);
        lenient().when(otherMemberUser.isMember()).thenReturn(true);

        guestUser = mock(PaymentUser.class);
        lenient().when(guestUser.memberId()).thenReturn(null);
        lenient().when(guestUser.isMember()).thenReturn(false);
    }

    @Test
    @DisplayName("결제 저장 테스트 - 승인 성공시")
    void savePaymentSuccess(){
        // given
        given(paymentRepository.save(any(Payment.class))).willAnswer(
                invocationOnMock -> {
                    Payment payment = invocationOnMock.getArgument(0);
                    ReflectionTestUtils.setField(payment,"paymentId",1L);
                    return payment;
                }
        );

        // when
        Payment result = paymentService.savePayment(response, order);

        // then
        assertNotNull(result);
        assertEquals(PaymentStatus.DONE, result.getPaymentStatus());
        assertEquals(50000, result.getTotalAmount());
        assertEquals(50000, result.getBalanceAmount());
        assertEquals(PaymentProvider.TOSS, result.getProvider());

        // 주문 상태 변경 확인
        assertEquals(OrderStatus.COMPLETED, order.getOrderStatus());

        // [확인] savePayment 내부 호출 검증
        // 구현부에서 save를 2번 호출하는 경우 times(2)로 변경하거나, 최소 1번 호출 검증을 위해 atLeastOnce() 사용
        verify(paymentRepository, atLeastOnce()).save(any(Payment.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    @DisplayName("결제 취소 상태 업데이트 - 성공")
    void updateCanceledStatus(){
        Long paymentId = 1L;
        Integer cancelAmount = 50000;

        Payment mockPayment = mock(Payment.class);
        given(mockPayment.getPaymentId()).willReturn(paymentId);

        Payment findPayment = Payment.builder()
                .paymentKey("test_paymentKey")
                .paymentStatus(PaymentStatus.DONE)
                .order(order)
                .totalAmount(50000)
                .build();

        ReflectionTestUtils.setField(findPayment, "paymentId", paymentId);

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(findPayment));

        // when
        paymentService.updatePaymentCanceledStatus(mockPayment, cancelAmount);

        // then
        assertEquals(PaymentStatus.CANCELED, findPayment.getPaymentStatus());
        assertEquals(0, findPayment.getBalanceAmount());
        assertEquals(OrderStatus.CANCELED, order.getOrderStatus());
    }

    @Test
    @DisplayName("결제 취소 상태 업데이트 - 실패(이미 취소 처리된 결제 정보)")
    void updatedCanceledStatus_Fail2(){
        Long paymentId = 1L;
        Integer cancelAmount = 50000;
        Payment mockPayment = mock(Payment.class);
        given(mockPayment.getPaymentId()).willReturn(paymentId);

        Payment findPayment = Payment.builder()
                .paymentKey("test_paymentKey")
                .paymentStatus(PaymentStatus.CANCELED) // 이미 취소됨
                .order(order)
                .build();

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(findPayment));

        // when & then
        assertThrows(PaymentAlreadyCanceledException.class,
                () -> paymentService.updatePaymentCanceledStatus(mockPayment, cancelAmount));
    }

    @Test
    @DisplayName("결제 취소 상태 업데이트 - 실패(없는 결제 정보)")
    void updateCanceledStatus_Fail(){
        Long paymentId = 1L;
        Integer cancelAmount = 50000;

        Payment mockPayment = mock(Payment.class);
        given(mockPayment.getPaymentId()).willReturn(paymentId);

        given(paymentRepository.findById(mockPayment.getPaymentId())).willReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () ->
                paymentService.updatePaymentCanceledStatus(mockPayment, cancelAmount));
    }

    @Test
    @DisplayName("주문 번호로 결제 내역 조회 - 실패(조회 내역 없음)")
    void getPaymentByOrderNumberFail(){
        String orderNumber = "non-existNumber";
        // [수정] Optional 반환 메서드이므로 null이 아닌 Optional.empty() 반환
        given(paymentRepository.findByOrder_OrderNumber(orderNumber)).willReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> paymentService.getPaymentByOrderNumber(orderNumber));
    }

    @Test
    @DisplayName("결제 ID로 조회 - 실패(결제 내역 정보 없음)")
    public void getPaymentByPaymentIdFail(){
        Long invalidPaymentId = 999L;
        given(paymentRepository.findById(invalidPaymentId)).willReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () ->
                paymentService.getPaymentById(invalidPaymentId));
    }

    @Test
    @DisplayName("주문 번호로 결제 내역 조회 - 성공")
    public void getPaymentByOrderNumberSuccess(){
        String orderNumber = "testOrderNumber";
        Payment payment = Payment.builder()
                .paymentKey("test_paymentKey")
                .paymentStatus(PaymentStatus.DONE)
                .order(order)
                .build();
        given(paymentRepository.findByOrder_OrderNumber(orderNumber)).willReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentByOrderNumber(orderNumber);
        assertNotNull(result);
        assertEquals("test_paymentKey", result.getPaymentKey());
    }

    @Test
    @DisplayName("결제 ID로 조회 - 성공")
    public void getPaymentByPaymentIdSuccess(){
        Long paymentId = 1L;

        Payment payment = Payment.builder()
                .paymentKey("test_paymentKey")
                .paymentStatus(PaymentStatus.DONE)
                .order(order)
                .build();

        ReflectionTestUtils.setField(payment,"paymentId",paymentId);

        given(paymentRepository.findById(paymentId)).willReturn(Optional.of(payment));

        Payment result = paymentService.getPaymentById(paymentId);
        assertNotNull(result);
        assertEquals("test_paymentKey", result.getPaymentKey());
        assertEquals(1L, result.getPaymentId());
    }

    @Test
    @DisplayName("결제 내역 전체 조회 - 성공")
    void getAllPaymentsSuccess() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        Payment payment = Payment.builder()
                .paymentKey("test_paymentKey")
                .paymentStatus(PaymentStatus.DONE)
                .order(order)
                .totalAmount(50000)
                .build();
        ReflectionTestUtils.setField(payment, "paymentId", 1L);

        Page<Payment> paymentPage = new PageImpl<>(List.of(payment));

        given(paymentRepository.findAll(pageable)).willReturn(paymentPage);

        // when
        Page<Payment> result = paymentService.getAllPayments(pageable);

        // then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("test_paymentKey", result.getContent().get(0).getPaymentKey());

        verify(paymentRepository, times(1)).findAll(pageable);
    }

    // ==========================================
    // [추가] getMemberPaymentByOrderNumber 테스트 (회원/비회원 권한 검증)
    // ==========================================

    @Test
    @DisplayName("회원 결제 내역 조회 - 성공 (본인)")
    void getMemberPaymentByOrderNumber_Success_Member() {
        // given
        Payment payment = Payment.builder().order(order).build();

        given(paymentRepository.findByOrder_OrderNumberAndOrder_MemberId("testOrderNumber", 1L))
                .willReturn(Optional.of(payment));

        // when
        Payment result = paymentService.getMemberPaymentByOrderNumber(memberUser, "testOrderNumber");

        // then
        assertNotNull(result);
        assertEquals(order, result.getOrder());
    }

    @Test
    @DisplayName("회원 결제 내역 조회 - 실패 (타인의 주문)")
    void getMemberPaymentByOrderNumber_Fail_OtherMember() {
        // given
        given(paymentRepository.findByOrder_OrderNumberAndOrder_MemberId("testOrderNumber", 2L))
                .willReturn(Optional.empty());

        // when & then
        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.getMemberPaymentByOrderNumber(otherMemberUser, "testOrderNumber"));
    }

    @Test
    @DisplayName("비회원 결제 내역 조회 - 성공 (비회원 주문)")
    void getMemberPaymentByOrderNumber_Success_Guest() {
        // given
        ReflectionTestUtils.setField(order, "memberId", null); // 비회원 주문으로 변경
        Payment payment = Payment.builder().order(order).build();

        given(paymentRepository.findByOrder_OrderNumber("testOrderNumber"))
                .willReturn(Optional.of(payment));

        // when
        Payment result = paymentService.getMemberPaymentByOrderNumber(guestUser, "testOrderNumber");

        // then
        assertNotNull(result);
    }

    @Test
    @DisplayName("비회원 결제 내역 조회 - 실패 (비회원이 회원 주문에 접근)")
    void getMemberPaymentByOrderNumber_Fail_GuestAccessMember() {
        // given
        // order는 setUp에서 memberId=1L로 설정되어 있음 (회원주문)
        Payment payment = Payment.builder().order(order).build();

        given(paymentRepository.findByOrder_OrderNumber("testOrderNumber"))
                .willReturn(Optional.of(payment));

        // when & then
        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.getMemberPaymentByOrderNumber(guestUser, "testOrderNumber"),
                "비회원은 회원의 결제 정보를 조회할 수 없습니다.");
    }

    @Test
    @DisplayName("회원 결제 내역 전체 조회 - 성공")
    void getAllMemberPayments_Success(){
        Long memberId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        Payment payment = Payment.builder()
                .paymentKey("test_paymentKey")
                .order(order) // setUp에서 만든 order (memberId=1L)
                .totalAmount(50000)
                .build();

        Page<Payment> expectedPage = new PageImpl<>(List.of(payment));

        given(paymentRepository.findByOrder_MemberId(memberId, pageable))
                .willReturn(expectedPage);

        // when
        Page<Payment> result = paymentService.getAllMemberPayments(memberId, pageable);

        // then
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(payment, result.getContent().get(0));

        verify(paymentRepository, times(1)).findByOrder_MemberId(memberId, pageable);
    }

    @Test
    @DisplayName("회원 결제 내역 전체 조회 - 실패 (MemberId Null)")
    void getAllMemberPayments_NullMemberId() {
        // given
        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<Payment> result = paymentService.getAllMemberPayments(null, pageable);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty()); // 빈 페이지 반환 확인

        // Repository가 호출되지 않아야 함
        verify(paymentRepository, never()).findByOrder_MemberId(any(), any());
    }
}