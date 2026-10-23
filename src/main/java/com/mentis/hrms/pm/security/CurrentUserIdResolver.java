package com.mentis.hrms.pm.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentUserIdResolver implements HandlerMethodArgumentResolver {

    private final PmUserProvider userProvider;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {

        HttpServletRequest req = webRequest.getNativeRequest(HttpServletRequest.class);
        if (req == null) throw new PmUserProvider.NotAuthenticatedException("No HttpServletRequest");

        PmUserProvider.PmUser user = userProvider.resolve(req);
        if (user == null) throw new PmUserProvider.NotAuthenticatedException("Not authenticated");

        PmCurrentUserHolder.set(user);
        return user.employeePk();
    }
}