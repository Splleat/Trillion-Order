package com.nhnacademy.order.delivery.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryPolicyTest {

    @Test
    @DisplayName("기본 생성자 테스트")
    void testNoArgsConstructor() {
        DeliveryPolicy deliveryPolicy = new DeliveryPolicy();
        assertThat(deliveryPolicy).isNotNull();
    }

    @Test
    @DisplayName("전체 인자 생성자 및 Getter 테스트")
    void testAllArgsConstructorAndGetters() {
        Long id = 1L;
        int fee = 3000;
        int threshold = 50000;

        DeliveryPolicy deliveryPolicy = new DeliveryPolicy(id, fee, threshold);

        assertThat(deliveryPolicy.getDeliveryPolicyId()).isEqualTo(id);
        assertThat(deliveryPolicy.getDeliveryPolicyFee()).isEqualTo(fee);
        assertThat(deliveryPolicy.getDeliveryPolicyThreshold()).isEqualTo(threshold);
    }

    @Test
    @DisplayName("update 메서드 테스트")
    void testUpdate() {
        DeliveryPolicy deliveryPolicy = new DeliveryPolicy(1L, 3000, 50000);

        int newFee = 2500;
        int newThreshold = 60000;

        deliveryPolicy.update(newFee, newThreshold);

        assertThat(deliveryPolicy.getDeliveryPolicyFee()).isEqualTo(newFee);
        assertThat(deliveryPolicy.getDeliveryPolicyThreshold()).isEqualTo(newThreshold);
    }
}
