package cn.communityelderly.modules.auth.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.mapper.AuthRateLimitMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;

@Service
public class AuthRateLimitService {

    private static final int PHONE_FAILURE_LIMIT = 5;
    private static final int IP_FAILURE_LIMIT = 30;
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final byte[] PHONE_HMAC_DOMAIN = "PHONE:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IP_HMAC_DOMAIN = "IP:".getBytes(StandardCharsets.UTF_8);

    private final AuthRateLimitMapper mapper;
    private final byte[] hmacKey;

    public AuthRateLimitService(AuthRateLimitMapper mapper,
                                @Value("${AUTH_RATE_LIMIT_HMAC_KEY:}") String hmacKey) {
        this.mapper = mapper;
        this.hmacKey = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public void assertLoginAllowed(String normalizedPhone, String sourceIp) {
        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        if (isBlocked("PHONE", phoneHash(normalizedPhone), now)
                || isBlocked("IP", ipHash(sourceIp), now)) {
            throw cooldown();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordFailure(String normalizedPhone, String sourceIp) {
        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        LocalDateTime windowCutoff = now.minusMinutes(15);
        LocalDateTime cooldownUntil = now.plusMinutes(15);
        byte[] phoneHash = phoneHash(normalizedPhone);
        byte[] ipHash = ipHash(sourceIp);
        mapper.recordFailure("PHONE", phoneHash, now, windowCutoff, cooldownUntil, PHONE_FAILURE_LIMIT);
        mapper.recordFailure("IP", ipHash, now, windowCutoff, cooldownUntil, IP_FAILURE_LIMIT);
        return isBlocked("PHONE", phoneHash, now) || isBlocked("IP", ipHash, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void resetPhoneFailures(String normalizedPhone) {
        mapper.resetPhoneFailures(phoneHash(normalizedPhone), LocalDateTime.now(SERVER_ZONE));
    }

    private boolean isBlocked(String keyType, byte[] keyHash, LocalDateTime now) {
        return mapper.hasActiveCooldown(keyType, keyHash, now) > 0;
    }

    private byte[] phoneHash(String phone) {
        return hmac(PHONE_HMAC_DOMAIN, phone == null ? "" : phone.trim());
    }

    private byte[] ipHash(String sourceIp) {
        return hmac(IP_HMAC_DOMAIN, sourceIp == null || sourceIp.isBlank() ? "unknown" : sourceIp.trim());
    }

    private byte[] hmac(byte[] domain, String value) {
        if (hmacKey.length < 32) {
            throw new BusinessException(
                    ErrorCode.AUTH_CONFIGURATION_UNAVAILABLE,
                    ErrorCode.AUTH_CONFIGURATION_UNAVAILABLE.getDefaultMessage()
            );
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            mac.update(domain);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private BusinessException cooldown() {
        return new BusinessException(ErrorCode.AUTH_COOLDOWN, ErrorCode.AUTH_COOLDOWN.getDefaultMessage());
    }
}
