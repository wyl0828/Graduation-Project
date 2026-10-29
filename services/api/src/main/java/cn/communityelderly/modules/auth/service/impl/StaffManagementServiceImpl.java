package cn.communityelderly.modules.auth.service.impl;

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
import cn.communityelderly.modules.order.service.WorkOrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Service
public class StaffManagementServiceImpl implements StaffManagementService {

    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern NORMALIZED_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Set<String> STAFF_ROLE_CODES = Set.of("COMMUNITY_ADMIN", "GRID_WORKER", "CARE_WORKER");

    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthSessionMapper authSessionMapper;
    private final AuditLogService auditLogService;
    private final WorkOrderService workOrderService;
    private final ObjectMapper objectMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffManagementServiceImpl(UserAccountMapper userAccountMapper,
                                      UserRoleMapper userRoleMapper,
                                      AuthSessionMapper authSessionMapper,
                                      AuditLogService auditLogService,
                                      WorkOrderService workOrderService,
                                      ObjectMapper objectMapper) {
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
        this.authSessionMapper = authSessionMapper;
        this.auditLogService = auditLogService;
        this.workOrderService = workOrderService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffSummaryVO> listStaff() {
        AdminScope scope = requireAdminScope();
        List<UserRole> assignments = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getCommunityId, scope.communityId())
                        .in(UserRole::getRoleCode, STAFF_ROLE_CODES)
                        .orderByAsc(UserRole::getUserId)
                        .orderByAsc(UserRole::getAssignmentId));
        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }

        Map<Long, List<UserRole>> assignmentsByUser = new LinkedHashMap<>();
        for (UserRole assignment : assignments) {
            if (assignment.getUserId() != null) {
                assignmentsByUser.computeIfAbsent(assignment.getUserId(), ignored -> new ArrayList<>())
                        .add(assignment);
            }
        }
        if (assignmentsByUser.isEmpty()) {
            return List.of();
        }

        List<UserAccount> accounts = userAccountMapper.selectBatchIds(assignmentsByUser.keySet());
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAccount> accountsById = new LinkedHashMap<>();
        for (UserAccount account : accounts) {
            if (account != null && "STAFF".equalsIgnoreCase(account.getAccountKind())) {
                accountsById.put(account.getUserId(), account);
            }
        }

        List<StaffSummaryVO> summaries = new ArrayList<>();
        for (Map.Entry<Long, List<UserRole>> entry : assignmentsByUser.entrySet()) {
            UserAccount account = accountsById.get(entry.getKey());
            if (account == null) {
                continue;
            }
            List<String> activeRoles = entry.getValue().stream()
                    .filter(role -> "ACTIVE".equalsIgnoreCase(role.getStatus()))
                    .map(UserRole::getRoleCode)
                    .filter(STAFF_ROLE_CODES::contains)
                    .distinct()
                    .sorted()
                    .toList();
            summaries.add(toSummary(account, activeRoles));
        }
        return summaries;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffSummaryVO createStaff(StaffCreateRequest request) {
        AdminScope scope = requireAdminScope();
        if (request == null) {
            throw badRequest("请求内容不能为空");
        }

        String phone = normalizePhone(request.getPhone());
        String initialPassword = request.getInitialPassword();
        validateInitialPassword(initialPassword);
        List<String> roleCodes = validateRoleSet(request.getRoles(), false);

        Long existingCount = userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getPhoneNormalized, phone));
        if (existingCount != null && existingCount > 0) {
            throw new BusinessException(ErrorCode.PHONE_NUMBER_EXISTS, "该手机号已注册，不能重复创建工作人员账号");
        }

        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        UserAccount account = new UserAccount();
        account.setAccountKind("STAFF");
        account.setPhoneNormalized(phone);
        account.setPasswordHash(passwordEncoder.encode(initialPassword));
        account.setStatus("ACTIVE");
        account.setPasswordChangedAt(now);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        try {
            if (userAccountMapper.insert(account) < 1 || account.getUserId() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "工作人员账号创建失败");
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(ErrorCode.PHONE_NUMBER_EXISTS, "该手机号已注册，不能重复创建工作人员账号");
        }

        for (String roleCode : roleCodes) {
            UserRole role = new UserRole();
            role.setUserId(account.getUserId());
            role.setRoleCode(roleCode);
            role.setCommunityId(scope.communityId());
            role.setStatus("ACTIVE");
            role.setGrantedBy(scope.actorUserId());
            role.setGrantedAt(now);
            if (userRoleMapper.insert(role) < 1) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "工作人员角色分配失败");
            }
        }

        recordAudit(scope, account.getUserId(), "STAFF_CREATED", List.of(), roleCodes, null);
        return toSummary(account, roleCodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StaffSummaryVO replaceRoles(Long userId, StaffRolesUpdateRequest request) {
        AdminScope scope = requireAdminScope();
        if (userId == null || userId < 1 || request == null) {
            throw badRequest("目标用户和请求内容不能为空");
        }

        List<String> requestedRoles = validateRoleSet(request.getRoles(), true);
        String reason = StringUtils.hasText(request.getReason()) ? request.getReason().trim() : null;
        if (reason != null && reason.length() > 255) {
            throw badRequest("调整原因不能超过255个字符");
        }

        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null || !"STAFF".equalsIgnoreCase(account.getAccountKind())) {
            throw notFound();
        }

        List<UserRole> communityAssignments = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getCommunityId, scope.communityId())
                        .in(UserRole::getRoleCode, STAFF_ROLE_CODES)
                        .orderByAsc(UserRole::getAssignmentId)
                        .last("FOR UPDATE"));
        if (communityAssignments == null || communityAssignments.isEmpty()) {
            throw notFound();
        }

        List<UserRole> activeAssignments = communityAssignments.stream()
                .filter(role -> "ACTIVE".equalsIgnoreCase(role.getStatus()))
                .toList();
        List<String> currentRoles = activeAssignments.stream()
                .map(UserRole::getRoleCode)
                .distinct()
                .sorted()
                .toList();
        if (currentRoles.equals(requestedRoles)) {
            return toSummary(account, requestedRoles);
        }

        boolean removingRoles = currentRoles.stream().anyMatch(role -> !requestedRoles.contains(role));
        if (removingRoles && !StringUtils.hasText(reason)) {
            throw badRequest("撤销工作人员角色时必须填写调整原因");
        }
        if ("ACTIVE".equalsIgnoreCase(account.getStatus())
                && currentRoles.contains("COMMUNITY_ADMIN")
                && !requestedRoles.contains("COMMUNITY_ADMIN")) {
            ensureAnotherCommunityAdmin(userId, scope.communityId());
        }

        boolean losesAllWorkOrderRoles = currentRoles.stream()
                .anyMatch(role -> "GRID_WORKER".equals(role) || "CARE_WORKER".equals(role))
                && requestedRoles.stream().noneMatch(role -> "GRID_WORKER".equals(role) || "CARE_WORKER".equals(role));
        if (losesAllWorkOrderRoles) {
            workOrderService.markOpenOrdersForWorkerHandoff(userId, scope.communityId(),
                    scope.actorUserId(), reason);
        }

        LocalDateTime now = LocalDateTime.now(SERVER_ZONE);
        for (UserRole activeRole : activeAssignments) {
            if (requestedRoles.contains(activeRole.getRoleCode())) {
                continue;
            }
            if (activeRole.getAssignmentId() == null) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "角色分配记录无效，请刷新后重试");
            }
            int updated = userRoleMapper.update(null,
                    new UpdateWrapper<UserRole>()
                            .eq("assignment_id", activeRole.getAssignmentId())
                            .eq("status", "ACTIVE")
                            .set("status", "REVOKED")
                            .set("revoked_at", now)
                            .set("revoked_by", scope.actorUserId())
                            .set("revoke_reason", reason));
            if (updated < 1) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "角色分配已被更新，请刷新后重试");
            }
        }

        Set<String> currentRoleSet = new LinkedHashSet<>(currentRoles);
        for (String roleCode : requestedRoles) {
            if (currentRoleSet.contains(roleCode)) {
                continue;
            }
            UserRole role = new UserRole();
            role.setUserId(userId);
            role.setRoleCode(roleCode);
            role.setCommunityId(scope.communityId());
            role.setStatus("ACTIVE");
            role.setGrantedBy(scope.actorUserId());
            role.setGrantedAt(now);
            if (userRoleMapper.insert(role) < 1) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "工作人员角色分配失败");
            }
        }

        authSessionMapper.update(null,
                new UpdateWrapper<AuthSession>()
                        .eq("user_id", userId)
                        .isNull("revoked_at")
                        .set("revoked_at", now)
                        .set("revoke_reason", "ROLE_CHANGED"));
        recordAudit(scope, userId, "STAFF_ROLES_REPLACED", currentRoles, requestedRoles, reason);
        return toSummary(account, requestedRoles);
    }

    private void ensureAnotherCommunityAdmin(Long targetUserId, Long communityId) {
        List<UserRole> admins = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getCommunityId, communityId)
                        .eq(UserRole::getRoleCode, "COMMUNITY_ADMIN")
                        .eq(UserRole::getStatus, "ACTIVE")
                        .orderByAsc(UserRole::getUserId)
                        .orderByAsc(UserRole::getAssignmentId)
                        .last("FOR UPDATE"));
        List<Long> otherAdminIds = admins == null ? List.of() : admins.stream()
                .map(UserRole::getUserId)
                .filter(userId -> userId != null && !userId.equals(targetUserId))
                .distinct()
                .toList();
        List<UserAccount> otherAdminAccounts = otherAdminIds.isEmpty()
                ? List.of()
                : userAccountMapper.selectBatchIds(otherAdminIds);
        boolean anotherAdminRemains = otherAdminAccounts != null && otherAdminAccounts.stream()
                .anyMatch(account -> account != null
                        && "STAFF".equalsIgnoreCase(account.getAccountKind())
                        && "ACTIVE".equalsIgnoreCase(account.getStatus()));
        if (!anotherAdminRemains) {
            throw badRequest("不能撤销本社区最后一个有效管理员角色");
        }
    }

    private List<String> validateRoleSet(List<String> requestedRoles, boolean allowEmpty) {
        if (requestedRoles == null || (!allowEmpty && requestedRoles.isEmpty())) {
            throw badRequest(allowEmpty ? "角色集合不能为空；空集合表示撤销本社区角色" : "至少选择一个工作人员角色");
        }

        TreeSet<String> normalized = new TreeSet<>();
        for (String requestedRole : requestedRoles) {
            if (!StringUtils.hasText(requestedRole)) {
                throw badRequest("角色代码不能为空");
            }
            String roleCode = requestedRole.trim().toUpperCase(java.util.Locale.ROOT);
            if (!STAFF_ROLE_CODES.contains(roleCode)) {
                throw badRequest("不允许分配的工作人员角色: " + roleCode);
            }
            if (!normalized.add(roleCode)) {
                throw badRequest("请求中包含重复角色: " + roleCode);
            }
        }

        if (normalized.isEmpty() && !allowEmpty) {
            throw badRequest("至少选择一个工作人员角色");
        }
        if (normalized.size() > 2
                || (normalized.contains("GRID_WORKER") && normalized.contains("CARE_WORKER"))) {
            throw badRequest("社区管理员只能与一种工作人员角色兼任，网格员和护工不能同时分配");
        }
        return List.copyOf(normalized);
    }

    private String normalizePhone(String requestedPhone) {
        if (!StringUtils.hasText(requestedPhone)) {
            throw badRequest("手机号不能为空");
        }
        String phone = requestedPhone.trim();
        if (!NORMALIZED_MOBILE.matcher(phone).matches()) {
            throw badRequest("手机号须为规范化的11位手机号");
        }
        return phone;
    }

    private void validateInitialPassword(String password) {
        if (!StringUtils.hasText(password) || password.codePointCount(0, password.length()) < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest("初始密码须至少8个字符且不超过 BCrypt 支持的长度");
        }
    }

    private AdminScope requireAdminScope() {
        CurrentUser current = UserContext.requireCurrentUser();
        if (!"STAFF".equalsIgnoreCase(current.getAccountKind())
                || !"COMMUNITY_ADMIN".equalsIgnoreCase(current.getCurrentRole())
                || !current.getRoles().contains("COMMUNITY_ADMIN")) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "仅本社区管理员可以管理员工账号");
        }
        try {
            Long actorUserId = Long.parseLong(current.getUserId());
            Long communityId = Long.parseLong(current.getCommunityId());
            if (actorUserId < 1 || communityId < 1) {
                throw new NumberFormatException("IDs must be positive");
            }
            return new AdminScope(actorUserId, communityId);
        } catch (NumberFormatException | NullPointerException invalidContext) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前管理员会话缺少有效用户或社区范围");
        }
    }

    private void recordAudit(AdminScope scope, Long targetUserId, String action,
                             List<String> beforeRoles, List<String> afterRoles, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("beforeRoles", beforeRoles);
        details.put("afterRoles", afterRoles);
        if (StringUtils.hasText(reason)) {
            details.put("reason", reason);
        }
        try {
            auditLogService.recordAudit(scope.actorUserId(), "COMMUNITY_ADMIN", scope.communityId(), "STAFF",
                    String.valueOf(targetUserId), action, "SUCCESS", null,
                    objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("工作人员变更审计明细序列化失败", serializationFailure);
        }
    }

    private StaffSummaryVO toSummary(UserAccount account, List<String> roleCodes) {
        StaffSummaryVO summary = new StaffSummaryVO();
        summary.setUserId(account.getUserId());
        summary.setMaskedPhone(maskPhone(account.getPhoneNormalized()));
        summary.setAccountStatus(account.getStatus());
        summary.setRoleCodes(roleCodes.stream().distinct().sorted().toList());
        return summary;
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "***********";
        }
        if (phone.length() <= 7) {
            return "*".repeat(phone.length());
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE,
                ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE.getDefaultMessage());
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorCode.BAD_REQUEST, message);
    }

    private record AdminScope(Long actorUserId, Long communityId) {
    }
}
