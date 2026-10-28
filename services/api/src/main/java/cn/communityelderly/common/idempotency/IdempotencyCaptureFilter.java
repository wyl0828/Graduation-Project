package cn.communityelderly.common.idempotency;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class IdempotencyCaptureFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCaptureFilter.class);
    private static final int MAX_REQUEST_BODY_BYTES = 1024 * 1024;
    private static final int MAX_RESPONSE_BODY_BYTES = 1024 * 1024;
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final ObjectProvider<IdempotencyService> idempotencyServiceProvider;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public IdempotencyCaptureFilter(ObjectProvider<IdempotencyService> idempotencyServiceProvider,
                                    com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.idempotencyServiceProvider = idempotencyServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/")
                || SAFE_METHODS.contains(request.getMethod().toUpperCase())
                || !StringUtils.hasText(request.getHeader(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] requestBody = readBoundedBody(request);
        if (requestBody == null) {
            writeRequestTooLarge(response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request, requestBody);
        cachedRequest.setAttribute(IdempotencyInterceptor.REQUEST_BODY_ATTRIBUTE, requestBody);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        boolean returnedNormally = false;
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
            returnedNormally = true;
        } finally {
            if (returnedNormally) {
                finishOwnedRequest(cachedRequest, cachedResponse);
            }
            cachedResponse.copyBodyToResponse();
        }
    }

    private byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        byte[] buffer = new byte[MAX_REQUEST_BODY_BYTES + 1];
        int length = 0;
        while (length < buffer.length) {
            int read = request.getInputStream().read(buffer, length, buffer.length - length);
            if (read < 0) {
                break;
            }
            length += read;
        }
        if (length > MAX_REQUEST_BODY_BYTES) {
            return null;
        }
        return java.util.Arrays.copyOf(buffer, length);
    }

    private void finishOwnedRequest(HttpServletRequest request,
                                    ContentCachingResponseWrapper response) throws IOException {
        Object pending = request.getAttribute(IdempotencyInterceptor.RESERVATION_ATTRIBUTE);
        if (!(pending instanceof IdempotencyService.Reservation reservation) || !reservation.owned()) {
            return;
        }
        IdempotencyService idempotencyService = idempotencyServiceProvider.getIfAvailable();
        if (idempotencyService == null) {
            writeUnavailable(response);
            return;
        }

        int status = response.getStatus();
        boolean successful = status >= 200 && status < 300;
        boolean replayableClientFailure = status >= 400 && status < 500;
        if (successful || replayableClientFailure) {
            byte[] body = response.getContentAsByteArray();
            if (body.length > MAX_RESPONSE_BODY_BYTES || !isJson(response.getContentType(), body)) {
                if (successful) {
                    writeUnavailable(response);
                }
                return;
            }
            Charset charset = resolveJsonCharset(response.getContentType());
            try {
                idempotencyService.complete(reservation, status, new String(body, charset), MDC.get("traceId"));
            } catch (RuntimeException exception) {
                log.error("无法保存幂等响应，保留处理中占位以避免重复执行", exception);
                writeUnavailable(response);
            }
            return;
        }
    }

    private boolean isJson(String contentType, byte[] body) {
        if (!StringUtils.hasText(contentType) || body.length == 0) {
            return false;
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            boolean jsonType = MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || mediaType.getSubtype().toLowerCase().endsWith("+json");
            return jsonType && objectMapper.readTree(body) != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private Charset resolveJsonCharset(String contentType) {
        try {
            Charset declaredCharset = MediaType.parseMediaType(contentType).getCharset();
            return declaredCharset == null ? StandardCharsets.UTF_8 : declaredCharset;
        } catch (Exception exception) {
            return StandardCharsets.UTF_8;
        }
    }

    private void writeRequestTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                Result.error(ErrorCode.BAD_REQUEST.name(), "幂等请求正文超过允许大小", null));
    }

    private void writeUnavailable(ContentCachingResponseWrapper response) throws IOException {
        response.resetBuffer();
        response.setStatus(ErrorCode.IDEMPOTENCY_UNAVAILABLE.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Result.error(ErrorCode.IDEMPOTENCY_UNAVAILABLE));
    }
}
