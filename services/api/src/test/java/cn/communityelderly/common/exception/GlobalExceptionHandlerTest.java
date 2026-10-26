package cn.communityelderly.common.exception;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        MDC.put("traceId", "trace-handler-test-01");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("验证 BusinessException 正确映射为相应 HTTP 状态码与统一 JSON 报文")
    void testBusinessException() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        BusinessException ex = new BusinessException(ErrorCode.ACTIVATION_CODE_EXPIRED);

        ResponseEntity<Result<Object>> entity = handler.handleBusinessException(ex, response);

        assertEquals(HttpStatus.CONFLICT, entity.getStatusCode());
        assertNotNull(entity.getBody());
        assertEquals("ACTIVATION_CODE_EXPIRED", entity.getBody().getCode());
        assertEquals("激活码已超过24小时有效期", entity.getBody().getMessage());
        assertEquals("trace-handler-test-01", entity.getBody().getTraceId());
        assertEquals("trace-handler-test-01", response.getHeader("X-Trace-Id"));
    }

    @Test
    @DisplayName("验证 ConflictException 乐观锁冲突正确携带 detail 数据")
    void testConflictException() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        ConflictException ex = ConflictException.optimisticLock(2, 1);

        ResponseEntity<Result<Object>> entity = handler.handleBusinessException(ex, response);

        assertEquals(HttpStatus.CONFLICT, entity.getStatusCode());
        assertNotNull(entity.getBody());
        assertEquals("OPTIMISTIC_LOCK_CONFLICT", entity.getBody().getCode());
        assertNotNull(entity.getBody().getData());
    }

    @Test
    @DisplayName("验证未知系统异常映射为 500 且对用户屏蔽真实堆栈")
    void testUnexpectedException() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Exception ex = new RuntimeException("数据库底层死锁异常堆栈信息");

        ResponseEntity<Result<Void>> entity = handler.handleUnexpectedException(ex, response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, entity.getStatusCode());
        assertNotNull(entity.getBody());
        assertEquals("INTERNAL_SERVER_ERROR", entity.getBody().getCode());
        assertEquals("系统繁忙，请稍后重试", entity.getBody().getMessage());
    }
}
