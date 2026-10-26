package cn.communityelderly.modules.auth;

import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.modules.auth.dto.AuthTokenResponse;
import cn.communityelderly.modules.auth.dto.LoginRequest;
import cn.communityelderly.modules.auth.dto.RegisterChildRequest;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import cn.communityelderly.modules.auth.service.impl.AuthServiceImpl;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.flyway.enabled=false",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
})
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_DB_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/elderly_care_demo\\?.*")
class AuthDatabaseIntegrationTest {

    private static final String TEST_PASSWORD = "isolated-integration-password";
    private static final String EXPECTED_MARKER_ID = "task2-auth-it";

    private record DemoCredential(String phone, String password) {}

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthServiceImpl authService;
    @Autowired
    private AuthSessionMapper sessionMapper;
    @Autowired
    private AuthRateLimitService rateLimitService;
    @Autowired
    private AuthSessionAuthenticationService sessionAuthenticationService;
    @Autowired
    private PhoneVerificationChallengeService phoneVerificationChallengeService;

    private String phone;
    private long userId;

    @DynamicPropertySource
    static void isolatedDatabaseProperties(DynamicPropertyRegistry registry) {
        String url = System.getenv("AUTH_TEST_DB_URL");
        String username = System.getenv("AUTH_TEST_DB_USERNAME");
        String password = System.getenv("AUTH_TEST_DB_PASSWORD");
        String hmacKey = System.getenv("AUTH_TEST_HMAC_KEY");
        String expectedPort = System.getenv("AUTH_TEST_DB_PORT");
        String expectedMarker = System.getenv("AUTH_TEST_DB_MARKER");
        if (url == null || !url.startsWith("jdbc:mysql://127.0.0.1:")
                || username == null || password == null
                || expectedPort == null || expectedMarker == null
                || hmacKey == null || hmacKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("AuthDatabaseIntegrationTest requires its isolated temporary MySQL script.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("AUTH_RATE_LIMIT_HMAC_KEY", () -> hmacKey);
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("PHONE_VERIFICATION_ENABLED", () -> "false");
    }

    @BeforeEach
    void createSyntheticAccount() {
        assertIsolatedDatabaseTarget();
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        phone = "130" + suffix;
        jdbc.update("INSERT INTO sys_community (community_code, community_name, status) VALUES (?, ?, 'ACTIVE')",
                "AUTH-IT-" + suffix, "认证隔离测试社区");
        Long communityId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String passwordHash = new BCryptPasswordEncoder().encode(TEST_PASSWORD);
        jdbc.update("INSERT INTO sys_user (account_kind, phone_normalized, password_hash, status, password_changed_at) "
                        + "VALUES ('STAFF', ?, ?, 'ACTIVE', DATE_SUB(NOW(3), INTERVAL 1 DAY))",
                phone, passwordHash);
        userId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("INSERT INTO sys_user_role (user_id, role_code, community_id, status, granted_by) "
                        + "VALUES (?, 'COMMUNITY_ADMIN', ?, 'ACTIVE', ?)",
                userId, communityId, userId);
    }

    private void assertIsolatedDatabaseTarget() {
        String expectedPort = System.getenv("AUTH_TEST_DB_PORT");
        String expectedMarker = System.getenv("AUTH_TEST_DB_MARKER");
        if (expectedPort == null || expectedMarker == null || expectedMarker.isBlank()) {
            throw new IllegalStateException("Missing isolated MySQL identity marker.");
        }
        var identity = jdbc.queryForMap("SELECT DATABASE() AS database_name, @@port AS database_port, marker_value "
                + "FROM auth_test_instance_marker WHERE marker_id=?", EXPECTED_MARKER_ID);
        if (!"elderly_care_demo".equals(identity.get("database_name"))
                || !expectedPort.equals(String.valueOf(identity.get("database_port")))
                || !expectedMarker.equals(identity.get("marker_value"))) {
            throw new IllegalStateException("Auth integration refused a database that does not match its temporary-instance marker.");
        }
    }

    @Test
    @DisplayName("MySQL 登录验证 BCrypt 并建立可撤销的 Web DB 会话与 CSRF 摘要")
    void bcryptLoginCreatesPersistedWebSession() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setPhone(phone);
        request.setPassword(TEST_PASSWORD);
        request.setClientType("WEB");

        AuthTokenResponse response = authService.login(request, "192.0.2.110");
        assertNotNull(response.getToken());
        assertNotNull(response.getCsrfToken());
        assertEquals("WEB", response.getClientType());

        AuthSession persisted = sessionMapper.selectOne(new LambdaQueryWrapper<AuthSession>()
                .eq(AuthSession::getTokenHash, MessageDigest.getInstance("SHA-256")
                        .digest(response.getToken().getBytes(StandardCharsets.UTF_8))));
        assertNotNull(persisted);
        assertNotNull(persisted.getCsrfTokenHash());
        assertEquals(32, persisted.getTokenHash().length);
        assertEquals(32, persisted.getCsrfTokenHash().length);

        CurrentUser authenticated = sessionAuthenticationService.authenticate(response.getToken(), "/api/v1/auth/current-user");
        assertNotNull(authenticated);
        assertEquals(String.valueOf(userId), authenticated.getUserId());
        assertEquals("COMMUNITY_ADMIN", authenticated.getCurrentRole());
        assertEquals("WEB", authenticated.getClientType());
        assertTrue(sessionAuthenticationService.isCsrfTokenValid(persisted.getSessionId(), response.getCsrfToken()));

        jdbc.update("UPDATE sys_user_role SET status='REVOKED', revoked_at=NOW(3) WHERE assignment_id=?",
                persisted.getActiveAssignmentId());
        assertEquals(null, sessionAuthenticationService.authenticate(response.getToken(), "/api/v1/auth/current-user"));
    }

    @Test
    @DisplayName("首次加载的六个合成演示账号均可按现行契约 BCrypt 登录")
    void documentedDemoSeedAccountsLoginWithBcrypt() throws Exception {
        List<DemoCredential> credentials = readDocumentedDemoCredentials();
        assertEquals(6, credentials.size());
        int index = 0;
        for (DemoCredential credential : credentials) {
            LoginRequest request = new LoginRequest();
            request.setPhone(credential.phone());
            request.setPassword(credential.password());
            request.setClientType("MINIAPP");

            AuthTokenResponse response = authService.login(request, "192.0.2." + (120 + index++));
            assertNotNull(response.getToken(), "documented synthetic account should receive a session");
            assertNotNull(sessionAuthenticationService.authenticate(response.getToken(), "/api/v1/auth/current-user"),
                    "seeded demo session should authenticate from MySQL");
        }
    }

    @Test
    @DisplayName("V3_1 限流记录在真实 MySQL 中持久化并可跨服务实例检查")
    void loginFailureCooldownPersistsInDatabase() {
        String sourceIp = "192.0.2." + Math.floorMod(System.nanoTime(), 200) + 1;
        boolean blocked = false;
        for (int i = 0; i < 5; i++) {
            blocked = rateLimitService.recordFailure(phone, sourceIp);
        }
        assertTrue(blocked);
        assertThrows(BusinessException.class, () -> rateLimitService.assertLoginAllowed(phone, sourceIp));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM auth_rate_limit", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auth_rate_limit WHERE key_type='PHONE' AND failure_count=5 AND cooldown_until > NOW(3)", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM auth_rate_limit WHERE key_type='IP' AND failure_count=5 AND cooldown_until IS NULL", Integer.class));
    }

    @Test
    @DisplayName("无短信渠道时公开注册和挑战签发失败且不写用户、角色、会话或挑战")
    void childRegistrationAndChallengeIssuanceFailClosedWithoutDatabaseWrites() {
        String unclaimedPhone = "139" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Integer usersBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE phone_normalized=?", Integer.class, unclaimedPhone);
        Integer rolesBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role r JOIN sys_user u ON u.user_id=r.user_id WHERE u.phone_normalized=?",
                Integer.class, unclaimedPhone);
        Integer sessionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_session s JOIN sys_user u ON u.user_id=s.user_id WHERE u.phone_normalized=?",
                Integer.class, unclaimedPhone);
        Integer challengesBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM phone_verification_challenge WHERE phone_normalized=?", Integer.class, unclaimedPhone);

