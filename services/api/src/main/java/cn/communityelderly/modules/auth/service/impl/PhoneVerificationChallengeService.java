package cn.communityelderly.modules.auth.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.entity.PhoneVerificationChallenge;
import cn.communityelderly.modules.auth.mapper.PhoneVerificationChallengeMapper;
import cn.communityelderly.modules.auth.service.VerificationCodeSender;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PhoneVerificationChallengeService {

    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern PHONE_CODE = Pattern.compile("^\\d{6}$");
    private static final Pattern PHONE_NUMBER = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Set<String> ALLOWED_PURPOSES = Set.of(
            "CHILD_REGISTRATION", "ACTIVATION", "PASSWORD_RESET", "PHONE_CHANGE_OLD", "PHONE_CHANGE_NEW");
    private static final int MAX_PENDING_PER_PHONE = 5;
    private static final byte[] HMAC_DOMAIN = "PHONE_CHALLENGE:".getBytes(StandardCharsets.UTF_8);

    private final PhoneVerificationChallengeMapper mapper;
    private final PhoneVerificationChallengeAttemptService attemptService;
    private final VerificationCodeSender verificationCodeSender;
    private final byte[] hmacKey;
    private final boolean enabled;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public PhoneVerificationChallengeService(PhoneVerificationChallengeMapper mapper,
                                             PhoneVerificationChallengeAttemptService attemptService,
                                             VerificationCodeSender verificationCodeSender,
                                             @Value("${AUTH_RATE_LIMIT_HMAC_KEY:}") String hmacKey,
                                             @Value("${PHONE_VERIFICATION_ENABLED:false}") boolean enabled) {
        this.mapper = mapper;
        this.attemptService = attemptService;
        this.verificationCodeSender = verificationCodeSender;
        this.hmacKey = hmacKey.getBytes(StandardCharsets.UTF_8);
        this.enabled = enabled;
    }

    /** Test constructor for exercising challenge validation independently of the provider gate. */
    public PhoneVerificationChallengeService(PhoneVerificationChallengeMapper mapper,
                                             PhoneVerificationChallengeAttemptService attemptService,
                                             VerificationCodeSender verificationCodeSender,
                                             String hmacKey) {
        this(mapper, attemptService, verificationCodeSender, hmacKey, true);
    }

    /** Creates a one-time challenge and sends its code through the configured provider. */
    @Transactional(rollbackFor = Exception.class)
    public IssuedChallenge issue(String phone, String purpose, String relatedRequestId, Long userId) {
        requireEnabled();
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedPurpose = purpose == null ? "" : purpose.trim().toUpperCase();
        String normalizedRelatedId = relatedRequestId == null ? "" : relatedRequestId.trim();
        if (!PHONE_NUMBER.matcher(normalizedPhone).matches()
                || !ALLOWED_PURPOSES.contains(normalizedPurpose)
                || !StringUtils.hasText(normalizedRelatedId)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "手机验证挑战参数无效");
        }
        requireHmacKey();

        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        Long recent = mapper.selectCount(new LambdaQueryWrapper<PhoneVerificationChallenge>()
                .eq(PhoneVerificationChallenge::getPhoneNormalized, normalizedPhone)
                .eq(PhoneVerificationChallenge::getPurpose, normalizedPurpose)
                .eq(PhoneVerificationChallenge::getStatus, "PENDING")
                .ge(PhoneVerificationChallenge::getCreatedAt, now.minusMinutes(10)));
        if (recent != null && recent >= MAX_PENDING_PER_PHONE) {
            throw new BusinessException(ErrorCode.AUTH_COOLDOWN, "验证码请求过于频繁，请稍后再试");
        }

        String challengeId = UUID.randomUUID().toString();
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        LocalDateTime expiresAt = now.plusMinutes(10);

        PhoneVerificationChallenge challenge = new PhoneVerificationChallenge();
        challenge.setChallengeId(challengeId);
        challenge.setUserId(userId);
        challenge.setPhoneNormalized(normalizedPhone);
        challenge.setPurpose(normalizedPurpose);
        challenge.setRelatedRequestId(normalizedRelatedId);
        challenge.setCodeMac(hmac(challengeId + normalizedPhone + normalizedPurpose + code));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(expiresAt);
        challenge.setAttemptCount(0);
        challenge.setStatus("PENDING");
        mapper.insert(challenge);

        verificationCodeSender.send(normalizedPhone, normalizedPurpose, code);
        return new IssuedChallenge(challengeId, expiresAt);
    }

    public record IssuedChallenge(String challengeId, LocalDateTime expiresAt) {
    }

    public void verifyAndConsume(String challengeId, String phone, String purpose,
                                 String relatedRequestId, String code) {
        requireEnabled();
        if (!StringUtils.hasText(challengeId) || !StringUtils.hasText(phone)
                || !StringUtils.hasText(purpose) || !StringUtils.hasText(relatedRequestId)
                || !StringUtils.hasText(code) || !PHONE_CODE.matcher(code).matches()) {
            throw verificationFailed();
        }
        requireHmacKey();

        String normalizedPhone = phone.trim();
        String normalizedPurpose = purpose.trim().toUpperCase();
        String normalizedRelatedId = relatedRequestId.trim();
        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        PhoneVerificationChallenge challenge = mapper.selectById(challengeId.trim());
        if (!matchesActiveChallenge(challenge, challengeId.trim(), normalizedPhone,
                normalizedPurpose, normalizedRelatedId, now)) {
            throw verificationFailed();
        }

        byte[] expectedMac = hmac(challengeId.trim() + normalizedPhone + normalizedPurpose + code);
        if (challenge.getCodeMac() == null || !MessageDigest.isEqual(expectedMac, challenge.getCodeMac())) {
            attemptService.recordFailure(challengeId.trim(), normalizedPhone,
                    normalizedPurpose, normalizedRelatedId, now);
            throw verificationFailed();
        }

        int consumed = mapper.consumeIfPending(challengeId.trim(), normalizedPhone,
                normalizedPurpose, normalizedRelatedId, expectedMac, now);
        if (consumed != 1) {
            throw verificationFailed();
        }
    }

    private boolean matchesActiveChallenge(PhoneVerificationChallenge challenge, String challengeId,
                                           String phone, String purpose, String relatedRequestId,
                                           LocalDateTime now) {
        return challenge != null
                && Objects.equals(challenge.getChallengeId(), challengeId)
                && Objects.equals(challenge.getPhoneNormalized(), phone)
                && Objects.equals(challenge.getPurpose(), purpose)
                && Objects.equals(challenge.getRelatedRequestId(), relatedRequestId)
                && "PENDING".equalsIgnoreCase(challenge.getStatus())
                && challenge.getConsumedAt() == null
                && challenge.getExpiresAt() != null
                && now.isBefore(challenge.getExpiresAt())
                && (challenge.getAttemptCount() == null || challenge.getAttemptCount() < 5);
    }

    private void requireHmacKey() {
        if (hmacKey.length < 32) {
            throw new BusinessException(
                    ErrorCode.AUTH_CONFIGURATION_UNAVAILABLE,
                    ErrorCode.AUTH_CONFIGURATION_UNAVAILABLE.getDefaultMessage()
            );
        }
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(ErrorCode.PHONE_VERIFICATION_UNAVAILABLE,
                    ErrorCode.PHONE_VERIFICATION_UNAVAILABLE.getDefaultMessage());
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            mac.update(HMAC_DOMAIN);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private BusinessException verificationFailed() {
        return new BusinessException(ErrorCode.VERIFICATION_FAILED,
                ErrorCode.VERIFICATION_FAILED.getDefaultMessage());
    }
}
