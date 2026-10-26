package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.dto.AuthTokenResponse;
import cn.communityelderly.modules.auth.dto.LoginRequest;
import cn.communityelderly.modules.auth.dto.RegisterChildRequest;
import cn.communityelderly.modules.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Test
    @DisplayName("测试管理员使用种子凭据登录成功")
    void testAdminLoginSuccess() {
        LoginRequest req = new LoginRequest();
        req.setPhone("13800000001");
        req.setPassword("Admin123456");
        req.setClientType("WEB");

        AuthTokenResponse response = authService.login(req);

        assertNotNull(response);
        assertNotNull(response.getToken());
        assertEquals("13800000001", response.getPhone());
        assertEquals("STAFF", response.getAccountKind());
        assertTrue(response.getRoles().contains("COMMUNITY_ADMIN"));
        assertEquals("COMMUNITY_ADMIN", response.getActiveRole());
    }

    @Test
    @DisplayName("测试密码错误抛出 INVALID_CREDENTIALS 业务异常")
    void testLoginWithWrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setPhone("13800000001");
        req.setPassword("WrongPassword999");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(req));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    @DisplayName("没有可信手机号验证渠道时公开子女注册明确不可用")
    void testRegisterChildUnavailableWithoutVerifiedPhoneChannel() {
        String testPhone = "13900009988";
        RegisterChildRequest req = new RegisterChildRequest();
        req.setPhone(testPhone);
        req.setPassword("ChildPass123");
        req.setName("测试子女小王");
        req.setPhoneChallengeId("synthetic-test-challenge");
        req.setVerificationCode("123456");

        BusinessException error = assertThrows(BusinessException.class,
                () -> authService.registerChild(req));
        assertEquals(ErrorCode.PHONE_VERIFICATION_UNAVAILABLE, error.getErrorCode());
    }
}
