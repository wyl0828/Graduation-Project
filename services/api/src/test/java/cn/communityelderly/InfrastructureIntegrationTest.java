package cn.communityelderly;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class InfrastructureIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("验证数据库连接与Flyway版本已升级至V3.1及以上")
    void testFlywaySchemaVersionIsV3() {
        assertNotNull(jdbcTemplate, "JdbcTemplate 应已正确注入");

        // 查询当前最新 Flyway 迁移版本
        String sql = "SELECT version FROM flyway_schema_history WHERE installed_rank = (SELECT MAX(installed_rank) FROM flyway_schema_history)";
        String latestVersion = jdbcTemplate.queryForObject(sql, String.class);

        assertNotNull(latestVersion, "Flyway 迁移版本不能为空");
        assertTrue(Double.parseDouble(latestVersion) >= 3.1, "Flyway 迁移版本必须不低于 3.1，当前为: " + latestVersion);
    }

    @Test
    @DisplayName("验证全量业务核心表数量与演示种子数据完整性")
    void testCoreTablesAndSeedDataIntegrity() {
        // 校验核心数据表记录数不为空
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
        Integer elderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM elder_profile", Integer.class);
        Integer vitalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vital_record", Integer.class);
        Integer catalogCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM service_catalog", Integer.class);
        Integer orderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order", Integer.class);

        assertNotNull(userCount);
        assertTrue(userCount >= 6, "sys_user 至少应有 6 条种子账号");

        assertNotNull(elderCount);
        assertTrue(elderCount >= 3, "elder_profile 至少应有 3 条老人档案");

        assertNotNull(vitalCount);
        assertTrue(vitalCount >= 5, "vital_record 至少应有 5 条体征记录");

        assertNotNull(catalogCount);
        assertTrue(catalogCount >= 4, "service_catalog 至少应有 4 条养老服务项");

        assertNotNull(orderCount);
        assertTrue(orderCount >= 2, "work_order 至少应有 2 条业务工单");
    }

    @Test
    @DisplayName("验证统一API响应体与用户上下文封装")
    void testResultAndUserContext() {
        // 验证统一成功响应结构
        Result<String> successResult = Result.success("test-payload");
        assertNotNull(successResult);
        assertEquals(ErrorCode.OK.name(), successResult.getCode());
        assertEquals("test-payload", successResult.getData());

        // 验证用户上下文绑定与清除
        CurrentUser user = new CurrentUser(
                "U-1001",
                "13800000001",
                "STAFF",
                List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN",
                "COMM_001"
        );
        UserContext.set(user);
        assertEquals(user, UserContext.get());
        assertTrue(UserContext.requireCurrentUser().hasRole("COMMUNITY_ADMIN"));

        UserContext.clear();
        assertNull(UserContext.get());
    }
}
