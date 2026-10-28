package cn.communityelderly.common.idempotency;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.common.trace.TraceFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

@Component
public class IdempotencyInterceptor implements HandlerInterceptor {
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String REQUEST_BODY_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".requestBody";
    static final String RESERVATION_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".reservation";

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final int PRINCIPAL_SCOPE_MAX_LENGTH = 64;
    private static final int OPERATION_SCOPE_MAX_LENGTH = 128;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 80;

    private final ObjectProvider<IdempotencyService> idempotencyServiceProvider;
    private final ObjectMapper objectMapper;

    public IdempotencyInterceptor(ObjectProvider<IdempotencyService> idempotencyServiceProvider,
                                  ObjectMapper objectMapper) {
        this.idempotencyServiceProvider = idempotencyServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Idempotency idempotency = handlerMethod.getMethodAnnotation(Idempotency.class);
        if (idempotency == null) {
            return true;
        }

        String suppliedKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (!StringUtils.hasText(suppliedKey)) {
            if (idempotency.required()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "执行关键写操作时缺少必要请求头: Idempotency-Key");
            }
            return true;
        }

        if (idempotency.expireSeconds() < 1) {
            throw new IllegalStateException("幂等记录过期时间必须为正数");
        }

        String idempotencyKey = suppliedKey.trim();
        String principalScope = resolvePrincipal(request);
        String operationScope = resolveOperationScope(request);
        ensureScopeLengths(principalScope, operationScope, idempotencyKey);
        String requestHash = hashRequest(request, getRequestBody(request));
        String traceId = MDC.get(TraceFilter.MDC_KEY);

        IdempotencyService.Reservation reservation;
        try {
            IdempotencyService idempotencyService = idempotencyServiceProvider.getIfAvailable();
            if (idempotencyService == null) {
                throw new IllegalStateException("幂等服务未配置");
            }
            reservation = idempotencyService.reserve(principalScope, operationScope,
                    idempotencyKey, requestHash, traceId == null ? "" : traceId,
                    idempotency.expireSeconds());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("无法创建或读取幂等记录，拒绝执行写操作", exception);
            throw new BusinessException(ErrorCode.IDEMPOTENCY_UNAVAILABLE);
        }

        if (reservation.owned()) {
            request.setAttribute(RESERVATION_ATTRIBUTE, reservation);
            return true;
        }

        response.setStatus(reservation.responseStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (StringUtils.hasText(reservation.traceId())) {
            response.setHeader(TraceFilter.TRACE_HEADER, reservation.traceId());
        }
        response.getOutputStream().write(reservation.responseBody().getBytes(StandardCharsets.UTF_8));
        return false;
    }

    private String resolvePrincipal(HttpServletRequest request) {
        CurrentUser user = UserContext.get();
        if (user != null && StringUtils.hasText(user.getUserId())) {
            return "USER:" + user.getUserId().trim();
        }
        String remoteAddress = request.getRemoteAddr();
        return "IP:" + (StringUtils.hasText(remoteAddress) ? remoteAddress : "UNKNOWN");
    }

    private String resolveOperationScope(HttpServletRequest request) {
        Object bestPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String routeTemplate = bestPattern instanceof String pattern ? pattern : request.getRequestURI();
        return request.getMethod().toUpperCase() + " " + routeTemplate;
    }

    private void ensureScopeLengths(String principal, String operation, String key) {
        if (principal.length() > PRINCIPAL_SCOPE_MAX_LENGTH
                || operation.length() > OPERATION_SCOPE_MAX_LENGTH
                || key.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "幂等主体、接口路径或请求键长度超过数据库约束");
        }
    }

    private byte[] getRequestBody(HttpServletRequest request) {
        Object body = request.getAttribute(REQUEST_BODY_ATTRIBUTE);
        return body instanceof byte[] bytes ? bytes : new byte[0];
    }

    private String hashRequest(HttpServletRequest request, byte[] requestBody) {
        try {
            ObjectNode fingerprint = objectMapper.createObjectNode();
            fingerprint.put("method", request.getMethod().toUpperCase());
            fingerprint.put("uri", request.getRequestURI());
            if (request.getQueryString() == null) {
                fingerprint.putNull("query");
            } else {
                fingerprint.put("query", request.getQueryString());
            }
            fingerprint.put("bodyPresent", requestBody.length > 0);
            fingerprint.set("body", requestBody.length == 0
                    ? com.fasterxml.jackson.databind.node.NullNode.getInstance()
                    : canonicalize(objectMapper.readTree(requestBody)));
            byte[] canonicalRequest = objectMapper.writeValueAsBytes(fingerprint);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalRequest);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "带幂等键的写请求必须包含合法JSON正文");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行时缺少 SHA-256", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            for (Map.Entry<String, JsonNode> field : fields.entrySet()) {
                sorted.set(field.getKey(), canonicalize(field.getValue()));
            }
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode values = objectMapper.createArrayNode();
            for (JsonNode value : node) {
                values.add(canonicalize(value));
            }
            return values;
        }
        return node.deepCopy();
    }
}
