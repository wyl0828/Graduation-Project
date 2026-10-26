package cn.communityelderly.common.security;

import cn.communityelderly.common.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

class SecurityContextTest {

    private final AuthSessionAuthenticationService sessionAuthenticationService = mock(AuthSessionAuthenticationService.class);
    private final AuthenticationFilter authenticationFilter = new AuthenticationFilter(
            sessionAuthenticationService, new com.fasterxml.jackson.databind.ObjectMapper());

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("验证 UserContext 绑定、获取与未登录抛出 UnauthorizedException")
    void testUserContext() {
        assertThrows(UnauthorizedException.class, UserContext::requireCurrentUser);

        CurrentUser user = new CurrentUser(
                "U001", "13800138000", "FAMILY", List.of("ELDER"), "ELDER", "COM-01"
        );
        UserContext.set(user);

        assertEquals("U001", UserContext.requireCurrentUser().getUserId());
        assertTrue(UserContext.requireCurrentUser().hasRole("ELDER"));
        assertFalse(UserContext.requireCurrentUser().hasRole("COMMUNITY_ADMIN"));

        UserContext.clear();
        assertNull(UserContext.get());
    }

    @Test
    @DisplayName("请求身份来自会话验证服务，且请求结束后清理 UserContext")
    void testAuthenticationFilter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
        request.addHeader("Authorization", "Bearer valid-session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CurrentUser authenticatedUser = new CurrentUser(
                "U001", "13800138000", "FAMILY", List.of("ELDER"), "ELDER", "COM-01", 1L, "session-1", "MINIAPP");
        when(sessionAuthenticationService.authenticate("valid-session", "/api/v1/test")).thenReturn(authenticatedUser);

        FilterChain filterChain = (req, res) -> {
            CurrentUser current = UserContext.get();
            assertNotNull(current);
            assertEquals("U001", current.getUserId());
            assertTrue(current.hasRole("ELDER"));
        };

        authenticationFilter.doFilter(request, response, filterChain);

        verify(sessionAuthenticationService).authenticate("valid-session", "/api/v1/test");
        // 过滤器执行完毕后强制清理
        assertNull(UserContext.get());
    }
}
