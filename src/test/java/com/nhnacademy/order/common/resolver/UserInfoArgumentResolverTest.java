package com.nhnacademy.order.common.resolver;

import com.nhnacademy.order.common.dto.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserInfoArgumentResolverTest {

    private UserInfoArgumentResolver resolver;
    private MethodParameter userInfoParameter;
    private NativeWebRequest webRequest;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        resolver = new UserInfoArgumentResolver();
        webRequest = mock(NativeWebRequest.class);

        // UserInfo 파라미터를 가진 메서드를 모킹하기 위한 준비
        class TestController {
            public void testMethod(UserInfo userInfo) {}
        }

        userInfoParameter = new MethodParameter(TestController.class.getMethod("testMethod", UserInfo.class), 0);
    }

    @Test
    @DisplayName("supportsParameter: UserInfo 타입 지원 확인")
    void supportsParameter_shouldSupportUserInfo() {
        assertThat(resolver.supportsParameter(userInfoParameter)).isTrue();
    }

    @Test
    @DisplayName("supportsParameter: UserInfo 외 다른 타입은 지원하지 않음")
    void supportsParameter_shouldNotSupportOtherTypes() throws NoSuchMethodException {
        class TestController {
            public void otherMethod(String other) {}
        }
        MethodParameter otherParameter = new MethodParameter(TestController.class.getMethod("otherMethod", String.class), 0);
        assertThat(resolver.supportsParameter(otherParameter)).isFalse();
    }

    @Test
    @DisplayName("resolveArgument: 회원 - X-Member-Id와 X-Member-Role 헤더가 있을 때 UserInfo 객체 생성 성공")
    void resolveArgument_shouldReturnMemberInfo_whenMemberHeadersExist() throws Exception {
        // given
        when(webRequest.getHeader("X-Member-Id")).thenReturn("123");
        when(webRequest.getHeader("X-Member-Role")).thenReturn("ROLE_MEMBER");
        when(webRequest.getHeader("X-Guest-Id")).thenReturn(null);

        // when
        Object result = resolver.resolveArgument(userInfoParameter, null, webRequest, null);

        // then
        assertThat(result).isInstanceOf(UserInfo.class);
        UserInfo userInfo = (UserInfo) result;
        assertThat(userInfo.userId()).isEqualTo(123L);
        assertThat(userInfo.guestId()).isNull();
        assertThat(userInfo.role()).isEqualTo("ROLE_MEMBER");
    }

    @Test
    @DisplayName("resolveArgument: 비회원 - X-Guest-Id와 X-Member-Role 헤더가 있을 때 UserInfo 객체 생성 성공")
    void resolveArgument_shouldReturnGuestInfo_whenGuestHeadersExist() throws Exception {
        // given
        String guestId = "guest-uuid-12345";
        when(webRequest.getHeader("X-Member-Id")).thenReturn(null);
        when(webRequest.getHeader("X-Guest-Id")).thenReturn(guestId);
        when(webRequest.getHeader("X-Member-Role")).thenReturn("ROLE_GUEST");

        // when
        Object result = resolver.resolveArgument(userInfoParameter, null, webRequest, null);

        // then
        assertThat(result).isInstanceOf(UserInfo.class);
        UserInfo userInfo = (UserInfo) result;
        assertThat(userInfo.userId()).isNull();
        assertThat(userInfo.guestId()).isEqualTo(guestId);
        assertThat(userInfo.role()).isEqualTo("ROLE_GUEST");
    }

    @Test
    @DisplayName("resolveArgument: 회원/비회원 헤더가 모두 있을 때 회원 정보를 우선함")
    void resolveArgument_shouldPrioritizeMemberInfo_whenAllHeadersExist() throws Exception {
        // given
        when(webRequest.getHeader("X-Member-Id")).thenReturn("123");
        when(webRequest.getHeader("X-Member-Role")).thenReturn("ROLE_MEMBER");
        when(webRequest.getHeader("X-Guest-Id")).thenReturn("guest-uuid-ignored");

        // when
        Object result = resolver.resolveArgument(userInfoParameter, null, webRequest, null);

        // then
        assertThat(result).isInstanceOf(UserInfo.class);
        UserInfo userInfo = (UserInfo) result;
        assertThat(userInfo.userId()).isEqualTo(123L);
        assertThat(userInfo.guestId()).isNull();
        assertThat(userInfo.role()).isEqualTo("ROLE_MEMBER");
    }

    @Test
    @DisplayName("resolveArgument: X-Member-Role 헤더가 없을 때 null 반환")
    void resolveArgument_shouldReturnNull_whenRoleHeaderIsMissing() throws Exception {
        // given
        when(webRequest.getHeader("X-Member-Id")).thenReturn("123");
        when(webRequest.getHeader("X-Guest-Id")).thenReturn("guest-uuid-12345");
        when(webRequest.getHeader("X-Member-Role")).thenReturn(null);

        // when
        Object result = resolver.resolveArgument(userInfoParameter, null, webRequest, null);

        // then
        assertThat(result).isNull();
    }
    
    @Test
    @DisplayName("resolveArgument: X-Member-Role 헤더가 비어있을 때 null 반환")
    void resolveArgument_shouldReturnNull_whenRoleHeaderIsBlank() throws Exception {
        // given
        when(webRequest.getHeader("X-Member-Id")).thenReturn("123");
        when(webRequest.getHeader("X-Member-Role")).thenReturn("  ");

        // when
        Object result = resolver.resolveArgument(userInfoParameter, null, webRequest, null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveArgument: 식별자(ID) 헤더가 모두 없을 때 null 반환")
    void resolveArgument_shouldReturnNull_whenAllIdHeadersAreMissing() throws Exception {
        // given
        when(webRequest.getHeader("X-Member-Id")).thenReturn(null);
        when(webRequest.getHeader("X-Guest-Id")).thenReturn(null);
        when(webRequest.getHeader("X-Member-Role")).thenReturn("ROLE_MEMBER");

        // when
        Object result = resolver.resolveArgument(userInfoParameter, null, webRequest, null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveArgument: X-Member-Id가 숫자가 아닐 때 NumberFormatException 발생")
    void resolveArgument_shouldThrowException_whenUserIdIsInvalid() {
        // given
        when(webRequest.getHeader("X-Member-Id")).thenReturn("not-a-number");
        when(webRequest.getHeader("X-Member-Role")).thenReturn("ROLE_MEMBER");

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(userInfoParameter, null, webRequest, null))
            .isInstanceOf(NumberFormatException.class);
    }
}
