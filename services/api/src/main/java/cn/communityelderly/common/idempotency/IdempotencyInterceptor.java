package cn.communityelderly.common.idempotency;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    // 本地内存幂等记录缓存 (compositeKey -> RequestSnapshot)
    private static final ConcurrentHashMap<String, RequestSnapshot> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;

    public static class RequestSnapshot {
        private final String uri;
        private final String method;
        private final long expireAtMillis;

        public RequestSnapshot(String uri, String method, int expireSeconds) {
            this.uri = uri;
            this.method = method;
            this.expireAtMillis = System.currentTimeMillis() + (long) expireSeconds * 1000L;
        }

        public String getUri() {
            return uri;
        }

        public String getMethod() {
            return method;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireAtMillis;
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Idempotency idempotency = handlerMethod.getMethodAnnotation(Idempotency.class);
        if (idempotency == null) {
            return true;
        }

        String key = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!StringUtils.hasText(key)) {
            if (idempotency.required()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "执行关键写操作时缺少必要请求头: Idempotency-Key");
            }
            return true;
        }

        cleanExpiredKeysIfNecessary();

        String principal = resolvePrincipal(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();
        String compositeKey = principal + ":" + key.trim();

        RequestSnapshot snapshot = new RequestSnapshot(uri, method, idempotency.expireSeconds());
        RequestSnapshot existing = CACHE.putIfAbsent(compositeKey, snapshot);

        if (existing != null) {
            if (existing.isExpired()) {
                // 已过期，更新为当前请求快照并放行
                CACHE.put(compositeKey, snapshot);
                return true;
            }

            // 未过期核验 URI 与 Method 是否一致
            if (!existing.getUri().equals(uri) || !existing.getMethod().equals(method)) {
                throw new BusinessException(
                        ErrorCode.IDEMPOTENCY_KEY_REUSED,
                        "幂等键已被使用但请求目标或方法不一致 (409 Conflict)"
                );
            }

            // 目标一致但已存在活动中的幂等键，直接阻断重复执行
            throw new BusinessException(
                    ErrorCode.DUPLICATE_OPERATION,
                    "重复请求已被拦截：相同幂等键操作已受理或已执行完成"
            );
        }

        return true;
    }

    private String resolvePrincipal(HttpServletRequest request) {
        CurrentUser user = UserContext.get();
        if (user != null && StringUtils.hasText(user.getUserId())) {
            return "USER:" + user.getUserId();
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip)) {
            ip = request.getRemoteAddr();
        }
        return "IP:" + (ip != null ? ip : "UNKNOWN");
    }

    private void cleanExpiredKeysIfNecessary() {
        if (CACHE.size() > MAX_CACHE_SIZE) {
            CACHE.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
}
