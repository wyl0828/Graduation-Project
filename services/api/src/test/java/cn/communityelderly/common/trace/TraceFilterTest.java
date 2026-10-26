package cn.communityelderly.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TraceFilterTest {

    private final TraceFilter traceFilter = new TraceFilter();

    @Test
    @DisplayName("请求未携带 X-Trace-Id 时自动生成并在响应头返回")
    void testTraceGeneration() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {
            // 过滤器链执行过程中 MDC 必须有值
            String currentTraceId = MDC.get("traceId");
            assertNotNull(currentTraceId);
            assertTrue(currentTraceId.startsWith("trace-"));
        };

        traceFilter.doFilter(request, response, filterChain);

        // 响应头必须携带一致的 traceId
        String responseTraceId = response.getHeader("X-Trace-Id");
        assertNotNull(responseTraceId);
        assertTrue(responseTraceId.startsWith("trace-"));

        // 退出后 MDC 必须被安全清理
        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("请求携带 X-Trace-Id 时透传复用")
    void testTraceReuse() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "client-trace-999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {
            assertEquals("client-trace-999", MDC.get("traceId"));
        };

        traceFilter.doFilter(request, response, filterChain);

        assertEquals("client-trace-999", response.getHeader("X-Trace-Id"));
        assertNull(MDC.get("traceId"));
    }
}
