package com.tounsiguide.communityservice.common.security;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets a controller method declare a {@link CurrentUser} parameter. Answers 401 when there is no identity. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        return CurrentUser.fromHeaders(
                webRequest.getHeader(CurrentUser.ID_HEADER),
                webRequest.getHeader(CurrentUser.NAME_HEADER),
                webRequest.getHeader(CurrentUser.ROLES_HEADER)
        );
    }
}
