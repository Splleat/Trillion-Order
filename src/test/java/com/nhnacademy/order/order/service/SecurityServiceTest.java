package com.nhnacademy.order.order.service;

import com.nhnacademy.order.common.dto.UserInfo;
import com.nhnacademy.order.order.exception.OrderNotFoundException;
import com.nhnacademy.order.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @InjectMocks
    private SecurityService securityService;

    @Mock
    private OrderRepository orderRepository;

    @Test
    @DisplayName("isAdmin: ADMIN 역할일 때 true 반환")
    void isAdmin_ShouldReturnTrue_ForAdminRole() {
        // given
        UserInfo adminInfo = new UserInfo(1L, null, "ADMIN");

        // when
        boolean result = securityService.isAdmin(adminInfo);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isAdmin: ADMIN이 아닌 역할일 때 false 반환")
    void isAdmin_ShouldReturnFalse_ForNonAdminRole() {
        // given
        UserInfo memberInfo = new UserInfo(1L, null, "MEMBER");

        // when
        boolean result = securityService.isAdmin(memberInfo);

        // then
        assertThat(result).isFalse();
    }
    
    @Test
    @DisplayName("isOrderOwner: 사용자가 주문 소유자일 때 true 반환")
    void isOrderOwner_ShouldReturnTrue_WhenUserIsOwner() {
        // given
        long userId = 1L;
        long orderId = 100L;
        UserInfo ownerInfo = new UserInfo(userId, null, "MEMBER");
        when(orderRepository.findMemberIdByOrderId(orderId)).thenReturn(Optional.of(userId));

        // when
        boolean result = securityService.isOrderOwner(ownerInfo, orderId);

        // then
        assertThat(result).isTrue();
        verify(orderRepository).findMemberIdByOrderId(orderId);
    }

    @Test
    @DisplayName("isOrderOwner: 주문이 존재하지 않을 때 OrderNotFoundException 발생")
    void isOrderOwner_ShouldThrowException_WhenOrderNotFound() {
        // given
        long userId = 1L;
        long orderId = 100L;
        UserInfo userInfo = new UserInfo(userId, null, "MEMBER");
        when(orderRepository.findMemberIdByOrderId(orderId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> securityService.isOrderOwner(userInfo, orderId))
            .isInstanceOf(OrderNotFoundException.class)
            .hasMessageContaining("존재하지 않는 주문 ID: " + orderId);
    }

    @Test
    @DisplayName("isOrderOwner: 사용자가 소유자가 아닐 때 false 반환")
    void isOrderOwner_ShouldReturnFalse_WhenUserIsNotOwner() {
        // given
        long userId = 1L;
        long ownerId = 2L;
        long orderId = 100L;
        UserInfo userInfo = new UserInfo(userId, null, "MEMBER");
        when(orderRepository.findMemberIdByOrderId(orderId)).thenReturn(Optional.of(ownerId));

        // when
        boolean result = securityService.isOrderOwner(userInfo, orderId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isOrderOwner: 비로그인 사용자일 때 false 반환")
    void isOrderOwner_ShouldReturnFalse_ForAnonymousUser() {
        // given
        long orderId = 100L;

        // when
        boolean result = securityService.isOrderOwner(null, orderId);

        // then
        assertThat(result).isFalse();
        verify(orderRepository, never()).findMemberIdByOrderId(anyLong());
    }

    @Test
    @DisplayName("isAuthenticated: 인증된 사용자일 때 true 반환")
    void isAuthenticated_ShouldReturnTrue_ForAuthenticatedUser() {
        // given
        UserInfo authenticatedUser = new UserInfo(1L, null, "MEMBER");

        // when
        boolean result = securityService.isAuthenticated(authenticatedUser);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isAuthenticated: 비로그인 사용자(null)일 때 false 반환")
    void isAuthenticated_ShouldReturnFalse_ForNullUser() {
        // when
        boolean result = securityService.isAuthenticated(null);

        // then
        assertThat(result).isFalse();
    }
}
