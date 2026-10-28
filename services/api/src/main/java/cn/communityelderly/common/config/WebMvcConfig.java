package cn.communityelderly.common.config;

import cn.communityelderly.common.idempotency.IdempotencyInterceptor;
import cn.communityelderly.common.security.RoleAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final RoleAuthorizationInterceptor roleAuthorizationInterceptor;
    private final IdempotencyInterceptor idempotencyInterceptor;

    public WebMvcConfig(RoleAuthorizationInterceptor roleAuthorizationInterceptor, IdempotencyInterceptor idempotencyInterceptor) {
        this.roleAuthorizationInterceptor = roleAuthorizationInterceptor;
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleAuthorizationInterceptor)
                .addPathPatterns("/api/**");

        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
