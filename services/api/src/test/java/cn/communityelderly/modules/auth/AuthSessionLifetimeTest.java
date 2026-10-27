package cn.communityelderly.modules.auth;

import cn.communityelderly.modules.auth.dto.AuthTokenResponse;
import cn.communityelderly.modules.auth.dto.LoginRequest;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import cn.communityelderly.modules.auth.service.impl.AuthServiceImpl;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthSessionLifetimeTest {

    private UserAccountMapper accountMapper;
    private UserRoleMapper roleMapper;
    private AuthSessionMapper sessionMapper;
    private AuthServiceImpl authService;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        accountMapper = mock(UserAccountMapper.class);
        roleMapper = mock(UserRoleMapper.class);
        sessionMapper = mock(AuthSessionMapper.class);
        authService = new AuthServiceImpl(
                accountMapper, roleMapper, sessionMapper, mock(AuthRateLimitService.class),
                mock(PhoneVerificationChallengeService.class));

        account = new UserAccount();
        account.setUserId(42L);
        account.setPhoneNormalized("13800000042");
        account.setAccountKind("STAFF");
        account.setPasswordHash(new BCryptPasswordEncoder().encode("synthetic-login-password"));
        account.setStatus("ACTIVE");
        when(accountMapper.selectOne(any(Wrapper.class))).thenReturn(account);

        UserRole assignment = new UserRole();
        assignment.setAssignmentId(700L);
        assignment.setUserId(42L);
        assignment.setRoleCode("COMMUNITY_ADMIN");
        assignment.setCommunityId(77L);
        assignment.setStatus("ACTIVE");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(assignment));
    }

    @Test
    @DisplayName("Web 登录建立 12 小时绝对期限的 BCrypt 会话")
    void webSessionExpiresAfterTwelveHours() {
        LocalDateTime beforeLogin = LocalDateTime.now();
        AuthTokenResponse response = authService.login(loginRequest("WEB"), "192.0.2.77");
        LocalDateTime afterLogin = LocalDateTime.now();

        ArgumentCaptor<AuthSession> session = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionMapper).insert(session.capture());
        assertNotNull(response.getToken());
        assertEquals(Duration.ofHours(12), Duration.between(session.getValue().getCreatedAt(), session.getValue().getAbsoluteExpiresAt()));
        assertEquals("WEB", session.getValue().getClientType());
        assertEquals("WEB", response.getClientType());
        assertEquals(700L, session.getValue().getActiveAssignmentId());
        assertNotNull(session.getValue().getTokenHash());
        assertNotNull(session.getValue().getCsrfTokenHash());
        assertNotNull(response.getCsrfToken());
        assertNotNull(response.getToken());
        assertEquals("77", response.getCommunityId());
        assertEquals(true, !session.getValue().getCreatedAt().isBefore(beforeLogin)
                && !session.getValue().getCreatedAt().isAfter(afterLogin));
    }

    @Test
    @DisplayName("小程序登录建立 30 天绝对期限的 BCrypt 会话")
    void miniappSessionExpiresAfterThirtyDays() {
        AuthTokenResponse response = authService.login(loginRequest("MINIAPP"), "192.0.2.77");

        ArgumentCaptor<AuthSession> session = ArgumentCaptor.forClass(AuthSession.class);
        verify(sessionMapper).insert(session.capture());
        assertEquals(Duration.ofDays(30), Duration.between(session.getValue().getCreatedAt(), session.getValue().getAbsoluteExpiresAt()));
        assertEquals("MINIAPP", session.getValue().getClientType());
        assertEquals("MINIAPP", response.getClientType());
        assertNull(session.getValue().getCsrfTokenHash());
        assertNull(response.getCsrfToken());
        assertNotNull(response.getToken());
    }

    @Test
    @DisplayName("Web 会话空闲达到 30 分钟后被拒绝且不刷新会话")
    void webSessionIsRejectedAfterThirtyMinutesIdleWithoutTouchingSession() {
        AuthSessionMapper sessions = mock(AuthSessionMapper.class);
        UserAccountMapper accounts = mock(UserAccountMapper.class);
        UserRoleMapper roles = mock(UserRoleMapper.class);
        AuthSessionAuthenticationService authenticationService = new AuthSessionAuthenticationService(
                sessions, accounts, roles);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));

        AuthSession idleSession = new AuthSession();
        idleSession.setSessionId("idle-web-session");
        idleSession.setUserId(42L);
        idleSession.setClientType("WEB");
        idleSession.setActiveAssignmentId(700L);
        idleSession.setCreatedAt(now.minusHours(1));
        idleSession.setLastActivityAt(now.minusMinutes(30));
        idleSession.setAbsoluteExpiresAt(now.plusHours(1));
        when(sessions.selectOne(any(Wrapper.class))).thenReturn(idleSession);

        assertNull(authenticationService.authenticate("idle-web-token", "/api/v1/auth/current-user"));

        verify(sessions, never()).updateById(any(AuthSession.class));
    }

    private LoginRequest loginRequest(String clientType) {
        LoginRequest request = new LoginRequest();
        request.setPhone(account.getPhoneNormalized());
        request.setPassword("synthetic-login-password");
        request.setClientType(clientType);
        return request;
    }
}
