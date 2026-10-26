package cn.communityelderly.modules.auth;

import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.dto.SwitchRoleRequest;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.impl.AuthRateLimitService;
import cn.communityelderly.modules.auth.service.impl.AuthServiceImpl;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRoleSwitchPersistenceTest {

    private UserRoleMapper roleMapper;
    private AuthSessionMapper sessionMapper;
    private AuthServiceImpl authService;
    private AuthSession session;

    @BeforeEach
    void setUp() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        roleMapper = mock(UserRoleMapper.class);
        sessionMapper = mock(AuthSessionMapper.class);
        authService = new AuthServiceImpl(accountMapper, roleMapper, sessionMapper, mock(AuthRateLimitService.class),
                mock(PhoneVerificationChallengeService.class));

        session = new AuthSession();
        session.setSessionId("session-1");
        session.setUserId(42L);
        session.setActiveAssignmentId(700L);
        session.setAbsoluteExpiresAt(java.time.LocalDateTime.now().plusDays(2));
        when(sessionMapper.selectById("session-1")).thenReturn(session);
        when(sessionMapper.updateById(any(AuthSession.class))).thenReturn(1);

        UserContext.set(new CurrentUser(
                "42", "13800000042", "STAFF",
                List.of("COMMUNITY_ADMIN", "GRID_WORKER"),
                "COMMUNITY_ADMIN", "77", 700L, "session-1"));
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("角色上下文切换持久化 active_assignment_id 和社区")
    void persistsSelectedRoleAssignment() {
        UserRole target = new UserRole();
        target.setAssignmentId(701L);
        target.setUserId(42L);
        target.setRoleCode("GRID_WORKER");
        target.setCommunityId(88L);
        target.setStatus("ACTIVE");
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(target));

        SwitchRoleRequest request = new SwitchRoleRequest();
        request.setTargetRoleCode("GRID_WORKER");
        var response = authService.switchRole(request);

        assertEquals(701L, session.getActiveAssignmentId());
        assertEquals("GRID_WORKER", response.getActiveRole());
        assertEquals("88", response.getCommunityId());
        assertEquals(701L, UserContext.requireCurrentUser().getActiveAssignmentId());
        assertEquals("GRID_WORKER", UserContext.requireCurrentUser().getCurrentRole());
        verify(sessionMapper).updateById(session);
    }

    @Test
    @DisplayName("角色分配已失效時拒絕切換")
    void refusesRoleWithoutCurrentActiveAssignment() {
        when(roleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        SwitchRoleRequest request = new SwitchRoleRequest();
        request.setTargetRoleCode("GRID_WORKER");

        assertThrows(BusinessException.class, () -> authService.switchRole(request));
    }
}
