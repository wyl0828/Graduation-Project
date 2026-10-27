package cn.communityelderly.modules.auth;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.dto.StaffCreateRequest;
import cn.communityelderly.modules.auth.dto.StaffRolesUpdateRequest;
import cn.communityelderly.modules.auth.dto.StaffSummaryVO;
import cn.communityelderly.modules.auth.entity.AuthSession;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.AuthSessionMapper;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.auth.service.StaffManagementService;
import cn.communityelderly.modules.auth.service.impl.StaffManagementServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffManagementServiceTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("staff-management-test");
        TableInfoHelper.initTableInfo(assistant, UserRole.class);
        TableInfoHelper.initTableInfo(assistant, UserAccount.class);
    }

    private UserAccountMapper userAccountMapper;
    private UserRoleMapper userRoleMapper;
    private AuthSessionMapper authSessionMapper;
    private AuditLogService auditLogService;
    private StaffManagementService staffManagementService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userAccountMapper = mock(UserAccountMapper.class);
        userRoleMapper = mock(UserRoleMapper.class);
        authSessionMapper = mock(AuthSessionMapper.class);
        auditLogService = mock(AuditLogService.class);
        staffManagementService = new StaffManagementServiceImpl(
                userAccountMapper, userRoleMapper, authSessionMapper, auditLogService, objectMapper);

        UserContext.set(new CurrentUser("10", "13800000010", "STAFF",
                List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "77", 701L, "admin-session-1"));
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("创建工作人员时保存 BCrypt 哈希且响应与审计不含初始密码")
    void createsStaffWithBcryptHashAndNeverExposesInitialPassword() throws Exception {
        when(userAccountMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userAccountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            account.setUserId(900L);
            return 1;
        });
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        String initialPassword = "InitialPass123";
        StaffSummaryVO summary = staffManagementService.createStaff(
                createRequest("13800000900", initialPassword, "COMMUNITY_ADMIN", "CARE_WORKER"));

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(accountCaptor.capture());
        UserAccount created = accountCaptor.getValue();
        assertEquals("STAFF", created.getAccountKind());
        assertEquals("13800000900", created.getPhoneNormalized());
        assertNotEquals(initialPassword, created.getPasswordHash());
        assertTrue(new BCryptPasswordEncoder().matches(initialPassword, created.getPasswordHash()));
        assertEquals(900L, summary.getUserId());
        assertEquals("138****0900", summary.getMaskedPhone());
        assertEquals("ACTIVE", summary.getAccountStatus());
        assertEquals(List.of("CARE_WORKER", "COMMUNITY_ADMIN"), summary.getRoleCodes());
        String responseJson = objectMapper.writeValueAsString(summary);
        assertFalse(responseJson.contains(initialPassword));
        assertFalse(responseJson.contains("initialPassword"));

        ArgumentCaptor<String> auditDetailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordAudit(eq(10L), eq("COMMUNITY_ADMIN"), eq(77L), eq("STAFF"), eq("900"),
                eq("STAFF_CREATED"), eq("SUCCESS"), isNull(), auditDetailsCaptor.capture());
        JsonNode auditDetails = objectMapper.readTree(auditDetailsCaptor.getValue());
        assertEquals(List.of("CARE_WORKER", "COMMUNITY_ADMIN"),
                objectMapper.convertValue(auditDetails.get("afterRoles"), objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, String.class)));
        assertFalse(auditDetailsCaptor.getValue().contains(initialPassword));
        assertFalse(auditDetailsCaptor.getValue().contains("13800000900"));
    }

    @Test
    @DisplayName("拒绝空白初始密码")
    void rejectsBlankInitialPasswordBeforeWriting() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> staffManagementService.createStaff(createRequest("13800000901", "   ", "GRID_WORKER")));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verifyNoInteractions(userAccountMapper, userRoleMapper, authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("拒绝不足八个字符的初始密码")
    void rejectsShortInitialPasswordBeforeWriting() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> staffManagementService.createStaff(createRequest("13800000902", "short7", "GRID_WORKER")));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verifyNoInteractions(userAccountMapper, userRoleMapper, authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("手机号已存在时不创建账号或审计")
    void rejectsDuplicatePhoneWithoutCreatingAccount() {
        when(userAccountMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> staffManagementService.createStaff(createRequest("13800000903", "ValidPass123", "GRID_WORKER")));

        assertEquals(ErrorCode.PHONE_NUMBER_EXISTS, error.getErrorCode());
        verify(userAccountMapper, never()).insert(any(UserAccount.class));
        verify(userRoleMapper, never()).insert(any(UserRole.class));
        verifyNoInteractions(authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("拒绝未知、家庭和运维角色")
    void rejectsUnknownFamilyAndMaintenanceRoles() {
        for (String invalidRole : List.of("UNKNOWN_ROLE", "ELDER", "CHILD", "OPS_ADMIN")) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> staffManagementService.createStaff(
                            createRequest("13800000904", "ValidPass123", invalidRole)));
            assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode(), invalidRole);
        }

        verifyNoInteractions(userAccountMapper, userRoleMapper, authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("允许社区管理员兼任一种工作人员角色")
    void allowsCommunityAdminWithOneWorkerRole() {
        when(userAccountMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(userAccountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            ((UserAccount) invocation.getArgument(0)).setUserId(901L);
            return 1;
        });
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);

        StaffSummaryVO summary = staffManagementService.createStaff(
                createRequest("13800000905", "ValidPass123", "COMMUNITY_ADMIN", "GRID_WORKER"));

        assertEquals(List.of("COMMUNITY_ADMIN", "GRID_WORKER"), summary.getRoleCodes());
        ArgumentCaptor<UserRole> roleCaptor = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleMapper, times(2)).insert(roleCaptor.capture());
        assertEquals(List.of("COMMUNITY_ADMIN", "GRID_WORKER"),
                roleCaptor.getAllValues().stream().map(UserRole::getRoleCode).sorted().toList());
        assertTrue(roleCaptor.getAllValues().stream().allMatch(role -> role.getCommunityId().equals(77L)));
        assertTrue(roleCaptor.getAllValues().stream().allMatch(role -> role.getGrantedBy().equals(10L)));
    }

    @Test
    @DisplayName("拒绝同时分配网格员和护工角色")
    void rejectsGridAndCareWorkerCombination() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> staffManagementService.createStaff(
                        createRequest("13800000906", "ValidPass123", "GRID_WORKER", "CARE_WORKER")));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verifyNoInteractions(userAccountMapper, userRoleMapper, authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("员工列表仅使用当前社区分配并展示已撤销全部角色的账号")
    void listsCurrentCommunityStaffIncludingRoleRevokedAccounts() {
        UserRole revokedGrid = role(920L, 920L, "GRID_WORKER", 77L, "REVOKED");
        UserRole activeCare = role(921L, 921L, "CARE_WORKER", 77L, "ACTIVE");
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(revokedGrid, activeCare));
        when(userAccountMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                account(920L, "13800000920", "ACTIVE"), account(921L, "13800000921", "DISABLED")));

        List<StaffSummaryVO> summaries = staffManagementService.listStaff();

        assertEquals(2, summaries.size());
        assertEquals(920L, summaries.get(0).getUserId());
        assertEquals(List.of(), summaries.get(0).getRoleCodes());
        assertEquals("DISABLED", summaries.get(1).getAccountStatus());
        assertEquals(List.of("CARE_WORKER"), summaries.get(1).getRoleCodes());
        ArgumentCaptor<LambdaQueryWrapper<UserRole>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userRoleMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<UserRole> listQuery = queryCaptor.getValue();
        listQuery.getSqlSegment();
        assertTrue(listQuery.getParamNameValuePairs().containsValue(77L));
        assertTrue(listQuery.getParamNameValuePairs().containsValue("CARE_WORKER"));
        assertTrue(listQuery.getParamNameValuePairs().containsValue("GRID_WORKER"));
    }

    @Test
    @DisplayName("跨社区工作人员目标按不存在处理且不产生副作用")
    void hidesCrossCommunityStaffTarget() {
        when(userAccountMapper.selectById(930L)).thenReturn(account(930L, "13800000930", "ACTIVE"));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        BusinessException error = assertThrows(BusinessException.class,
                () -> staffManagementService.replaceRoles(930L, updateRequest(List.of("GRID_WORKER"), "调整岗位")));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(userRoleMapper, never()).update(any(), any(Wrapper.class));
        verify(userRoleMapper, never()).insert(any(UserRole.class));
        verify(userRoleMapper, never()).delete(any(Wrapper.class));
        verifyNoInteractions(authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("撤销角色保留分配历史并撤销目标账号全部会话")
    void revokesRolesWithoutDeletingHistoryAndRevokesEverySession() throws Exception {
        UserRole activeGrid = role(940L, 940L, "GRID_WORKER", 77L, "ACTIVE");
        UserRole priorCare = role(941L, 940L, "CARE_WORKER", 77L, "REVOKED");
        when(userAccountMapper.selectById(940L)).thenReturn(account(940L, "13800000940", "ACTIVE"));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(activeGrid, priorCare));
        when(userRoleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(userRoleMapper.insert(any(UserRole.class))).thenReturn(1);
        when(authSessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(3);

        StaffSummaryVO summary = staffManagementService.replaceRoles(940L, updateRequest(List.of(), "岗位调整"));

        assertEquals(List.of(), summary.getRoleCodes());
        verify(userRoleMapper, never()).delete(any(Wrapper.class));
        ArgumentCaptor<UpdateWrapper<UserRole>> roleUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(userRoleMapper).update(isNull(), roleUpdateCaptor.capture());
        assertTrue(roleUpdateCaptor.getValue().getSqlSet().contains("status"));
        assertTrue(roleUpdateCaptor.getValue().getSqlSet().contains("revoked_at"));
        assertTrue(roleUpdateCaptor.getValue().getSqlSet().contains("revoked_by"));
        assertTrue(roleUpdateCaptor.getValue().getSqlSet().contains("revoke_reason"));

        ArgumentCaptor<UpdateWrapper<AuthSession>> sessionUpdateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(authSessionMapper).update(isNull(), sessionUpdateCaptor.capture());
        UpdateWrapper<AuthSession> sessionUpdate = sessionUpdateCaptor.getValue();
        String sessionSql = sessionUpdate.getSqlSegment();
        assertTrue(sessionUpdate.getSqlSet().contains("revoked_at"));
        assertTrue(sessionUpdate.getParamNameValuePairs().containsValue("ROLE_CHANGED"));
        assertTrue(sessionUpdate.getParamNameValuePairs().containsValue(940L));
        assertFalse(sessionSql.contains("client_type"));

        ArgumentCaptor<String> auditDetailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).recordAudit(eq(10L), eq("COMMUNITY_ADMIN"), eq(77L), eq("STAFF"), eq("940"),
                eq("STAFF_ROLES_REPLACED"), eq("SUCCESS"), isNull(), auditDetailsCaptor.capture());
        JsonNode auditDetails = objectMapper.readTree(auditDetailsCaptor.getValue());
        assertEquals("岗位调整", auditDetails.get("reason").asText());
        assertEquals(List.of("GRID_WORKER"), objectMapper.convertValue(auditDetails.get("beforeRoles"),
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)));
        assertTrue(auditDetails.get("afterRoles").isEmpty());
    }

    @Test
    @DisplayName("拒绝撤销本社区最后一个有效管理员角色")
    void refusesToRemoveLastActiveCommunityAdmin() {
        UserRole targetAdmin = role(950L, 950L, "COMMUNITY_ADMIN", 77L, "ACTIVE");
        when(userAccountMapper.selectById(950L)).thenReturn(account(950L, "13800000950", "ACTIVE"));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(targetAdmin), List.of(targetAdmin));

        BusinessException error = assertThrows(BusinessException.class,
                () -> staffManagementService.replaceRoles(950L, updateRequest(List.of(), "离职")));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertTrue(error.getMessage().contains("最后一个"));
        verify(userRoleMapper, never()).update(any(), any(Wrapper.class));
        verify(userRoleMapper, never()).insert(any(UserRole.class));
        verifyNoInteractions(authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("禁用账号的有效角色分配不能满足最后管理员保护")
    void doesNotCountDisabledAdminWhenProtectingLastActiveAdmin() {
        UserRole targetAdmin = role(960L, 960L, "COMMUNITY_ADMIN", 77L, "ACTIVE");
        UserRole disabledAdmin = role(961L, 961L, "COMMUNITY_ADMIN", 77L, "ACTIVE");
        when(userAccountMapper.selectById(960L)).thenReturn(account(960L, "13800000960", "ACTIVE"));
        when(userRoleMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(targetAdmin), List.of(targetAdmin, disabledAdmin));
        when(userAccountMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(account(961L, "13800000961", "DISABLED")));
        when(userRoleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(authSessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> staffManagementService.replaceRoles(960L, updateRequest(List.of(), "离职")));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        assertTrue(error.getMessage().contains("最后一个"));
        verify(userRoleMapper, never()).update(any(), any(Wrapper.class));
        verify(userRoleMapper, never()).insert(any(UserRole.class));
        verifyNoInteractions(authSessionMapper, auditLogService);
    }

    @Test
    @DisplayName("禁用的目标账号不视为正在移除的有效管理员")
    void allowsRemovingDisabledAdminAssignmentBecauseItIsNotEffective() {
        UserRole disabledTargetAdmin = role(970L, 970L, "COMMUNITY_ADMIN", 77L, "ACTIVE");
        when(userAccountMapper.selectById(970L)).thenReturn(account(970L, "13800000970", "DISABLED"));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(disabledTargetAdmin));
        when(userRoleMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(authSessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        StaffSummaryVO summary = staffManagementService.replaceRoles(970L, updateRequest(List.of(), "离职"));

        assertEquals(List.of(), summary.getRoleCodes());
        verify(userRoleMapper).update(isNull(), any(Wrapper.class));
        verify(authSessionMapper).update(isNull(), any(Wrapper.class));
        verify(auditLogService).recordAudit(eq(10L), eq("COMMUNITY_ADMIN"), eq(77L), eq("STAFF"), eq("970"),
                eq("STAFF_ROLES_REPLACED"), eq("SUCCESS"), isNull(), anyString());
    }

    private StaffCreateRequest createRequest(String phone, String password, String... roles) {
        StaffCreateRequest request = new StaffCreateRequest();
        request.setPhone(phone);
        request.setInitialPassword(password);
        request.setRoles(List.of(roles));
        return request;
    }

    private StaffRolesUpdateRequest updateRequest(List<String> roles, String reason) {
        StaffRolesUpdateRequest request = new StaffRolesUpdateRequest();
        request.setRoles(roles);
        request.setReason(reason);
        return request;
    }

    private UserRole role(Long assignmentId, Long userId, String roleCode, Long communityId, String status) {
        UserRole role = new UserRole();
        role.setAssignmentId(assignmentId);
        role.setUserId(userId);
        role.setRoleCode(roleCode);
        role.setCommunityId(communityId);
        role.setStatus(status);
        return role;
    }

    private UserAccount account(Long userId, String phone, String status) {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setAccountKind("STAFF");
        account.setPhoneNormalized(phone);
        account.setStatus(status);
        return account;
    }
}
