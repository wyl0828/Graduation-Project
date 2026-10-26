package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.dto.LoginRequest;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import cn.communityelderly.modules.auth.service.impl.AuthServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthPasswordVerificationTest {

    private UserAccountMapper userAccountMapper;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        userAccountMapper = mock(UserAccountMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        AuthSessionMapper authSessionMapper = mock(AuthSessionMapper.class);
        AuthRateLimitService authRateLimitService = mock(AuthRateLimitService.class);
        authService = new AuthServiceImpl(userAccountMapper, userRoleMapper, authSessionMapper,
                authRateLimitService, mock(cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService.class));
    }

    @Test
    @DisplayName("不支持的旧哈希不能通过通用密码兜底登录")
    void unsupportedPasswordHashIsRejectedWithoutFallback() {
        UserAccount account = new UserAccount();
        account.setUserId(77L);
        account.setPhoneNormalized("13000000077");
        account.setAccountKind("FAMILY");
        account.setPasswordHash("legacy-format");
        account.setStatus("ACTIVE");
        when(userAccountMapper.selectOne(any(Wrapper.class))).thenReturn(account);

        LoginRequest request = new LoginRequest();
        request.setPhone(account.getPhoneNormalized());
        request.setPassword("123456");

        BusinessException error = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
    }
}
