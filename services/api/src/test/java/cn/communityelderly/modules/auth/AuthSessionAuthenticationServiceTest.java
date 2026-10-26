package cn.communityelderly.modules.auth;

import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionAuthenticationServiceTest {

    private static final String RAW_TOKEN = "opaque-session-for-test";
    private AuthSessionMapper sessionMapper;
    private UserAccountMapper accountMapper;
    private UserRoleMapper roleMapper;
    private AuthSessionAuthenticationService service;
    private AuthSession session;
    private UserAccount account;
    private UserRole role;
    private LocalDateTime now;

    @BeforeEach
    void setUp() throws Exception {
        sessionMapper = mock(AuthSessionMapper.class);
        accountMapper = mock(UserAccountMapper.class);
        roleMapper = mock(UserRoleMapper.class);
        service = new AuthSessionAuthenticationService(sessionMapper, accountMapper, roleMapper);
        now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(42L);
        session.setTokenHash(MessageDigest.getInstance("SHA-256").digest(RAW_TOKEN.getBytes(StandardCharsets.UTF_8)));
        session.setClientType("WEB");
        session.setActiveAssignmentId(700L);
        session.setCreatedAt(now.minusHours(1));
        session.setLastActivityAt(now.minusMinutes(1));
        session.setAbsoluteExpiresAt(now.plusHours(1));
        session.setCsrfTokenHash(MessageDigest.getInstance("SHA-256")
                .digest("csrf-token-for-test".getBytes(StandardCharsets.UTF_8)));

        account = new UserAccount();
        account.setUserId(42L);
        account.setPhoneNormalized("13800000042");
        account.setAccountKind("STAFF");
        account.setStatus("ACTIVE");

        role = new UserRole();
        role.setAssignmentId(700L);
        role.setUserId(42L);
        role.setRoleCode("COMMUNITY_ADMIN");
        role.setCommunityId(77L);
        role.setStatus("ACTIVE");

        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(session);
        when(sessionMapper.selectById("session-1")).thenReturn(session);
        when(accountMapper.selectById(42L)).thenReturn(account);
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        when(sessionMapper.updateById(any(AuthSession.class))).thenReturn(1);
    }

    @Test
    @DisplayName("每个请求按 token 摘要读取数据库会话并装载活动分配")
    void authenticatesOnlyFromPersistedSessionAndActiveAssignment() {
        CurrentUser user = service.authenticate(RAW_TOKEN, "/api/v1/dashboard/community/summary");

        assertEquals("42", user.getUserId());
        assertEquals("COMMUNITY_ADMIN", user.getCurrentRole());
        assertEquals("77", user.getCommunityId());
        assertEquals(700L, user.getActiveAssignmentId());
        assertEquals("WEB", user.getClientType());
        verify(sessionMapper).updateById(session);
    }

    @Test
    @DisplayName("Web CSRF 校验按 session 读取摘要并使用常量时间比较")
    void validatesOnlyMatchingPersistedCsrfToken() {
        assertTrue(service.isCsrfTokenValid("session-1", "csrf-token-for-test"));
        assertFalse(service.isCsrfTokenValid("session-1", "wrong-csrf-token"));

        session.setRevokedAt(now);
        assertFalse(service.isCsrfTokenValid("session-1", "csrf-token-for-test"));
    }

    @Test
    @DisplayName("绝对期限到达时拒绝会话")
    void rejectsSessionAtAbsoluteExpiry() {
        session.setAbsoluteExpiresAt(now.minusSeconds(1));

        assertNull(service.authenticate(RAW_TOKEN, "/api/v1/dashboard/community/summary"));
    }

    @Test
    @DisplayName("撤销会话后立即拒绝新请求")
    void rejectsRevokedSession() {
        session.setRevokedAt(now.minusSeconds(1));

        assertNull(service.authenticate(RAW_TOKEN, "/api/v1/dashboard/community/summary"));
    }

    @Test
    @DisplayName("停用账号后立即拒绝已有会话")
    void rejectsDisabledAccount() {
        account.setStatus("DISABLED");

        assertNull(service.authenticate(RAW_TOKEN, "/api/v1/dashboard/community/summary"));
    }

    @Test
    @DisplayName("密码更新时间晚于会话创建时间时立即拒绝旧会话")
    void rejectsSessionCreatedBeforePasswordChange() {
        session.setCreatedAt(now.minusHours(1));
        account.setPasswordChangedAt(now.minusMinutes(1));

        assertNull(service.authenticate(RAW_TOKEN, "/api/v1/dashboard/community/summary"));
    }

    @Test
    @DisplayName("活动角色分配撤销后立即拒绝旧会话")
    void rejectsSessionWhoseAssignmentIsNoLongerActive() {
        role.setAssignmentId(701L);

        assertNull(service.authenticate(RAW_TOKEN, "/api/v1/dashboard/community/summary"));
    }

    @Test
    @DisplayName("Web 闲置达到 30 分钟时拒绝会话")
    void rejectsIdleWebSessionAtBoundary() {
        session.setLastActivityAt(now.minusMinutes(30));

        assertNull(service.authenticate(RAW_TOKEN, "/api/v1/dashboard/community/summary"));
    }

    @Test
    @DisplayName("通知短轮询不会延长会话闲置期限")
    void notificationPollDoesNotExtendIdleExpiry() {
        LocalDateTime previousActivity = session.getLastActivityAt();
        CurrentUser user = service.authenticate(RAW_TOKEN, "/api/v1/notifications/unread-count");

        assertEquals("42", user.getUserId());
        assertEquals(previousActivity, session.getLastActivityAt());
        assertFalse(session.getLastActivityAt().isAfter(previousActivity));
    }
}
