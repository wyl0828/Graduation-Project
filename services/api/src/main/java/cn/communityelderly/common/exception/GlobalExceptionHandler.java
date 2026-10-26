package cn.communityelderly.common.exception;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusinessException(BusinessException ex, HttpServletResponse response) {
        ErrorCode errorCode = ex.getErrorCode();
        int httpStatus = errorCode.getHttpStatus();

        log.warn("业务异常 [{}]: {}", errorCode.name(), ex.getMessage());
        attachTraceId(response);

        Result<Object> result = Result.error(errorCode.name(), ex.getMessage(), ex.getErrorData());
        return ResponseEntity.status(httpStatus).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletResponse response) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        log.warn("参数校验异常: {}", fieldErrors);
        attachTraceId(response);

        Result<Map<String, String>> result = Result.error(
                ErrorCode.VALIDATION_FAILED.name(),
                "请求参数校验未通过，请检查输入",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleMessageNotReadable(HttpMessageNotReadableException ex, HttpServletResponse response) {
        log.warn("HTTP 请求正文反序列化失败: {}", ex.getMessage());
        attachTraceId(response);
        Result<Void> result = Result.error(ErrorCode.BAD_REQUEST.name(), "请求正文格式不符合规范或JSON解析失败", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletResponse response) {
        log.warn("HTTP 方法不支持: {}", ex.getMessage());
        attachTraceId(response);
        Result<Void> result = Result.error(ErrorCode.BAD_REQUEST.name(), "当前请求方法不支持: " + ex.getMethod(), null);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(result);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletResponse response) {
        log.warn("HTTP Content-Type 不支持: {}", ex.getMessage());
        attachTraceId(response);
        Result<Void> result = Result.error(ErrorCode.BAD_REQUEST.name(), "不支持的 Content-Type 请求类型", null);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(result);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex, HttpServletResponse response) {
        attachTraceId(response);
        Result<Void> result = Result.error(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE.name(), "请求的资源未找到: " + ex.getResourcePath(), null);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpectedException(Exception ex, HttpServletResponse response) {
        String traceId = MDC.get("traceId");
        log.error("系统未知未捕获异常 [traceId={}]: ", traceId, ex);
        attachTraceId(response);

        Result<Void> result = Result.error(
                ErrorCode.INTERNAL_SERVER_ERROR.name(),
                ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    private void attachTraceId(HttpServletResponse response) {
        String traceId = MDC.get("traceId");
        if (traceId != null && response != null && !response.isCommitted()) {
            response.setHeader("X-Trace-Id", traceId);
        }
    }
}
