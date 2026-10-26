package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.dto.PasswordRecoveryTicketDTO;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import cn.communityelderly.modules.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthPasswordRecoveryUnavailableTest {

    private UserAccountMapper accountMapper;
    private AuthSessionMapper sessionMapper;
    private AuthRateLimitService rateLimitService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        accountMapper = mock(UserAccountMapper.class);
        sessionMapper = mock(AuthSessionMapper.class);
        UserRoleMapper roleMapper = mock(UserRoleMapper.class);
        rateLimitService = mock(AuthRateLimitService.class);
        authService = new AuthServiceImpl(accountMapper, roleMapper, sessionMapper, rateLimitService,
                mock(cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService.class));
    }

    @Test
    @DisplayName("恢复渠道未配置时拒绝请求，不创建工单、不改密码或会话")
    void recoveryIsUnavailableWithoutChangingCredentialsOrSessions() {
        PasswordRecoveryTicketDTO request = new PasswordRecoveryTicketDTO();
        request.setPhone("13000000077");
        request.setRealName("测试用户");
        request.setAppealReason("测试原因");

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> authService.submitPasswordRecoveryTicket(request));

        assertEquals(ErrorCode.RECOVERY_UNAVAILABLE, error.getErrorCode());
        verifyNoInteractions(accountMapper, sessionMapper, rateLimitService);
    }
}
