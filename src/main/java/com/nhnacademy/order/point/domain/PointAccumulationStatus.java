package com.nhnacademy.order.point.domain;

public enum PointAccumulationStatus {
    PENDING,    // 처리 대기
    COMPLETED,  // 처리 성공
    FAILED      // 처리 최종 실패 (DLQ)
}