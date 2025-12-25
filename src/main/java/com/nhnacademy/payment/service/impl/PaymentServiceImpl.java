package com.nhnacademy.payment.service.impl;


import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.service.EmailService;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.dto.response.PaymentApiResponse;
import com.nhnacademy.payment.entity.Payment;
import com.nhnacademy.payment.entity.PaymentProvider;
import com.nhnacademy.payment.entity.PaymentStatus;
import com.nhnacademy.payment.exception.PaymentAlreadyCanceledException;
import com.nhnacademy.payment.exception.PaymentNotFoundException;
import com.nhnacademy.payment.repository.PaymentRepository;
import com.nhnacademy.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final EmailService emailService;

    //DB에 담는 시점에서만 Transactional 호출하면 됨.
    @Override
    @Transactional
    public Payment savePayment(PaymentApiResponse response, Order order) {

        Payment savePayment = Payment.builder()
                .paymentKey(response.paymentKey())
                .paymentStatus(PaymentStatus.DONE)
                .paymentRequestAt(LocalDateTime.parse(response.requestedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .paymentApprovedAt(LocalDateTime.parse(response.approvedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .paymentReceipt(response.receiptUrl())
                .order(order)
                .totalAmount(response.totalAmount())
                .provider(PaymentProvider.valueOf(response.provider()))
                .build();

        paymentRepository.save(savePayment);

        paymentRepository.save(savePayment);
        savePayment.getOrder().setOrderStatus(com.nhnacademy.order.order.domain.OrderStatus.COMPLETED);
        orderRepository.save(order);

        // 비회원/회원 모두 주문 시 이메일이 있다면 주문 번호 발송
        if (order.getOrdererInfo() != null && order.getOrdererInfo().ordererEmail() != null) {
            emailService.sendOrderNumber(order.getOrdererInfo().ordererEmail(), order.getOrderNumber());
        }

        return savePayment;
    }

    //결제 취소시
    @Override
    @Transactional
    public void updatePaymentCanceledStatus(Payment payment, Integer cancelAmount) {

        //해당 결제를 찾을 수 없다면?
        Payment findPayment = paymentRepository.findById((payment.getPaymentId())).orElseThrow(
                () -> new PaymentNotFoundException("결제 정보가 존재하지 않습니다."));

        //이미 취소된 결제 건 이라면?
        if(findPayment.getPaymentStatus().equals(PaymentStatus.CANCELED)) {
            throw new PaymentAlreadyCanceledException("이미 전체 취소된 결제 건 입니다.");
        }

        findPayment.cancelPayment(cancelAmount);

        if(findPayment.getPaymentStatus() == PaymentStatus.CANCELED) {
            findPayment.getOrder().setOrderStatus(com.nhnacademy.order.order.domain.OrderStatus.CANCELED);
        }
    }

    //결제 정보 반환 -> 서버에서 처리할때만 사용할듯
    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentByOrderNumber(String orderNumber) {
        return paymentRepository.findByOrder_OrderNumber(orderNumber).orElseThrow(
                ()->new PaymentNotFoundException("결제 정보가 존재하지 않습니다")
        );
    }

    //관리자 특정 결제 내역 조회 -> 사용자에게 보여줄 페이지(단건 조회)
    @Override
    @Transactional(readOnly = true)
    public Payment getPaymentById(Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(
                () -> new PaymentNotFoundException("결제 정보가 존재하지 않습니다."+paymentId)
        );
    }

    //관리자 전용 결제 내역 전체 조회,
    @Override
    @Transactional(readOnly = true)
    public Page<Payment> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable);
    }


    //회원 결제내역 전체 조회(회원)
    @Override
    @Transactional(readOnly = true)
    public Page<Payment> getAllMemberPayments(Long memberId, Pageable pageable) {
        if(memberId == null ){
            return Page.empty(pageable);
        }
        return paymentRepository.findByOrder_MemberId(memberId,pageable);
    }

    //회원 결제 내역 단건 조회(회원,비회원)
    @Override
    @Transactional(readOnly = true)
    public Payment getMemberPaymentByOrderNumber(PaymentUser user, String orderNumber) {
        if (user.isMember()) {
            return paymentRepository.findByOrder_OrderNumberAndOrder_MemberId(orderNumber, user.memberId())
                    .orElseThrow(() -> new PaymentNotFoundException("본인의 결제 내역만 조회할 수 있습니다."));
        }

        // Case 2: 비회원인 경우 -> 주문번호로 조회 후 소유권 검증
        Payment payment = paymentRepository.findByOrder_OrderNumber(orderNumber)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보가 존재하지 않습니다."));

        validateGuestOwner(payment, user); // 비회원 검증 로직 수행

        return payment;
    }

    private void validateGuestOwner(Payment payment, PaymentUser user) {
        Order order = payment.getOrder();

        // 해당 주문이 회원 주문(memberId 존재)이라면, 비회원은 절대 볼 수 없음
        if (order.getMemberId() != null) {
            throw new PaymentNotFoundException("비회원은 회원의 결제 정보를 조회할 수 없습니다.");
        }
    }
}
