package cn.communityelderly.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrentUserAuthorizationTest {

    @Test
    @DisplayName("角色授权只承认当前活动角色，不承认会话历史角色列表")
    void hasRoleUsesOnlyCurrentRole() {
        CurrentUser user = new CurrentUser(
                "42", "13800000000", "STAFF",
                List.of("COMMUNITY_ADMIN", "GRID_WORKER"),
                "COMMUNITY_ADMIN", "17");

        assertTrue(user.hasRole("COMMUNITY_ADMIN"));
        assertFalse(user.hasRole("GRID_WORKER"));
    }
}
