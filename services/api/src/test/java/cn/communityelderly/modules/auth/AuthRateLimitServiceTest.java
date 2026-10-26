package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.mapper.AuthRateLimitMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimitServiceTest {

    private static final String PHONE = "13000000077";
    private static final String IP = "192.0.2.77";
    private AuthRateLimitMapper mapper;
    private AuthRateLimitService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthRateLimitMapper.class);
        service = new AuthRateLimitService(mapper, "test-only-hmac-key-with-at-least-32-characters");
    }

    @Test
    @DisplayName("手机号与 IP 失败计数独立持久化且仅保存 HMAC 摘要")
    void recordsSeparatePiiSafePhoneAndIpKeys() {
        ArgumentCaptor<byte[]> phoneHash = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> ipHash = ArgumentCaptor.forClass(byte[].class);

        assertFalse(service.recordFailure(PHONE, IP));

        verify(mapper).recordFailure(eq("PHONE"), phoneHash.capture(), any(LocalDateTime.class),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(5));
        verify(mapper).recordFailure(eq("IP"), ipHash.capture(), any(LocalDateTime.class),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(30));
        assertEquals(32, phoneHash.getValue().length);
        assertEquals(32, ipHash.getValue().length);
        assertFalse(Arrays.equals(PHONE.getBytes(StandardCharsets.UTF_8), phoneHash.getValue()));
        assertFalse(Arrays.equals(IP.getBytes(StandardCharsets.UTF_8), ipHash.getValue()));
    }

    @Test
    @DisplayName("存在未结束冷却时登录被限流")
    void activeCooldownRejectsLogin() {
        when(mapper.hasActiveCooldown(eq("PHONE"), any(byte[].class), any(LocalDateTime.class))).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertLoginAllowed(PHONE, IP));

        assertEquals(ErrorCode.AUTH_COOLDOWN, error.getErrorCode());
    }

    @Test
    @DisplayName("缺少 HMAC 密钥时限流检查 fail-closed")
    void missingHmacKeyRejectsAuthenticationWithoutDatabaseLookup() {
        service = new AuthRateLimitService(mapper, "");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertLoginAllowed(PHONE, IP));

        assertEquals(ErrorCode.AUTH_CONFIGURATION_UNAVAILABLE, error.getErrorCode());
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }
}
