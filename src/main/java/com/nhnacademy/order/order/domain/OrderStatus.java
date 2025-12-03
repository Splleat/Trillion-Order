package com.nhnacademy.order.order.domain;

public enum OrderStatus {
    PENDING,    // 결제 대기
    COMPLETED,  // 결제 완료
    CANCELED,   // 모든 상품 취소/환불

    // 사가를 위한 상태
    CREATING,           // 주문 생성 사가 진행 중
    CREATION_FAILED,    // 주문 생성 실패

    // 스케줄링을 위한 상태 (사가 완료 이후 -> 도메인 상태 변경까지)
    // 해당 상태들이 없으면 스케줄링을 위해 매번 DB를 뒤져서 확인해야 함 -> 강한 결합과 비효율
    AWAITING_POST_PROCESSING,
    AWAITING_CANCELLATION
}