        BusinessException challengeError = assertThrows(BusinessException.class,
                () -> phoneVerificationChallengeService.issue(
                        unclaimedPhone, "CHILD_REGISTRATION", unclaimedPhone, null));
        assertEquals(cn.communityelderly.common.api.ErrorCode.PHONE_VERIFICATION_UNAVAILABLE,
                challengeError.getErrorCode());

        RegisterChildRequest request = new RegisterChildRequest();
        request.setPhone(unclaimedPhone);
        request.setPhoneChallengeId("synthetic-fake-challenge");
        request.setVerificationCode("123456");
        request.setPassword("isolated-registration-test-password");
        request.setName("隔离注册测试");
        BusinessException registrationError = assertThrows(BusinessException.class,
                () -> authService.registerChild(request, "192.0.2.199"));
        assertEquals(cn.communityelderly.common.api.ErrorCode.PHONE_VERIFICATION_UNAVAILABLE,
                registrationError.getErrorCode());

        assertEquals(usersBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE phone_normalized=?", Integer.class, unclaimedPhone));
        assertEquals(rolesBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role r JOIN sys_user u ON u.user_id=r.user_id WHERE u.phone_normalized=?",
                Integer.class, unclaimedPhone));
        assertEquals(sessionsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_session s JOIN sys_user u ON u.user_id=s.user_id WHERE u.phone_normalized=?",
                Integer.class, unclaimedPhone));
        assertEquals(challengesBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM phone_verification_challenge WHERE phone_normalized=?", Integer.class, unclaimedPhone));
    }

    private List<DemoCredential> readDocumentedDemoCredentials() throws Exception {
        String path = System.getenv("AUTH_TEST_SEED_DOC_PATH");
        if (path == null || path.isBlank()) throw new IllegalStateException("Missing demo credential contract path.");
        List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
        int begin = -1;
        int end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("### 5.1 ")) begin = i + 1;
            else if (begin >= 0 && lines.get(i).startsWith("### 5.2 ")) { end = i; break; }
        }
        if (begin < 0) throw new IllegalStateException("Demo credential section was not found.");
        List<String> header = null;
        List<DemoCredential> credentials = new java.util.ArrayList<>();
        int phoneColumn = -1;
        int passwordColumn = -1;
        for (int i = begin; i < end; i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("|")) continue;
            String content = line.substring(1, line.endsWith("|") ? line.length() - 1 : line.length());
            List<String> cells = java.util.Arrays.stream(content.split("\\|", -1)).map(String::trim).toList();
            if (header == null) {
                header = cells;
                for (int c = 0; c < header.size(); c++) {
                    if (header.get(c).contains("手机号")) phoneColumn = c;
                    if (header.get(c).contains("登录密码")) passwordColumn = c;
                }
                continue;
            }
            if (cells.stream().allMatch(cell -> cell.matches(":?-{3,}:?"))) continue;
            if (cells.size() != header.size() || phoneColumn < 0 || passwordColumn < 0) {
                throw new IllegalStateException("Unexpected demo credential table layout.");
            }
            String phone = cells.get(phoneColumn).replace("`", "").trim();
            String password = cells.get(passwordColumn).replace("`", "").trim();
            if (!phone.matches("\\d{11}") || password.isBlank()) {
                throw new IllegalStateException("A documented synthetic credential row is incomplete.");
            }
            credentials.add(new DemoCredential(phone, password));
        }
        return credentials;
    }
}
