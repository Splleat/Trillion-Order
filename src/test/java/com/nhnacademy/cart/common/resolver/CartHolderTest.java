package com.nhnacademy.cart.common.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartHolderTest {

    @Test
    @DisplayName("Member 생성 성공 및 isMember=true 확인")
    void createMember_Success() {
        CartHolder holder = CartHolder.member(100L);

        assertThat(holder.getMemberId()).isEqualTo(100L);
        assertThat(holder.getGuestId()).isNull();
        assertThat(holder.isMember()).isTrue();
    }

    @Test
    @DisplayName("Guest 생성 성공 및 isMember=false 확인")
    void createGuest_Success() {
        CartHolder holder = CartHolder.guest("guest-uuid-123");

        assertThat(holder.getGuestId()).isEqualTo("guest-uuid-123");
        assertThat(holder.getMemberId()).isNull();
        assertThat(holder.isMember()).isFalse();
    }

    @Test
    @DisplayName("Member ID가 null이면 예외 발생")
    void createMember_Fail_NullId() {
        assertThatThrownBy(() -> CartHolder.member(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MemberId must not be null");
    }

    @Test
    @DisplayName("Guest ID가 null이면 예외 발생")
    void createGuest_Fail_NullId() {
        assertThatThrownBy(() -> CartHolder.guest(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GuestId must not be null");
    }
}