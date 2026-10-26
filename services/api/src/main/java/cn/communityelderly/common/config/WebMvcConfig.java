package cn.communityelderly.common.config;

import cn.communityelderly.common.idempotency.IdempotencyInterceptor;
import cn.communityelderly.common.security.RoleAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

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
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path photoPath = Paths.get(".local-data", "files", "photos").toAbsolutePath().normalize();
        registry.addResourceHandler("/api/v1/files/view/**")
                .addResourceLocations(photoPath.toUri().toString());
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
