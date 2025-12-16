package com.nhnacademy.payment.resolver;

import com.nhnacademy.payment.config.PaymentUser;
import lombok.extern.slf4j.Slf4j; // 로그 사용을 위해 추가
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component; // 빈 등록을 위해 추가
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j // 로그 기능을 위해 추가
@Component
public class PaymentUserResolver implements HandlerMethodArgumentResolver {

    private static final String HEADER_MEMBER_ID = "X-Member-Id";
    private static final String HEADER_MEMBER_ROLE = "X-Member-Role";
    private static final String HEADER_GUEST_ID = "X-Guest-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return PaymentUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {

        // 1. 헤더 값 가져오기
        String memberIdHeader = webRequest.getHeader(HEADER_MEMBER_ID);
        String memberRoleHeader = webRequest.getHeader(HEADER_MEMBER_ROLE); // 역할 헤더 추가
        String guestIdHeader = webRequest.getHeader(HEADER_GUEST_ID);

        Long memberId = null;
        Long guestId = null;

        // 역할 기본값 처리 (헤더가 없으면 "GUEST" 혹은 null 등 정책에 따라 설정)
        String role = StringUtils.hasText(memberRoleHeader) ? memberRoleHeader : "GUEST";

        // 2. 회원 ID 파싱 (변수명 오타 수정: memberIdStr -> memberIdHeader)
        if (StringUtils.hasText(memberIdHeader)) {
            try {
                memberId = Long.valueOf(memberIdHeader);
            } catch (NumberFormatException e) {
                log.error("Member ID 형식이 올바르지 않습니다: {}", memberIdHeader);
            }
        }

        // 3. 비회원 ID 파싱 (변수명 오타 수정: guestIdStr -> guestIdHeader)
        if (StringUtils.hasText(guestIdHeader)) {
            try {
                guestId = Long.valueOf(guestIdHeader);
            } catch (NumberFormatException e) {
                log.error("Guest ID 형식이 올바르지 않습니다: {}", guestIdHeader);
            }
        }

        // 4. 검증: 둘 다 없으면 에러
        if (memberId == null && guestId == null) {
            throw new IllegalArgumentException("사용자 식별 정보(MemberId or GuestId)가 헤더에 없습니다.");
        }

        boolean isMember = (memberId != null);

        // 5. role 정보까지 포함하여 객체 생성
        return new PaymentUser(memberId, guestId, role, isMember);
    }
}