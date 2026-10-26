package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.dto.PasswordChangeRequest;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import cn.communityelderly.modules.auth.service.impl.AuthServiceImpl;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthPasswordChangeTest {

    private UserAccountMapper accountMapper;
    private AuthSessionMapper sessionMapper;
    private AuthServiceImpl authService;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        accountMapper = mock(UserAccountMapper.class);
        sessionMapper = mock(AuthSessionMapper.class);
        UserRoleMapper roleMapper = mock(UserRoleMapper.class);
        authService = new AuthServiceImpl(accountMapper, roleMapper, sessionMapper, mock(AuthRateLimitService.class),
                mock(PhoneVerificationChallengeService.class));

        account = new UserAccount();
        account.setUserId(42L);
        account.setPhoneNormalized("13800000042");
        account.setAccountKind("STAFF");
        account.setPasswordHash(new BCryptPasswordEncoder().encode("synthetic-current-password"));
        account.setStatus("ACTIVE");
        when(accountMapper.selectById(42L)).thenReturn(account);
        when(accountMapper.updateById(any(UserAccount.class))).thenReturn(1);
        when(sessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(2);

        UserContext.set(new CurrentUser(
                "42", "13800000042", "STAFF", java.util.List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN", "77", 700L, "session-1"));
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("改密更新 BCrypt 哈希并撤销该账号全部会话")
    void changesPasswordAndRevokesAllSessions() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword("synthetic-current-password");
        request.setNewPassword("synthetic-new-password-2026");
        String oldHash = account.getPasswordHash();

        authService.changePassword(request);

        assertAll(
                () -> assertNotEquals(oldHash, account.getPasswordHash()),
                () -> assertTrue(new BCryptPasswordEncoder().matches(request.getNewPassword(), account.getPasswordHash())),
                () -> assertNotNull(account.getPasswordChangedAt())
        );
        verify(accountMapper).updateById(account);
        verify(sessionMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    @DisplayName("当前密码错误时不修改账号或会话")
    void rejectsWrongCurrentPasswordWithoutWriting() {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword("synthetic-wrong-password");
        request.setNewPassword("synthetic-new-password-2026");

        BusinessException error = assertThrows(BusinessException.class, () -> authService.changePassword(request));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
        verify(accountMapper, never()).updateById(any(UserAccount.class));
        verify(sessionMapper, never()).update(any(), any(Wrapper.class));
    }
}
