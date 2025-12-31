package com.nhnacademy.cart.common.resolver;

import com.nhnacademy.cart.common.annotation.GuestOnly;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CartHolderResolverTest {

    @InjectMocks
    private CartHolderResolver resolver;

    @Mock
    private MethodParameter parameter;

    @Mock
    private NativeWebRequest webRequest;

    @Mock
    private HttpServletRequest request;

    // ======================================================================
    //  supportsParameter 테스트
    // ======================================================================

    @Test
    @DisplayName("supports: 파라미터 타입이 CartHolder면 true")
    void supportsParameter_True() {
        // given
        given(parameter.getParameterType()).willReturn((Class) CartHolder.class);

        // when
        boolean result = resolver.supportsParameter(parameter);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("supports: 파라미터 타입이 다른 것이면 false")
    void supportsParameter_False() {
        // given
        given(parameter.getParameterType()).willReturn((Class) String.class);

        // when
        boolean result = resolver.supportsParameter(parameter);

        // then
        assertThat(result).isFalse();
    }

    // ======================================================================
    //  resolveArgument 테스트
    // ======================================================================

    @Test
    @DisplayName("Resolve: [@GuestOnly] 어노테이션이 있고, 헤더에 GuestId가 있으면 Guest 반환")
    void resolve_GuestOnly_Success() {
        // given
        given(webRequest.getNativeRequest()).willReturn(request);
        given(parameter.hasParameterAnnotation(GuestOnly.class)).willReturn(true); // @GuestOnly 있음
        given(request.getHeader("X-Guest-Id")).willReturn("guest-123");

        // when
        CartHolder result = (CartHolder) resolver.resolveArgument(parameter, null, webRequest, null);

        // then
        assertThat(result.isMember()).isFalse();
        assertThat(result.getGuestId()).isEqualTo("guest-123");
    }

    @Test
    @DisplayName("Resolve: [@GuestOnly] 어노테이션이 있는데, 헤더에 GuestId가 없으면 예외 발생")
    void resolve_GuestOnly_Fail_NoHeader() {
        // given
        given(webRequest.getNativeRequest()).willReturn(request);
        given(parameter.hasParameterAnnotation(GuestOnly.class)).willReturn(true);
        given(request.getHeader("X-Guest-Id")).willReturn(null); // 헤더 없음

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비회원 정보가 없습니다.");
    }

    @Test
    @DisplayName("Resolve: [일반] MemberId 헤더가 있으면 Member 반환 (GuestId가 있어도 무시)")
    void resolve_Member_Success() {
        // given
        given(webRequest.getNativeRequest()).willReturn(request);
        given(parameter.hasParameterAnnotation(GuestOnly.class)).willReturn(false);
        given(request.getHeader("X-Member-Id")).willReturn("100"); // 회원 헤더 존재

        // when
        CartHolder result = (CartHolder) resolver.resolveArgument(parameter, null, webRequest, null);

        // then
        assertThat(result.isMember()).isTrue();
        assertThat(result.getMemberId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Resolve: [일반] MemberId 헤더 형식이 숫자가 아니면 예외 발생")
    void resolve_Member_Fail_NumberFormat() {
        // given
        given(webRequest.getNativeRequest()).willReturn(request);
        given(parameter.hasParameterAnnotation(GuestOnly.class)).willReturn(false);
        given(request.getHeader("X-Member-Id")).willReturn("invalid-id"); // 숫자 아님

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("잘못된 회원 ID 형식");
    }

    @Test
    @DisplayName("Resolve: [일반] MemberId 없고 GuestId만 있으면 Guest 반환")
    void resolve_NoMember_But_Guest_Success() {
        // given
        given(webRequest.getNativeRequest()).willReturn(request);
        given(parameter.hasParameterAnnotation(GuestOnly.class)).willReturn(false);
        given(request.getHeader("X-Member-Id")).willReturn(null); // 회원 없음
        given(request.getHeader("X-Guest-Id")).willReturn("guest-456"); // 비회원 있음

        // when
        CartHolder result = (CartHolder) resolver.resolveArgument(parameter, null, webRequest, null);

        // then
        assertThat(result.isMember()).isFalse();
        assertThat(result.getGuestId()).isEqualTo("guest-456");
    }

    @Test
    @DisplayName("Resolve: [일반] 둘 다 없으면 예외 발생")
    void resolve_NoHeaders_Fail() {
        // given
        given(webRequest.getNativeRequest()).willReturn(request);
        given(parameter.hasParameterAnnotation(GuestOnly.class)).willReturn(false);
        given(request.getHeader("X-Member-Id")).willReturn(null);
        given(request.getHeader("X-Guest-Id")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> resolver.resolveArgument(parameter, null, webRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유저 인증이 필요합니다");
    }
}