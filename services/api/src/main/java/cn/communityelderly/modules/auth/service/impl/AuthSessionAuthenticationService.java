package cn.communityelderly.modules.auth.service.impl;

import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class AuthSessionAuthenticationService {

    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration WEB_IDLE_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration MINIAPP_IDLE_TIMEOUT = Duration.ofDays(7);

    private final AuthSessionMapper authSessionMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;

    public AuthSessionAuthenticationService(AuthSessionMapper authSessionMapper,
                                            UserAccountMapper userAccountMapper,
                                            UserRoleMapper userRoleMapper) {
        this.authSessionMapper = authSessionMapper;
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
    }

    public CurrentUser authenticate(String rawToken, String requestUri) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }

        AuthSession session = authSessionMapper.selectOne(
                new LambdaQueryWrapper<AuthSession>()
                        .eq(AuthSession::getTokenHash, sha256(rawToken))
                        .last("LIMIT 1")
        );
        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        if (session == null || session.getRevokedAt() != null
                || session.getCreatedAt() == null
                || session.getAbsoluteExpiresAt() == null
                || !now.isBefore(session.getAbsoluteExpiresAt())
                || !hasRecentActivity(session, now)) {
            return null;
        }

        Duration idleTimeout = idleTimeout(session.getClientType());
        if (idleTimeout == null) {
            return null;
        }

        UserAccount account = userAccountMapper.selectById(session.getUserId());
        if (account == null || !"ACTIVE".equalsIgnoreCase(account.getStatus())
                || (account.getPasswordChangedAt() != null
                && session.getCreatedAt().isBefore(account.getPasswordChangedAt()))) {
            return null;
        }

        List<UserRole> assignments = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, account.getUserId())
                        .eq(UserRole::getStatus, "ACTIVE")
                        .orderByAsc(UserRole::getAssignmentId)
        );
        if (assignments == null || assignments.isEmpty() || session.getActiveAssignmentId() == null) {
            return null;
        }

        UserRole activeAssignment = assignments.stream()
                .filter(role -> session.getActiveAssignmentId().equals(role.getAssignmentId()))
                .findFirst()
                .orElse(null);
        if (activeAssignment == null || !StringUtils.hasText(activeAssignment.getRoleCode())) {
            return null;
        }

        if (!isNotificationPoll(requestUri)) {
            session.setLastActivityAt(now);
            authSessionMapper.updateById(session);
        }

        List<String> activeRoleCodes = assignments.stream()
                .map(UserRole::getRoleCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        String communityId = activeAssignment.getCommunityId() == null
                ? null
                : String.valueOf(activeAssignment.getCommunityId());

        return new CurrentUser(
                String.valueOf(account.getUserId()),
                account.getPhoneNormalized(),
                account.getAccountKind(),
                activeRoleCodes,
                activeAssignment.getRoleCode(),
                communityId,
                activeAssignment.getAssignmentId(),
                session.getSessionId(),
                session.getClientType()
        );
    }

    public boolean isCsrfTokenValid(String sessionId, String csrfToken) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(csrfToken)) {
            return false;
        }
        AuthSession session = authSessionMapper.selectById(sessionId);
        if (session == null || session.getRevokedAt() != null || session.getCsrfTokenHash() == null) {
            return false;
        }
        return MessageDigest.isEqual(session.getCsrfTokenHash(), sha256(csrfToken));
    }

    private boolean hasRecentActivity(AuthSession session, LocalDateTime now) {
        Duration idleTimeout = idleTimeout(session.getClientType());
        LocalDateTime lastActivityAt = session.getLastActivityAt();
        return idleTimeout != null && lastActivityAt != null
                && now.isBefore(lastActivityAt.plus(idleTimeout));
    }

    private Duration idleTimeout(String clientType) {
        if ("WEB".equalsIgnoreCase(clientType)) {
            return WEB_IDLE_TIMEOUT;
        }
        if ("MINIAPP".equalsIgnoreCase(clientType)) {
            return MINIAPP_IDLE_TIMEOUT;
        }
        return null;
    }

    private boolean isNotificationPoll(String requestUri) {
        return requestUri != null && requestUri.startsWith("/api/v1/notifications/");
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
