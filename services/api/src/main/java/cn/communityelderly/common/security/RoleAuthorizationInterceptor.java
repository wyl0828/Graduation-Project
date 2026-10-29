package cn.communityelderly.common.security;

import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        if (isAnonymousEndpoint(request.getMethod(), path)) {
            return true;
        }

        CurrentUser user = UserContext.get();
        if (user == null) {
            throw new UnauthorizedException("访问该资源需要先登录");
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRoles annotation = handlerMethod.getMethodAnnotation(RequireRoles.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequireRoles.class);
        }

        if (annotation == null) {
            return true;
        }

        String[] allowedRoles = annotation.value();
        boolean hasPermission = Arrays.stream(allowedRoles).anyMatch(user::hasRole);
        if (!hasPermission) {
            throw new ForbiddenException("您的当前角色 [" + user.getCurrentRole() + "] 无权执行此操作");
        }

        return true;
    }

    private boolean isAnonymousEndpoint(String method, String path) {
        // Public service catalogue: non-sensitive, needed before login to browse offerings.
        if (HttpMethod.GET.matches(method) && "/api/v1/service-catalogs".equals(path)) {
            return true;
        }
        if (!HttpMethod.POST.matches(method)) {
            return false;
        }
        return switch (path) {
            case "/api/v1/auth/login",
                    "/api/v1/auth/register-child",
                    "/api/v1/auth/forgot-password/ticket",
                    "/api/v1/auth/phone-challenges",
                    "/api/v1/auth/activate-elder",
                    "/api/v1/elder/activation/consume" -> true;
            default -> false;
        };
    }
}
