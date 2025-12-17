package com.nhnacademy.payment.service.impl;

import com.nhnacademy.order.order.domain.Order;
import com.nhnacademy.order.order.domain.OrderStatus;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import com.nhnacademy.payment.config.PaymentGateway;
import com.nhnacademy.payment.config.PaymentUser;
import com.nhnacademy.payment.config.TossPaymentClient;
import com.nhnacademy.payment.dto.reqeust.PaymentRequestDto;
import com.nhnacademy.payment.dto.response.PaymentApiResponse;
import com.nhnacademy.payment.entity.Payment;
import com.nhnacademy.payment.exception.*;
import com.nhnacademy.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFlowService {
    private final PaymentService paymentService;
    private final PaymentGatewayRoutingService routingService;
    private final OrderRepository orderRepository;


    //결제 승인은 이미 승인된 상태만 생각 주문을 취소해도 취소한 주문에 대해서 다시 주문을 할 수 있음
    //당연한 말이지만 결제 대기중인 주문 상태건은 결제 승인 가능하게 해야 함.
    //결제 승인 -> 취소나 대기 중은 승인 가능하게, 이미 결제 처리되었다면? 결제 승인 불가능 하게
    public Payment confirmPayment(PaymentUser user, PaymentRequestDto request) {

        // 결제를 승인하려 했을때 찾을 수 없는 주문 건이라면?
        Order order = orderRepository.findOrderWithItemsByOrderNumber(request.orderNumber())
                .orElseThrow(() -> new OrderNotFoundException(request.orderNumber()));

        //view에서 뿌린 금액과 db에 저장된 금액이 일치하지 않을 시?
        if(!request.amount().equals(order.getOrderDetails().totalPrice())){
            throw new PaymentAmountMissMatchException("주문 금액과 결제 금액이 일치 하지 않습니다.");
        }

        //결제 승인시 이미 결제 승인된 주문이라면?
        if(order.getOrderStatus().equals(OrderStatus.COMPLETED)){
            throw new PaymentAlreadyApprovedException(request.orderNumber());
        }
        validateOrderOwner(order,user);

        //front->confirm 요청을 보내면 -> 맞는 처리가 가능한 PG사 호출.
        PaymentGateway gateway = routingService.getGateway(request.provider());

            PaymentApiResponse response = gateway.confirm(
                    request.paymentKey(),
                    request.orderNumber(),
                    request.amount()
            );

        try{
            return paymentService.savePayment(response, order);
        }catch (Exception e){
            //특정 이유로 데이터베이스에 결제 정보가 정상적으로 저장이 안된다면?
            log.error("결제 정보 저장 중 오류 발생 결제 취소 : {} ", request.orderNumber());

            //pg사에서는 다시 돈을 돌려주고
            gateway.cancel(request.paymentKey(), "서버 데이터베이스 저장 오류",response.totalAmount());

            //주문이 정상적으로 처리되지 않았으니 CANCELED로 롤백
            order.setOrderStatus(OrderStatus.CANCELED);
            orderRepository.save(order);
            throw new PaymentSaveFailException(request.orderNumber(), "데이터베이스에 저장 중 오류 발생");
        }
    }

    public void cancelPaymentByMember(String orderNumber, String cancelReason, Integer cancelAmount, PaymentUser user){
        Payment findPayment = paymentService.getPaymentByOrderNumber(orderNumber);

        validateOrderOwner(findPayment.getOrder(), user);

        processCancelPayment(findPayment, cancelReason, cancelAmount);
    }

    // [추가] 공통 검증 메서드 (private)
    private void validateOrderOwner(Order order, PaymentUser user) {
        //관리자는 모든 곳에 접근 가능하므로 여기서 메서드 종료
        if ("ROLE_ADMIN".equals(user.role())) {
            return;
        }
        //X-member-Id 할당시
        if (user.isMember()) {

            if (order.getMemberId() == null || !order.getMemberId().equals(user.memberId())) {
                throw new IllegalArgumentException("회원 주문 정보가 일치하지 않습니다.");
            }
            //X-Guest-Id 할당시 -> 비회원 일시
        } else {
            //이때 주문은 memberId는 null로 저장함.
            if (order.getMemberId() != null) {
                throw new IllegalArgumentException("비회원은 회원의 주문에 접근할 수 없습니다.");
            }
        }
    }


    //결제 취소 -> 주문이 결제 대기이거나 이미 취소됐다면 취소 못하게 해야 함, 배송 전 도서에 대해서만 부른다면.
    private void processCancelPayment(Payment payment,String cancelReason,Integer cancelAmount) {

        //이미 결제가 취소된 주문건에서는 또 다시 결제 취소는 불가능 함.
        if(payment.getOrder().getOrderStatus().equals(OrderStatus.CANCELED)){
            throw new PaymentAlreadyCanceledException("이미 전액 취소된 결제 건 입니다.");
        }

        //주문이 결제 대기 상태인데 취소 하려 할때도 역시 결제 취소는 불가능하다고 처리 해줘야 할듯
        if(payment.getOrder().getOrderStatus().equals(OrderStatus.PENDING)){
            throw new PaymentNotApprovedException("결제가 승인되지 않은 주문 건 입니다.");
        }

        //약간의 방어로직같은 느낌이긴 함 -> 만약 취소 금액이 null 이면 -> 전체 취소를 아니면 넘겨 받은 취소 금액을
        int amountToCancel = (cancelAmount == null) ? payment.getBalanceAmount() : cancelAmount;

        PaymentGateway gateway = routingService.getGateway(payment.getProvider().toString());

        PaymentApiResponse response = gateway.cancel(
                payment.getPaymentKey(),
                cancelReason,
                amountToCancel
                );

        if (isCancelSuccess(response.status())) {
            // 검증 통과 시에만 DB 상태 변경
            paymentService.updatePaymentCanceledStatus(payment, amountToCancel);
        } else {
            // (선택) 로그 남기기: 토스에서 에러는 안 냈지만 상태가 이상함
            log.warn("결제 취소 요청은 성공했으나 상태가 예상과 다름: {}", response.status());
        }
    }

    private boolean isCancelSuccess(String status) {
        return "CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status);
    }
}
