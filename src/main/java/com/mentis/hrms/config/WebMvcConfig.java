package com.mentis.hrms.config;

import com.mentis.hrms.interceptor.AuditInterceptor;
import com.mentis.hrms.interceptor.RoleInterceptorV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private RoleInterceptorV2 roleInterceptorV2;

    @Autowired
    private AuditInterceptor auditInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ORDER MATTERS: V2 authenticates first, audit runs afterCompletion (sees denies too).
        registry.addInterceptor(roleInterceptorV2)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/candidate/auth/**", "/candidate/login", "/candidate/create-password",
                        "/candidate/forgot-password", "/candidate/reset-password/**",
                        "/static/**", "/webjars/**", "/data/**",
                        "/css/**", "/js/**", "/images/**",
                        "/", "/home", "/job-description", "/actuator/health", "/favicon.ico", "/error");
        registry.addInterceptor(auditInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/static/**", "/webjars/**", "/css/**", "/js/**", "/images/**", "/data/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Static resources (kept explicit for predictable resolution)
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCachePeriod(3600);

        // Data JSON files
        registry.addResourceHandler("/data/**")
                .addResourceLocations("classpath:/static/data/")
                .setCachePeriod(3600);

        // NOTE: /uploads/** is NO LONGER public (Sprint 2, REQ-2-05).
        // Authenticated file access now flows via SecureUploadController at /secure-uploads/**.
        // The upload DIRECTORY itself is still resolved from hrms.uploads.dir / HRMS_UPLOAD_DIR / ./uploads
        // (created here at boot so the app fails fast on misconfigured volumes).
        String uploadDir = System.getProperty("hrms.uploads.dir",
                System.getenv().getOrDefault("HRMS_UPLOAD_DIR", "./uploads"));
        java.nio.file.Path uploadRoot = java.nio.file.Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(uploadRoot);
        } catch (java.io.IOException e) {
            org.slf4j.LoggerFactory.getLogger(WebMvcConfig.class)
                    .warn("Could not create upload directory {}: {}", uploadRoot, e.getMessage());
        }

        // Webjars
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCachePeriod(3600);
    }
}
