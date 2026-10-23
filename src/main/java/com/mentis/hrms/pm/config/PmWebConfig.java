package com.mentis.hrms.pm.config;

import com.mentis.hrms.pm.security.CurrentUserIdResolver;
import com.mentis.hrms.pm.security.PmCurrentUserInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class PmWebConfig implements WebMvcConfigurer {

    private final CurrentUserIdResolver currentUserIdResolver;
    private final PmCurrentUserInterceptor pmCurrentUserInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pmCurrentUserInterceptor)
                .addPathPatterns("/api/pm/**");
    }
}