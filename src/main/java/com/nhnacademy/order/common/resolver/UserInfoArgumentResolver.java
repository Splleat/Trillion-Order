package com.nhnacademy.order.common.resolver;

import com.nhnacademy.order.common.dto.UserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
public class UserInfoArgumentResolver implements HandlerMethodArgumentResolver {
    private static final String HEADER_USER_ID = "X-Member-Id";
    private static final String HEADER_USER_ROLE = "X-Member-Role";
    private static final String HEADER_GUEST_ID = "X-Guest-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(UserInfo.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        String guestIdStr = webRequest.getHeader(HEADER_GUEST_ID);
        String userIdStr = webRequest.getHeader(HEADER_USER_ID);
        String userRole = webRequest.getHeader(HEADER_USER_ROLE);

        log.info("요청 헤더 - X-Member-Id: {}, X-Guest-Id: {}, X-Member-Role: {}", userIdStr, guestIdStr, userRole);

        if (userRole == null || userRole.isBlank()) {
            return null;
        }

        if (userIdStr != null && !userIdStr.isBlank()) {
            Long userId = Long.parseLong(userIdStr);
            return new UserInfo(userId, null, userRole);
        }

        if (guestIdStr != null && !guestIdStr.isBlank()) {
            return new UserInfo(null, guestIdStr, userRole);
        }

        return null;
    }
}
