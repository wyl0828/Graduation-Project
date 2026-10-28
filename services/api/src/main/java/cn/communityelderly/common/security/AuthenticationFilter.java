package cn.communityelderly.common.security;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.Result;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationFilter extends OncePerRequestFilter {
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String SESSION_COOKIE_NAME = "SESSION_TOKEN";

    private final AuthSessionAuthenticationService sessionAuthenticationService;
    private final ObjectMapper objectMapper;

    public AuthenticationFilter(AuthSessionAuthenticationService sessionAuthenticationService,
                                 ObjectMapper objectMapper) {
        this.sessionAuthenticationService = sessionAuthenticationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String uri = request.getRequestURI();
            if (isAnonymousPath(uri)) {
                filterChain.doFilter(request, response);
                return;
            }

            String authHeader = request.getHeader("Authorization");
            boolean bearerAuth = StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX);
            String token = bearerAuth
                    ? authHeader.substring(BEARER_PREFIX.length()).trim()
                    : extractCookieToken(request);
            boolean cookieAuth = !bearerAuth && StringUtils.hasText(token);
            if (StringUtils.hasText(token)) {
                CurrentUser user = sessionAuthenticationService.authenticate(token, uri);
                if (user != null) {
                    UserContext.set(user);
                    // Only cookie-borne credentials are ambient, so only they need CSRF protection.
                    // A Bearer token must be attached explicitly by the caller and is therefore
                    // safe to accept from either the web console or the mini program.
                    if (cookieAuth && isUnsafeMethod(request.getMethod())
                            && !sessionAuthenticationService.isCsrfTokenValid(
                            user.getSessionId(), request.getHeader("X-CSRF-Token"))) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json");
                        response.setCharacterEncoding("UTF-8");
                        objectMapper.writeValue(response.getWriter(), Result.error(ErrorCode.CSRF_VALIDATION_FAILED));
                        return;
                    }
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private boolean isAnonymousPath(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return switch (path) {
            case "/api/v1/auth/login",
                    "/api/v1/auth/register-child",
                    "/api/v1/auth/forgot-password/ticket",
                    "/api/v1/auth/phone-challenges",
                    "/api/v1/auth/activate-elder",
                    "/api/v1/elder/activation/consume",
                    "/api/v1/service-catalogs" -> true;
            default -> false;
        };
    }

    private String extractCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> SESSION_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private boolean isUnsafeMethod(String method) {
        return !Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(method.toUpperCase());
    }
}
