package com.mentis.hrms.pm.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class PmTabWebConfig implements WebMvcConfigurer {

    private final PmTabInterceptor pmTabInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pmTabInterceptor)
                .addPathPatterns("/candidate/dashboard", "/candidate/dashboard/**");
    }
}