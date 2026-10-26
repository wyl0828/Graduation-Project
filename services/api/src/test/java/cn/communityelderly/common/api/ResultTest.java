package cn.communityelderly.common.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @BeforeEach
    void setUp() {
        MDC.put("traceId", "test-trace-12345");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("验证 Result.success() 返回标准结构与自动注入的 traceId")
    void testSuccess() {
        Result<String> result = Result.success("测试数据");

        assertEquals("OK", result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals("测试数据", result.getData());
        assertEquals("test-trace-12345", result.getTraceId());
    }

    @Test
    @DisplayName("验证 Result.error() 错误码构造与默认提示")
    void testError() {
        Result<Void> result = Result.error(ErrorCode.INVALID_CREDENTIALS);

        assertEquals("INVALID_CREDENTIALS", result.getCode());
        assertEquals("手机号或密码错误", result.getMessage());
        assertNull(result.getData());
        assertEquals("test-trace-12345", result.getTraceId());
    }

    @Test
    @DisplayName("验证 PageResult 分页包装结构")
    void testPageResult() {
        List<String> items = List.of("老人A", "老人B");
        PageResult<String> pageResult = PageResult.of(items, 1, 10, 2);

        assertEquals(2, pageResult.getTotal());
        assertEquals(1, pageResult.getPage());
        assertEquals(10, pageResult.getPageSize());
        assertEquals(2, pageResult.getItems().size());
    }
}
