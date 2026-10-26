package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.entity.PhoneVerificationChallenge;
import cn.communityelderly.modules.auth.mapper.PhoneVerificationChallengeMapper;
import cn.communityelderly.modules.auth.service.VerificationCodeSender;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeAttemptService;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhoneVerificationChallengeServiceTest {

    private static final String TEST_HMAC_KEY = "test-only-hmac-key-with-at-least-32-characters";
    private static final String CHALLENGE_ID = "challenge-00000000-0000-0000-0000-000000000001";
    private static final String PHONE = "13000000077";
    private static final String PURPOSE = "ACTIVATION";
    private static final String TICKET_ID = "ticket-00000000-0000-0000-0000-000000000001";
    private static final String CODE = "654321";

    private PhoneVerificationChallengeMapper mapper;
    private PhoneVerificationChallengeAttemptService attemptService;
    private VerificationCodeSender codeSender;
    private PhoneVerificationChallengeService service;
    private PhoneVerificationChallenge challenge;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(PhoneVerificationChallengeMapper.class);
        attemptService = mock(PhoneVerificationChallengeAttemptService.class);
        codeSender = mock(VerificationCodeSender.class);
        service = new PhoneVerificationChallengeService(mapper, attemptService, codeSender, TEST_HMAC_KEY);

        challenge = new PhoneVerificationChallenge();
        challenge.setChallengeId(CHALLENGE_ID);
        challenge.setPhoneNormalized(PHONE);
        challenge.setPurpose(PURPOSE);
        challenge.setRelatedRequestId(TICKET_ID);
        challenge.setCodeMac(hmac(TEST_HMAC_KEY, CHALLENGE_ID + PHONE + PURPOSE + CODE));
        challenge.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusMinutes(1));
        challenge.setExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(4));
        challenge.setAttemptCount(0);
        challenge.setStatus("PENDING");
        when(mapper.selectById(CHALLENGE_ID)).thenReturn(challenge);
        when(mapper.consumeIfPending(eq(CHALLENGE_ID), eq(PHONE), eq(PURPOSE), eq(TICKET_ID),
                any(byte[].class), any(LocalDateTime.class))).thenReturn(1);
    }

    @Test
    @DisplayName("正确验证码须匹配手机号、用途和激活票据并原子消费一次")
    void consumesMatchingChallengeOnce() throws Exception {
        service.verifyAndConsume(CHALLENGE_ID, PHONE, PURPOSE, TICKET_ID, CODE);

        ArgumentCaptor<byte[]> mac = ArgumentCaptor.forClass(byte[].class);
        verify(mapper).consumeIfPending(eq(CHALLENGE_ID), eq(PHONE), eq(PURPOSE), eq(TICKET_ID),
                mac.capture(), any(LocalDateTime.class));
        assertArrayEquals(hmac(TEST_HMAC_KEY, CHALLENGE_ID + PHONE + PURPOSE + CODE), mac.getValue());
    }

    @Test
    @DisplayName("错误验证码计入失败次数并拒绝激活")
    void wrongCodeRecordsFailureAndRejects() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(CHALLENGE_ID, PHONE, PURPOSE, TICKET_ID, "111111"));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verify(attemptService).recordFailure(eq(CHALLENGE_ID), eq(PHONE), eq(PURPOSE),
                eq(TICKET_ID), any(LocalDateTime.class));
        verify(mapper, org.mockito.Mockito.never()).consumeIfPending(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("已过期或绑定信息不符的挑战拒绝且不消费")
    void expiredOrMismatchedChallengeDoesNotConsume() {
        challenge.setExpiresAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(1));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(CHALLENGE_ID, PHONE, PURPOSE, TICKET_ID, CODE));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verifyNoInteractions(attemptService);
        verify(mapper, org.mockito.Mockito.never()).consumeIfPending(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("缺少服务器 HMAC 密钥时验证码核验 fail-closed")
    void missingKeyRejectsWithoutReadingChallenge() {
        service = new PhoneVerificationChallengeService(mapper, attemptService, codeSender, "");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(CHALLENGE_ID, PHONE, PURPOSE, TICKET_ID, CODE));

        assertEquals(ErrorCode.AUTH_CONFIGURATION_UNAVAILABLE, error.getErrorCode());
        verifyNoInteractions(mapper, attemptService);
    }

    @Test
    @DisplayName("未接入可信短信渠道时手机号挑战创建明确不可用且不写数据库")
    void disabledPhoneVerificationCannotIssueChallenge() {
        service = new PhoneVerificationChallengeService(
                mapper, attemptService, codeSender, TEST_HMAC_KEY, false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.issue(PHONE, "CHILD_REGISTRATION", PHONE, null));

        assertEquals(ErrorCode.PHONE_VERIFICATION_UNAVAILABLE, error.getErrorCode());
        verifyNoInteractions(mapper, attemptService, codeSender);
    }

    @Test
    @DisplayName("关闭手机号验证时即使提交挑战编号和验证码也不能核验消费")
    void disabledPhoneVerificationCannotConsumeChallenge() {
        service = new PhoneVerificationChallengeService(
                mapper, attemptService, codeSender, TEST_HMAC_KEY, false);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(
                        CHALLENGE_ID, PHONE, "CHILD_REGISTRATION", PHONE, CODE));

        assertEquals(ErrorCode.PHONE_VERIFICATION_UNAVAILABLE, error.getErrorCode());
        verifyNoInteractions(mapper, attemptService, codeSender);
    }

    @Test
    @DisplayName("其他手机号绑定的挑战不能验证子女注册")
    void challengeBoundToAnotherPhoneCannotBeConsumedForRegistration() {
        challenge.setPurpose("CHILD_REGISTRATION");
        challenge.setRelatedRequestId(PHONE);
        challenge.setPhoneNormalized("13000000078");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(
                        CHALLENGE_ID, PHONE, "CHILD_REGISTRATION", PHONE, CODE));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verifyNoInteractions(attemptService);
        verify(mapper, org.mockito.Mockito.never()).consumeIfPending(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("其他用途绑定的挑战不能验证子女注册")
    void challengeBoundToAnotherPurposeCannotBeConsumedForRegistration() {
        challenge.setRelatedRequestId(PHONE);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(
                        CHALLENGE_ID, PHONE, "CHILD_REGISTRATION", PHONE, CODE));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verifyNoInteractions(attemptService);
        verify(mapper, org.mockito.Mockito.never()).consumeIfPending(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("不存在的伪造挑战编号拒绝核验且不消费")
    void forgedChallengeIdCannotBeConsumed() {
        when(mapper.selectById(CHALLENGE_ID)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.verifyAndConsume(CHALLENGE_ID, PHONE, PURPOSE, TICKET_ID, CODE));

        assertEquals(ErrorCode.VERIFICATION_FAILED, error.getErrorCode());
        verifyNoInteractions(attemptService);
        verify(mapper, org.mockito.Mockito.never()).consumeIfPending(
                any(), any(), any(), any(), any(), any());
    }

    private byte[] hmac(String key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update("PHONE_CHALLENGE:".getBytes(StandardCharsets.UTF_8));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }
}
