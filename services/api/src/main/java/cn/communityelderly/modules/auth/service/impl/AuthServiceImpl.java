package cn.communityelderly.modules.auth.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.UnauthorizedException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.dto.*;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Duration WEB_ABSOLUTE_LIFETIME = Duration.ofHours(12);
    private static final Duration MINIAPP_ABSOLUTE_LIFETIME = Duration.ofDays(30);
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern BCRYPT_HASH = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthSessionMapper authSessionMapper;
    private final AuthRateLimitService rateLimitService;
    private final PhoneVerificationChallengeService phoneVerificationChallengeService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());

    @Autowired
    public AuthServiceImpl(UserAccountMapper userAccountMapper,
                           UserRoleMapper userRoleMapper,
                           AuthSessionMapper authSessionMapper,
                           AuthRateLimitService rateLimitService,
                           PhoneVerificationChallengeService phoneVerificationChallengeService) {
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
        this.authSessionMapper = authSessionMapper;
        this.rateLimitService = rateLimitService;
        this.phoneVerificationChallengeService = phoneVerificationChallengeService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse login(LoginRequest request) {
        return loginInternal(request, "unknown");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse login(LoginRequest request, String sourceIp) {
        return loginInternal(request, sourceIp);
    }

    private AuthTokenResponse loginInternal(LoginRequest request, String sourceIp) {
        String phone = request.getPhone().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();
        rateLimitService.assertLoginAllowed(phone, sourceIp);

        UserAccount user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getPhoneNormalized, phone)
        );

        boolean passwordMatched = matchesPassword(password,
                user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus()) || !passwordMatched) {
            if (rateLimitService.recordFailure(phone, sourceIp)) {
                throw new BusinessException(ErrorCode.AUTH_COOLDOWN, ErrorCode.AUTH_COOLDOWN.getDefaultMessage());
            }
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
        }

        List<UserRole> activeRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, user.getUserId())
                        .eq(UserRole::getStatus, "ACTIVE")
                        .orderByAsc(UserRole::getAssignmentId)
        );
        if (activeRoles == null || activeRoles.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "账号未分配有效业务角色");
        }

        UserRole primaryRole = activeRoles.get(0);
        if (primaryRole.getAssignmentId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "账号角色分配无效，请联系管理员");
        }
        List<String> roleCodes = activeRoles.stream()
                .map(UserRole::getRoleCode)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (roleCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "账号未分配有效业务角色");
        }

        String clientType = normalizeClientType(request.getClientType());
        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        LocalDateTime expiresAt = "WEB".equals(clientType)
                ? now.plus(WEB_ABSOLUTE_LIFETIME)
                : now.plus(MINIAPP_ABSOLUTE_LIFETIME);
        String rawToken = UUID.randomUUID().toString().replace("-", "");
        String csrfToken = "WEB".equals(clientType)
                ? UUID.randomUUID().toString().replace("-", "")
                : null;
        AuthSession session = new AuthSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(user.getUserId());
        session.setTokenHash(sha256(rawToken));
        session.setClientType(clientType);
        session.setActiveAssignmentId(primaryRole.getAssignmentId());
        session.setCreatedAt(now);
        session.setLastActivityAt(now);
        session.setAbsoluteExpiresAt(expiresAt);
        if (csrfToken != null) {
            session.setCsrfTokenHash(sha256(csrfToken));
        }
        authSessionMapper.insert(session);

        rateLimitService.resetPhoneFailures(phone);
        String communityId = primaryRole.getCommunityId() == null
                ? null
                : String.valueOf(primaryRole.getCommunityId());
        AuthTokenResponse response = new AuthTokenResponse(
                rawToken,
                user.getUserId(),
                user.getPhoneNormalized(),
                user.getAccountKind(),
                roleCodes,
                primaryRole.getRoleCode(),
                communityId,
                expiresAt
        );
        response.setClientType(clientType);
        response.setCsrfToken(csrfToken);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse registerChild(RegisterChildRequest request) {
        return registerChild(request, "unknown");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse registerChild(RegisterChildRequest request, String sourceIp) {
        String phone = request.getPhone().trim();
        String challengeId = request.getPhoneChallengeId();
        String verificationCode = request.getVerificationCode();
        if (!StringUtils.hasText(challengeId) || !StringUtils.hasText(verificationCode)
                || !verificationCode.matches("^\\d{6}$")) {
            throw new BusinessException(ErrorCode.VERIFICATION_FAILED,
                    ErrorCode.VERIFICATION_FAILED.getDefaultMessage());
        }
        phoneVerificationChallengeService.verifyAndConsume(
                challengeId, phone, "CHILD_REGISTRATION", phone, verificationCode);
        String password = request.getPassword();
        validateNewPassword(password);
        Long existingCount = userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getPhoneNormalized, phone)
        );
        if (existingCount != null && existingCount > 0) {
            throw new BusinessException(ErrorCode.PHONE_NUMBER_EXISTS, "该手机号已被注册，请直接登录");
        }

        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        UserAccount user = new UserAccount();
        user.setAccountKind("FAMILY");
        user.setPhoneNormalized(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setStatus("ACTIVE");
        user.setPasswordChangedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userAccountMapper.insert(user);

        UserRole childRole = new UserRole();
        childRole.setUserId(user.getUserId());
        childRole.setRoleCode("CHILD");
        childRole.setStatus("ACTIVE");
        childRole.setGrantedBy(user.getUserId());
        childRole.setGrantedAt(now);
        userRoleMapper.insert(childRole);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhone(phone);
        loginRequest.setPassword(password);
        loginRequest.setClientType("MINIAPP");
        return loginInternal(loginRequest, sourceIp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        AuthSession session = authSessionMapper.selectOne(
                new LambdaQueryWrapper<AuthSession>()
                        .eq(AuthSession::getTokenHash, sha256(token))
                        .last("LIMIT 1")
        );
        if (session != null && session.getRevokedAt() == null) {
            session.setRevokedAt(LocalDateTime.now(SERVER_ZONE));
            session.setRevokeReason("LOGOUT");
            authSessionMapper.updateById(session);
        }
    }

    @Override
    public CurrentUser getCurrentUser() {
        CurrentUser user = UserContext.get();
        if (user == null) {
            throw new UnauthorizedException("未登录或会话已失效");
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeRequest request) {
        CurrentUser current = getCurrentUser();
        if (!StringUtils.hasText(current.getSessionId())) {
            throw new UnauthorizedException("当前会话无效，请重新登录");
        }
        UserAccount account = userAccountMapper.selectById(Long.parseLong(current.getUserId()));
        if (account == null || !"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new UnauthorizedException("当前会话无效，请重新登录");
        }
        if (!matchesPassword(request.getCurrentPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, ErrorCode.INVALID_CREDENTIALS.getDefaultMessage());
        }
        validateNewPassword(request.getNewPassword());
        if (matchesPassword(request.getNewPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码须与当前密码不同");
        }

        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setPasswordChangedAt(now);
        account.setUpdatedAt(now);
        if (userAccountMapper.updateById(account) < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "密码更新失败，请重试");
        }

        int revoked = authSessionMapper.update(null,
                new UpdateWrapper<AuthSession>()
                        .eq("user_id", account.getUserId())
                        .isNull("revoked_at")
                        .set("revoked_at", now)
                        .set("revoke_reason", "PASSWORD_RESET"));
        if (revoked < 1) {
            throw new UnauthorizedException("当前会话无效，请重新登录");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthTokenResponse switchRole(SwitchRoleRequest request) {
        CurrentUser current = getCurrentUser();
        String targetRole = request.getTargetRoleCode().trim().toUpperCase();
        if (!current.getRoles().contains(targetRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "您尚未获得该业务角色的授权");
        }

        List<UserRole> targetAssignments = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, Long.parseLong(current.getUserId()))
                        .eq(UserRole::getRoleCode, targetRole)
                        .eq(UserRole::getStatus, "ACTIVE")
                        .orderByAsc(UserRole::getAssignmentId)
        );
        if (targetAssignments == null || targetAssignments.size() != 1) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "目标角色分配不存在或不唯一");
        }
        UserRole targetAssignment = targetAssignments.get(0);
        if (targetAssignment.getAssignmentId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "目标角色分配无效");
        }

        AuthSession session = current.getSessionId() == null
                ? null
                : authSessionMapper.selectById(current.getSessionId());
        if (session == null || session.getRevokedAt() != null
                || !current.getUserId().equals(String.valueOf(session.getUserId()))) {
            throw new UnauthorizedException("会话已失效，请重新登录");
        }
        session.setActiveAssignmentId(targetAssignment.getAssignmentId());
        session.setLastActivityAt(LocalDateTime.now(SERVER_ZONE));
        if (authSessionMapper.updateById(session) < 1) {
            throw new UnauthorizedException("会话已失效，请重新登录");
        }

        String communityId = targetAssignment.getCommunityId() == null
                ? null
                : String.valueOf(targetAssignment.getCommunityId());
        CurrentUser updatedUser = new CurrentUser(
                current.getUserId(), current.getPhone(), current.getAccountKind(), current.getRoles(),
                targetAssignment.getRoleCode(), communityId, targetAssignment.getAssignmentId(), session.getSessionId());
        UserContext.set(updatedUser);

        AuthTokenResponse response = new AuthTokenResponse(
                null,
                Long.parseLong(updatedUser.getUserId()),
                updatedUser.getPhone(),
                updatedUser.getAccountKind(),
                updatedUser.getRoles(),
                updatedUser.getCurrentRole(),
                updatedUser.getCommunityId(),
                session.getAbsoluteExpiresAt()
        );
        response.setClientType(session.getClientType());
        return response;
    }

    @Override
    public String submitPasswordRecoveryTicket(PasswordRecoveryTicketDTO request) {
        throw new BusinessException(
                ErrorCode.RECOVERY_UNAVAILABLE,
                ErrorCode.RECOVERY_UNAVAILABLE.getDefaultMessage()
        );
    }

    private String normalizeClientType(String clientType) {
        String normalized = StringUtils.hasText(clientType) ? clientType.trim().toUpperCase() : "MINIAPP";
        if (!"WEB".equals(normalized) && !"MINIAPP".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "客户端类型无效");
        }
        return normalized;
    }

    private boolean matchesPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || rawPassword.codePointCount(0, rawPassword.length()) > 128
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            passwordEncoder.matches("", dummyPasswordHash);
            return false;
        }
        if (!StringUtils.hasText(storedHash) || !BCRYPT_HASH.matcher(storedHash).matches()) {
            passwordEncoder.matches("", dummyPasswordHash);
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void validateNewPassword(String password) {
        if (!StringUtils.hasText(password) || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "密码长度须为至少8个字符且不超过 BCrypt 支持的长度");
        }
    }

    private byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
