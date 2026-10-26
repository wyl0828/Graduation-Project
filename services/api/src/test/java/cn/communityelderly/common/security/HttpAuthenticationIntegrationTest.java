package cn.communityelderly.common.security;

import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.service.AuthService;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import cn.communityelderly.modules.dashboard.service.CommunityDashboardService;
import cn.communityelderly.modules.elder.service.ActivationCodeService;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.emergency.service.SosEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WebMvcTest(controllers = {
        cn.communityelderly.modules.auth.controller.AuthController.class,
        cn.communityelderly.modules.dashboard.controller.CommunityDashboardController.class,
        cn.communityelderly.modules.audit.controller.AuditLogController.class,
        cn.communityelderly.modules.emergency.controller.SosEventController.class,
        cn.communityelderly.modules.elder.controller.ElderArchiveController.class,
        cn.communityelderly.modules.elder.controller.ElderActivationController.class
})
class HttpAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PhoneVerificationChallengeService phoneVerificationChallengeService;

    @MockitoBean
    private CommunityDashboardService dashboardService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private SosEventService sosEventService;

    @MockitoBean
    private ElderArchiveService elderArchiveService;

    @MockitoBean
    private ActivationCodeService activationCodeService;

    @MockitoBean
    private cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService sessionAuthenticationService;

    @Test
    @DisplayName("匿名请求不能读取看板、审计、SOS 或档案")
    void anonymousRequestsToProtectedResourcesAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/community/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/audit/logs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/emergency/sos/active"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/elder/archives/123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("固定演示令牌不能访问业务接口")
    void demoTokenIsNotAnAuthenticationCredential() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/community/summary")
                        .header("Authorization", "Bearer demo-admin-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("有效会话只能读取其活动角色社区的数据")
    void authenticatedDashboardRequestIsScopedToActiveCommunity() throws Exception {
        CurrentUser user = new CurrentUser(
                "42", "13800000042", "STAFF",
                java.util.List.of("COMMUNITY_ADMIN", "GRID_WORKER"),
                "COMMUNITY_ADMIN", "77", 700L, "session-1", "MINIAPP");
        when(sessionAuthenticationService.authenticate(eq("valid-session"), anyString())).thenReturn(user);

        mockMvc.perform(get("/api/v1/dashboard/community/summary")
                        .header("Authorization", "Bearer valid-session")
                        .queryParam("communityId", "77"))
                .andExpect(status().isOk());
        verify(dashboardService).getSummary(77L);

        mockMvc.perform(get("/api/v1/dashboard/community/summary")
                        .header("Authorization", "Bearer valid-session")
                        .queryParam("communityId", "88"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditLogService, sosEventService, elderArchiveService);
    }

    @Test
    @DisplayName("登录可匿名发起，但当前用户接口仍要求有效会话")
    void loginIsPublicAndCurrentUserIsProtected() throws Exception {
        cn.communityelderly.modules.auth.dto.AuthTokenResponse miniResponse = new cn.communityelderly.modules.auth.dto.AuthTokenResponse(
                "miniapp-login-response", 77L, "13000000077", "FAMILY",
                java.util.List.of("CHILD"), "CHILD", null,
                java.time.LocalDateTime.now().plusDays(30));
        miniResponse.setClientType("MINIAPP");
        when(authService.login(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(miniResponse);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"13000000077\",\"password\":\"synthetic-test-input\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/current-user"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"synthetic-current-password\",\"newPassword\":\"synthetic-new-password-2026\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Web 登录只通过安全 Cookie 建立会话，不把 session token 放进 JSON")
    void webLoginUsesSecureHttpOnlyCookieAndReturnsNoBearerToken() throws Exception {
        cn.communityelderly.modules.auth.dto.AuthTokenResponse webResponse = new cn.communityelderly.modules.auth.dto.AuthTokenResponse(
                "web-session-secret", 42L, "13000000042", "STAFF",
                java.util.List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "77",
                java.time.LocalDateTime.now().plusHours(12));
        webResponse.setClientType("WEB");
        webResponse.setCsrfToken("csrf-response-token");
        when(authService.login(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(webResponse);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"13000000042\",\"password\":\"synthetic-test-input\",\"clientType\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertTrue(setCookie != null && setCookie.contains("SESSION_TOKEN="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertEquals(Boolean.parseBoolean(System.getenv().getOrDefault("AUTH_COOKIE_SECURE", "true")),
                setCookie.contains("; Secure"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertFalse(result.getResponse().getContentAsString().contains("web-session-secret"));
    }

    @Test
    @DisplayName("Cookie 认证的状态变更必须验证与会话绑定的 CSRF token")
    void cookieAuthenticatedMutationRequiresCsrfHeader() throws Exception {
        CurrentUser user = new CurrentUser(
                "42", "13000000042", "STAFF", java.util.List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN", "77", 700L, "session-web", "WEB");
        when(sessionAuthenticationService.authenticate(eq("cookie-session"), anyString())).thenReturn(user);

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(new jakarta.servlet.http.Cookie(AuthenticationFilter.SESSION_COOKIE_NAME, "cookie-session"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"synthetic-current-password\",\"newPassword\":\"synthetic-new-password-2026\"}"))
                .andExpect(status().isForbidden());

        when(sessionAuthenticationService.isCsrfTokenValid("session-web", "csrf-valid")).thenReturn(true);
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .cookie(new jakarta.servlet.http.Cookie(AuthenticationFilter.SESSION_COOKIE_NAME, "cookie-session"))
                        .header("X-CSRF-Token", "csrf-valid")
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"synthetic-current-password\",\"newPassword\":\"synthetic-new-password-2026\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("小程序登录仍返回 Bearer token JSON")
    void miniAppLoginKeepsBearerTokenContract() throws Exception {
        cn.communityelderly.modules.auth.dto.AuthTokenResponse miniResponse = new cn.communityelderly.modules.auth.dto.AuthTokenResponse(
                "miniapp-session-secret", 43L, "13000000043", "FAMILY",
                java.util.List.of("CHILD"), "CHILD", null,
                java.time.LocalDateTime.now().plusDays(30));
        miniResponse.setClientType("MINIAPP");
        when(authService.login(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn(miniResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"13000000043\",\"password\":\"synthetic-test-input\",\"clientType\":\"MINIAPP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("miniapp-session-secret"));
    }

    @Test
    @DisplayName("无已验证手机渠道时密码恢复明确不可用且不创建假工单")
    void passwordRecoveryFailsClosedWithoutIssuingTicket() throws Exception {
        when(authService.submitPasswordRecoveryTicket(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BusinessException(ErrorCode.RECOVERY_UNAVAILABLE,
                        ErrorCode.RECOVERY_UNAVAILABLE.getDefaultMessage()));

        mockMvc.perform(post("/api/v1/auth/forgot-password/ticket")
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"13000000077\",\"realName\":\"测试用户\",\"appealReason\":\"密码遗失\"}"))
                .andExpect(status().isServiceUnavailable());

        verify(authService).submitPasswordRecoveryTicket(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("首次激活缺少一次性手机验证码或初始密码时拒绝请求")
    void firstActivationRequiresPhoneChallengeAndInitialPassword() throws Exception {
        mockMvc.perform(post("/api/v1/elder/activation/consume")
                        .contentType(APPLICATION_JSON)
                        .content("{\"ticketId\":\"ticket-test\",\"activationCode\":\"654321\",\"phone\":\"13000000077\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("公开子女注册缺少一次性手机挑战时拒绝请求")
    void childRegistrationRequiresPhoneChallengeFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register-child")
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"13000000077\",\"password\":\"synthetic-new-password-2026\",\"name\":\"测试子女\"}"))
                .andExpect(status().isUnprocessableEntity());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("没有可信短信渠道时公开挑战签发入口返回不可用且不回显验证码")
    void phoneChallengeIssuanceFailsClosedWithoutSmsProvider() throws Exception {
        when(phoneVerificationChallengeService.issue(
                "13000000077", "ACTIVATION", "ticket-test", null))
                .thenThrow(new BusinessException(ErrorCode.PHONE_VERIFICATION_UNAVAILABLE,
                        ErrorCode.PHONE_VERIFICATION_UNAVAILABLE.getDefaultMessage()));

        mockMvc.perform(post("/api/v1/auth/phone-challenges")
                        .contentType(APPLICATION_JSON)
                        .content("{\"phone\":\"13000000077\",\"purpose\":\"ACTIVATION\",\"relatedRequestId\":\"ticket-test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        verify(phoneVerificationChallengeService).issue(
                "13000000077", "ACTIVATION", "ticket-test", null);
    }
}
