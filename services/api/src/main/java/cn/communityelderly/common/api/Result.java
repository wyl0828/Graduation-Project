package cn.communityelderly.common.api;

import org.slf4j.MDC;

public class Result<T> {
    private String code;
    private String message;
    private String traceId;
    private T data;

    public Result() {
        this.traceId = MDC.get("traceId");
    }

    public Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.OK.name(), ErrorCode.OK.getDefaultMessage(), data);
    }

    public static <T> Result<T> success() {
        return new Result<>(ErrorCode.OK.name(), ErrorCode.OK.getDefaultMessage(), null);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ErrorCode.OK.name(), message, data);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.name(), errorCode.getDefaultMessage(), null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String customMessage) {
        return new Result<>(errorCode.name(), customMessage, null);
    }

    public static <T> Result<T> error(String code, String message, T data) {
        return new Result<>(code, message, data);
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
