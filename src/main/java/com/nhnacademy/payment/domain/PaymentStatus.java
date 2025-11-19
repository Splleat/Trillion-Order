package com.nhnacademy.payment.domain;

//이름이 겹침 Payment 전용임.
public enum PaymentStatus {
    PENDING,//결제 대기
    COMPLETED, // 결제 완료
    CANCELED
}
