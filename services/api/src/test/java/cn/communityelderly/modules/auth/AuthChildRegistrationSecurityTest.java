package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.dto.RegisterChildRequest;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import cn.communityelderly.modules.auth.service.impl.AuthServiceImpl;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuthChildRegistrationSecurityTest {

    private static final String PHONE = "13000000077";
    private static final String CODE = "123456";

    private UserAccountMapper accountMapper;
    private UserRoleMapper roleMapper;
    private AuthSessionMapper sessionMapper;
    private AuthRateLimitService rateLimitService;
    private PhoneVerificationChallengeService challengeService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        accountMapper = mock(UserAccountMapper.class);
        roleMapper = mock(UserRoleMapper.class);
        sessionMapper = mock(AuthSessionMapper.class);
        rateLimitService = mock(AuthRateLimitService.class);
        challengeService = mock(PhoneVerificationChallengeService.class);
        authService = new AuthServiceImpl(accountMapper, roleMapper, sessionMapper,
                rateLimitService, challengeService);
    }

    @Test
    @DisplayName("缺少手机号挑战编号或验证码时拒绝注册且不访问身份表")
    void missingChallengeFieldsFailBeforeAnyIdentityLookupOrWrite() {
        RegisterChildRequest request = validRequest();
        request.setPhoneChallengeId(null);
        request.setVerificationCode(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.registerChild(request, "127.0.0.1"));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verifyNoInteractions(challengeService, accountMapper, roleMapper, sessionMapper, rateLimitService);
    }

    @Test
    @DisplayName("伪造或跨手机号挑战核验失败时不创建用户、角色或会话")
    void forgedOrCrossPhoneChallengeCannotClaimPhone() {
        RegisterChildRequest request = validRequest();
        request.setPhoneChallengeId("challenge-for-another-phone");
        doThrow(verificationFailed()).when(challengeService).verifyAndConsume(
                "challenge-for-another-phone", PHONE, "CHILD_REGISTRATION", PHONE, CODE);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.registerChild(request, "127.0.0.1"));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verify(challengeService).verifyAndConsume(
                "challenge-for-another-phone", PHONE, "CHILD_REGISTRATION", PHONE, CODE);
        verifyNoInteractions(accountMapper, roleMapper, sessionMapper, rateLimitService);
    }

    @Test
    @DisplayName("绑定其他用途的挑战不能用于子女注册且不创建用户、角色或会话")
    void challengeForAnotherPurposeCannotRegisterChild() {
        RegisterChildRequest request = validRequest();
        request.setPhoneChallengeId("activation-challenge");
        doThrow(verificationFailed()).when(challengeService).verifyAndConsume(
                "activation-challenge", PHONE, "CHILD_REGISTRATION", PHONE, CODE);

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.registerChild(request, "127.0.0.1"));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verify(challengeService).verifyAndConsume(
                "activation-challenge", PHONE, "CHILD_REGISTRATION", PHONE, CODE);
        verifyNoInteractions(accountMapper, roleMapper, sessionMapper, rateLimitService);
    }

    private RegisterChildRequest validRequest() {
        RegisterChildRequest request = new RegisterChildRequest();
        request.setPhone(PHONE);
        request.setPassword("safe-test-password-2026");
        request.setName("测试子女");
        request.setPhoneChallengeId("challenge-id");
        request.setVerificationCode(CODE);
        return request;
    }

    private BusinessException verificationFailed() {
        return new BusinessException(ErrorCode.VERIFICATION_FAILED,
                ErrorCode.VERIFICATION_FAILED.getDefaultMessage());
    }
}
