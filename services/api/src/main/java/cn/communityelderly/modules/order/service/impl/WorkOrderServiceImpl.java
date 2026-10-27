package cn.communityelderly.modules.order.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.entity.WorkOrderAssignmentHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderReviewDTO;
import cn.communityelderly.modules.order.dto.WorkOrderVO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final List<String> CARE_WORKER_TERMINAL_STATUSES = List.of(
            "CLOSED", "VOIDED", "TERMINATED_PRE_ACCEPTANCE", "SERVICE_STOPPED");

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderAssignmentHistoryMapper assignmentHistoryMapper;
    private final WorkOrderActionHistoryMapper actionHistoryMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final ReservationExecutionLinkMapper executionLinkMapper;
    private final ServiceReservationMapper reservationMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final AuditLogService auditLogService;

    public WorkOrderServiceImpl(WorkOrderMapper workOrderMapper,
                                WorkOrderAssignmentHistoryMapper assignmentHistoryMapper,
                                WorkOrderActionHistoryMapper actionHistoryMapper,
                                ElderProfileMapper elderProfileMapper,
                                UserAccountMapper userAccountMapper,
                                UserRoleMapper userRoleMapper,
                                ReservationExecutionLinkMapper executionLinkMapper,
                                ServiceReservationMapper reservationMapper,
                                ObjectAuthorizationPolicy authorizationPolicy,
                                AuditLogService auditLogService) {
        this.workOrderMapper = workOrderMapper;
        this.assignmentHistoryMapper = assignmentHistoryMapper;
        this.actionHistoryMapper = actionHistoryMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
        this.executionLinkMapper = executionLinkMapper;
        this.reservationMapper = reservationMapper;
        this.authorizationPolicy = authorizationPolicy;
        this.auditLogService = auditLogService;
    }

    @Override
    public List<WorkOrderVO> getDispatchPool(Long communityId, String status) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        // The community is always taken from the authenticated session, never from the request.
        Long scopedCommunityId = resolveSessionCommunityId();
        if (communityId != null && !communityId.equals(scopedCommunityId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只能查询当前社区的待派工单");
        }
        List<Long> scopedElderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (scopedElderIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<WorkOrder> query = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getCommunityId, scopedCommunityId)
                .in(WorkOrder::getElderId, scopedElderIds);
        if (StringUtils.hasText(status)) {
            query.eq(WorkOrder::getStatus, status);
        } else {
            query.notIn(WorkOrder::getStatus, List.of("CLOSED", "VOIDED"));
        }
        query.orderByDesc(WorkOrder::getCreatedAt);

        List<WorkOrder> list = workOrderMapper.selectList(query);
        list.forEach(order -> requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN"));
        return convertToVOList(list);
    }

    @Override
    public List<WorkOrderVO> getMyTasks(Long workerUserId, String status) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        if (isCurrentRole(current, "CARE_WORKER")) {
            CareWorkerScope scope = requireCareWorkerScope(workerUserId);
            LambdaQueryWrapper<WorkOrder> query = new LambdaQueryWrapper<WorkOrder>()
                    .eq(WorkOrder::getCurrentAssigneeId, scope.userId)
                    .eq(WorkOrder::getCommunityId, scope.communityId)
                    .notIn(WorkOrder::getStatus, CARE_WORKER_TERMINAL_STATUSES);
            if (StringUtils.hasText(status)) {
                query.eq(WorkOrder::getStatus, status);
            }
            query.orderByDesc(WorkOrder::getCreatedAt);

            List<WorkOrder> list = workOrderMapper.selectList(query);
            List<WorkOrderVO> result = new ArrayList<>();
            for (WorkOrder order : list) {
                if (isCareWorkerTerminalOrder(order)) {
                    continue;
                }
                ElderProfile elder = requireCareWorkerOrder(order, scope);
                result.add(convertToCareWorkerVO(order, elder));
            }
            return result;
        }

        authorizationPolicy.requireRole("GRID_WORKER");
        Long selfId = currentUserId(current);
        if (workerUserId != null && !workerUserId.equals(selfId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只能查看本人的待办工单");
        }
        Long scopedCommunityId = resolveSessionCommunityId();
        List<Long> scopedElderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (scopedElderIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<WorkOrder> query = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getCurrentAssigneeId, selfId)
                .eq(WorkOrder::getCommunityId, scopedCommunityId)
                .in(WorkOrder::getElderId, scopedElderIds);
        if (StringUtils.hasText(status)) {
            query.eq(WorkOrder::getStatus, status);
        } else {
            query.notIn(WorkOrder::getStatus, List.of("CLOSED", "VOIDED"));
        }
        query.orderByDesc(WorkOrder::getCreatedAt);

        List<WorkOrder> list = workOrderMapper.selectList(query);
        list.forEach(order -> requireOrderInCurrentCommunity(order, "GRID_WORKER"));
        return convertToVOList(list);
    }

    @Override
    public WorkOrderVO getOrderDetail(Long workOrderId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        if (isCurrentRole(current, "CARE_WORKER")) {
            CareWorkerScope scope = requireCareWorkerScope(null);
            WorkOrder careOrder = workOrderMapper.selectById(workOrderId);
            if (careOrder == null) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "指定的工单不存在");
            }
            ElderProfile elder = requireCareWorkerOrder(careOrder, scope);
            return convertToCareWorkerVO(careOrder, elder);
        }

        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "指定的工单不存在");
        }
        ElderProfile elder = authorizationPolicy.requireElderAccess(order.getElderId());
        requireOrderCommunityMatchesElder(order, elder);
        return convertToVO(order);
    }

    @Override
    @Transactional
    public void dispatchOrder(Long workOrderId, WorkOrderDispatchDTO dto, Long operatorId) {
        requireCurrentActor(operatorId, "COMMUNITY_ADMIN");
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在");
        }
        ElderProfile elder = requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        assertStatus(order, "PENDING_DISPATCH", "DISPATCHED");

        UserAccount assignee = userAccountMapper.selectById(dto.getAssigneeUserId());
        UserRole assignment = findActiveDispatchAssignment(dto.getAssigneeUserId(), elder.getCommunityId(), "GRID_WORKER");
        if (assignment == null) {
            assignment = findActiveDispatchAssignment(dto.getAssigneeUserId(), elder.getCommunityId(), "CARE_WORKER");
        }
        if (assignee == null || !"ACTIVE".equalsIgnoreCase(assignee.getStatus())
                || !"STAFF".equalsIgnoreCase(assignee.getAccountKind())
                || !dto.getAssigneeUserId().equals(assignee.getUserId())
                || !isActiveDispatchAssignment(assignment, dto.getAssigneeUserId(), elder.getCommunityId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "指派的目标工作人员不存在");
        }

        String actionType = order.getCurrentAssigneeId() == null ? "DISPATCH" : "REASSIGN";
        String fromStatus = order.getStatus();

        order.setCurrentAssigneeId(dto.getAssigneeUserId());
        order.setDispatchTime(LocalDateTime.now());
        order.setStatus("DISPATCHED");
        workOrderMapper.updateById(order);

        // 1. 记录指派历史
        WorkOrderAssignmentHistory assignHistory = new WorkOrderAssignmentHistory();
        assignHistory.setWorkOrderId(workOrderId);
        assignHistory.setActionType(actionType);
        assignHistory.setOperatorUserId(operatorId);
        assignHistory.setAssignedToUserId(dto.getAssigneeUserId());
        assignHistory.setReason(dto.getReason());
        assignmentHistoryMapper.insert(assignHistory);

        // 2. 记录流转审计历史
        recordActionHistory(workOrderId, operatorId, "COMMUNITY_ADMIN", fromStatus, "DISPATCHED",
                "DISPATCH", dto.getReason());
    }

    @Override
    @Transactional
    public void acceptOrder(Long workOrderId, Long workerUserId) {
        WorkerActor actor = requireWorkerActor(workerUserId);
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在");
        }
        requireWorkerOrderAccess(order, actor);
        assertStatus(order, "DISPATCHED");
        if (order.getCurrentAssigneeId() == null || !order.getCurrentAssigneeId().equals(workerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "您不是该工单的责任人，无权接单");
        }

        String fromStatus = order.getStatus();
        order.setStatus("ACCEPTED");
        order.setAcceptTime(LocalDateTime.now());
        updateWorkOrderOrThrow(order);

        recordActionHistory(workOrderId, workerUserId, actor.historyRole, fromStatus, "ACCEPTED",
                "ACCEPT", actor.careWorker ? "护工已确认接单响应" : "网格员已确认接单响应");
    }

    @Override
    @Transactional
    public void rejectOrder(Long workOrderId, String reason, Long workerUserId) {
        requireCurrentActor(workerUserId, "GRID_WORKER");
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在");
        }
        requireOrderInCurrentCommunity(order, "GRID_WORKER");
        assertStatus(order, "DISPATCHED", "ACCEPTED");
        if (order.getCurrentAssigneeId() == null || !order.getCurrentAssigneeId().equals(workerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "您不是该工单的责任人，无权拒单");
        }

        String fromStatus = order.getStatus();
        order.setCurrentAssigneeId(null);
        order.setStatus("PENDING_DISPATCH");
        workOrderMapper.updateById(order);

        WorkOrderAssignmentHistory assignHistory = new WorkOrderAssignmentHistory();
        assignHistory.setWorkOrderId(workOrderId);
        assignHistory.setActionType("REJECT_RETURN");
        assignHistory.setOperatorUserId(workerUserId);
        assignHistory.setAssignedToUserId(workerUserId);
        assignHistory.setReason("拒单退回待派: " + reason);
        assignmentHistoryMapper.insert(assignHistory);

        recordActionHistory(workOrderId, workerUserId, "COMMUNITY_WORKER", fromStatus, "PENDING_DISPATCH",
                "REJECT", reason);
    }

    @Override
    @Transactional
    public void startService(Long workOrderId, Long workerUserId) {
        WorkerActor actor = requireWorkerActor(workerUserId);
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在");
        }
        requireWorkerOrderAccess(order, actor);
        assertStatus(order, "ACCEPTED");
        if (order.getCurrentAssigneeId() == null || !order.getCurrentAssigneeId().equals(workerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "您不是该工单的责任人，无权开始服务");
        }

        String fromStatus = order.getStatus();
        order.setStatus("PROCESSING");
        order.setServiceStartTime(LocalDateTime.now());
        updateWorkOrderOrThrow(order);

        recordActionHistory(workOrderId, workerUserId, actor.historyRole, fromStatus, "PROCESSING",
                "START_SERVICE", actor.careWorker ? "护工已到达长辈现场并开始提供服务" : "工作人员已到达长辈现场并开始提供服务");
    }

    @Override
    @Transactional
    public void submitExecutionResult(Long workOrderId, WorkOrderExecutionDTO dto, Long workerUserId) {
        WorkerActor actor = requireWorkerActor(workerUserId);
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在");
        }
        requireWorkerOrderAccess(order, actor);
        assertStatus(order, "PROCESSING");
        if (order.getCurrentAssigneeId() == null || !order.getCurrentAssigneeId().equals(workerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "您不是该工单的责任人，无权提交服务总结");
        }

        // 校验照片凭据或免照理由
        if (Boolean.TRUE.equals(dto.getIsExemptPhoto())) {
            if (!StringUtils.hasText(dto.getPhotoExemptionReason()) || dto.getPhotoExemptionReason().trim().length() < 5) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "申请免拍照结案必须详细填写不少于5个字的合规理由");
            }
            order.setResultPhotoRequired(false);
            order.setPhotoExemptionReason(dto.getPhotoExemptionReason().trim());
            order.setPhotoUrls(null);
        } else {
            if (dto.getPhotoUrls() == null || dto.getPhotoUrls().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "非免照服务必须上传现场履约照片凭据");
            }
            order.setResultPhotoRequired(true);
            order.setPhotoExemptionReason(null);
            order.setPhotoUrls(String.join(";", dto.getPhotoUrls()));
        }

        String fromStatus = order.getStatus();
        order.setServiceFinishTime(LocalDateTime.now());
        order.setServiceResultSummary(dto.getResultSummary());
        order.setStatus("PENDING_RESULT_REVIEW");
        updateWorkOrderOrThrow(order);

        recordActionHistory(workOrderId, workerUserId, actor.historyRole, fromStatus, "PENDING_RESULT_REVIEW",
                "SUBMIT_RESULT", "服务人员已完成上门履约并提交结案凭据，等待社区复核");
    }

    @Override
    public void assertCurrentCareWorkerMayUpload(Long workOrderId) {
        CareWorkerScope scope = requireCareWorkerScope(null);
        if (workOrderId == null || workOrderId <= 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireCareWorkerOrder(order, scope);
    }

    @Override
    @Transactional
    public void reviewWorkOrder(Long workOrderId, WorkOrderReviewDTO dto, Long reviewerId) {
        requireCurrentActor(reviewerId, "COMMUNITY_ADMIN");
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在");
        }
        requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        // 社区和角色授权完成后再拒绝执行人自审，避免越权请求探测其他社区工单。
        if (order.getCurrentAssigneeId() != null && order.getCurrentAssigneeId().equals(reviewerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SELF_REVIEW, "操作员不能审核自己执行结案的工单！必须由其他社区管理员审核。");
        }
        assertStatus(order, "PENDING_RESULT_REVIEW");

        String fromStatus = order.getStatus();

        if ("APPROVE".equalsIgnoreCase(dto.getAction())) {
            order.setStatus("CLOSED");
            order.setReviewerUserId(reviewerId);
            order.setReviewedAt(LocalDateTime.now());
            order.setReviewComment(dto.getReviewComment());
            workOrderMapper.updateById(order);

            // 若由预约派生，联动更新 service_reservation 为 COMPLETED
            if ("RESERVATION".equals(order.getSourceType())) {
                ReservationExecutionLink link = executionLinkMapper.selectOne(
                        new LambdaQueryWrapper<ReservationExecutionLink>().eq(ReservationExecutionLink::getWorkOrderId, workOrderId)
                );
                if (link != null) {
                    ServiceReservation r = reservationMapper.selectById(link.getReservationId());
                    if (r != null) {
                        r.setStatus("COMPLETED");
                        reservationMapper.updateById(r);
                    }
                }
            }

            recordActionHistory(workOrderId, reviewerId, "COMMUNITY_ADMIN", fromStatus, "CLOSED",
                    "CLOSE", "审核通过：结案归档");

            auditLogService.recordAudit(
                    reviewerId,
                    "COMMUNITY_ADMIN",
                    order.getCommunityId(),
                    "WORK_ORDER",
                    String.valueOf(workOrderId),
                    "WORK_ORDER_APPROVE",
                    "SUCCESS",
                    "127.0.0.1",
                    "{\"orderNo\":\"" + order.getOrderNo() + "\",\"action\":\"APPROVE\",\"comment\":\"" + (dto.getReviewComment() != null ? dto.getReviewComment() : "") + "\"}"
            );
        } else {
            // 审核驳回退回重办
            order.setStatus("PROCESSING");
            order.setReviewComment("退回重办: " + dto.getReviewComment());
            workOrderMapper.updateById(order);

            recordActionHistory(workOrderId, reviewerId, "COMMUNITY_ADMIN", fromStatus, "PROCESSING",
                    "RETURN_REVIEW", "审核退回重办: " + dto.getReviewComment());

            auditLogService.recordAudit(
                    reviewerId,
                    "COMMUNITY_ADMIN",
                    order.getCommunityId(),
                    "WORK_ORDER",
                    String.valueOf(workOrderId),
                    "WORK_ORDER_REJECT",
                    "SUCCESS",
                    "127.0.0.1",
                    "{\"orderNo\":\"" + order.getOrderNo() + "\",\"action\":\"REJECT\",\"comment\":\"" + (dto.getReviewComment() != null ? dto.getReviewComment() : "") + "\"}"
            );
        }
    }

    private void assertStatus(WorkOrder order, String... allowedStatuses) {
        String status = order.getStatus();
        if (status == null || Arrays.stream(allowedStatuses).noneMatch(s -> s.equalsIgnoreCase(status))) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "工单当前状态 [" + status + "] 不支持此操作");
        }
    }

    private Long resolveSessionCommunityId() {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        String communityId = current.getCommunityId();
        if (!StringUtils.hasText(communityId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前角色未分配有效社区范围");
        }
        try {
            return Long.parseLong(communityId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前角色未分配有效社区范围");
        }
    }

    private ElderProfile requireOrderInCurrentCommunity(WorkOrder order, String... allowedRoles) {
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(order.getElderId(), allowedRoles);
        requireOrderCommunityMatchesElder(order, elder);
        return elder;
    }

    private void requireOrderCommunityMatchesElder(WorkOrder order, ElderProfile elder) {
        if (order.getCommunityId() == null || elder.getCommunityId() == null
                || !order.getCommunityId().equals(elder.getCommunityId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
    }

    private Long requireCurrentActor(Long suppliedUserId, String... allowedRoles) {
        authorizationPolicy.requireRole(allowedRoles);
        Long currentUserId = currentUserId(authorizationPolicy.requireCurrentUser());
        if (suppliedUserId == null || !suppliedUserId.equals(currentUserId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "操作身份与当前会话不一致");
        }
        return currentUserId;
    }

    private WorkerActor requireWorkerActor(Long suppliedUserId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        if (isCurrentRole(current, "CARE_WORKER")) {
            return WorkerActor.forCareWorker(requireCareWorkerScope(suppliedUserId));
        }
        requireCurrentActor(suppliedUserId, "GRID_WORKER");
        return WorkerActor.forGridWorker();
    }

    private CareWorkerScope requireCareWorkerScope(Long suppliedUserId) {
        authorizationPolicy.requireRole("CARE_WORKER");
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        Long currentId = currentUserId(current);
        if (suppliedUserId != null && !suppliedUserId.equals(currentId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只能操作本人的待办工单");
        }
        Long communityId = resolveSessionCommunityId();
        if (!"STAFF".equalsIgnoreCase(current.getAccountKind())) {
            throw careWorkerAccessDenied();
        }

        UserAccount account = userAccountMapper.selectById(currentId);
        if (account == null || !"ACTIVE".equalsIgnoreCase(account.getStatus())
                || !"STAFF".equalsIgnoreCase(account.getAccountKind())) {
            throw careWorkerAccessDenied();
        }

        UserRole assignment = userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, currentId)
                .eq(UserRole::getRoleCode, "CARE_WORKER")
                .eq(UserRole::getCommunityId, communityId)
                .eq(UserRole::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (assignment == null || !currentId.equals(assignment.getUserId())
                || !"CARE_WORKER".equalsIgnoreCase(assignment.getRoleCode())
                || !communityId.equals(assignment.getCommunityId())
                || !"ACTIVE".equalsIgnoreCase(assignment.getStatus())) {
            throw careWorkerAccessDenied();
        }
        return new CareWorkerScope(currentId, communityId);
    }

    private void requireWorkerOrderAccess(WorkOrder order, WorkerActor actor) {
        if (actor.careWorker) {
            requireCareWorkerOrder(order, actor.careWorkerScope);
        } else {
            requireOrderInCurrentCommunity(order, "GRID_WORKER");
        }
    }

    private void updateWorkOrderOrThrow(WorkOrder order) {
        if (workOrderMapper.updateById(order) < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "工单已被其他操作更新，请刷新后重试");
        }
    }

    private ElderProfile requireCareWorkerOrder(WorkOrder order, CareWorkerScope scope) {
        if (order == null || isCareWorkerTerminalOrder(order) || order.getCurrentAssigneeId() == null
                || !scope.userId.equals(order.getCurrentAssigneeId())
                || order.getCommunityId() == null || !scope.communityId.equals(order.getCommunityId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        ElderProfile elder = elderProfileMapper.selectById(order.getElderId());
        if (elder == null || elder.getCommunityId() == null
                || !scope.communityId.equals(elder.getCommunityId())
                || !order.getCommunityId().equals(elder.getCommunityId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        return elder;
    }

    private boolean isCareWorkerTerminalOrder(WorkOrder order) {
        return order != null && StringUtils.hasText(order.getStatus())
                && CARE_WORKER_TERMINAL_STATUSES.stream()
                .anyMatch(status -> status.equalsIgnoreCase(order.getStatus()));
    }

    private BusinessException careWorkerAccessDenied() {
        return new BusinessException(ErrorCode.ACCESS_DENIED, "当前护工账号或社区角色无效");
    }

    private boolean isActiveDispatchAssignment(UserRole assignment, Long userId, Long communityId) {
        return assignment != null && userId != null && communityId != null
                && userId.equals(assignment.getUserId())
                && communityId.equals(assignment.getCommunityId())
                && "ACTIVE".equalsIgnoreCase(assignment.getStatus())
                && ("GRID_WORKER".equalsIgnoreCase(assignment.getRoleCode())
                || "CARE_WORKER".equalsIgnoreCase(assignment.getRoleCode()));
    }

    private UserRole findActiveDispatchAssignment(Long userId, Long communityId, String roleCode) {
        return userRoleMapper.selectOne(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleCode, roleCode)
                .eq(UserRole::getCommunityId, communityId)
                .eq(UserRole::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    private boolean isCurrentRole(CurrentUser user, String role) {
        return user.getCurrentRole() != null && role.equalsIgnoreCase(user.getCurrentRole());
    }

    private Long currentUserId(CurrentUser current) {
        try {
            return Long.parseLong(current.getUserId());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
    }

    private void recordActionHistory(Long workOrderId, Long operatorId, String role, String fromStatus,
                                     String toStatus, String actionName, String comment) {
        WorkOrderActionHistory history = new WorkOrderActionHistory();
        history.setWorkOrderId(workOrderId);
        history.setOperatorUserId(operatorId);
        history.setOperatorRole(role);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setActionName(actionName);
        history.setComment(comment);
        String traceId = MDC.get("traceId");
        history.setTraceId(StringUtils.hasText(traceId) ? traceId : "TR-" + UUID.randomUUID().toString().substring(0, 8));
        actionHistoryMapper.insert(history);
    }

    private List<WorkOrderVO> convertToVOList(List<WorkOrder> list) {
        List<WorkOrderVO> result = new ArrayList<>();
        for (WorkOrder o : list) {
            result.add(convertToVO(o));
        }
        return result;
    }

    private WorkOrderVO convertToCareWorkerVO(WorkOrder order, ElderProfile elder) {
        WorkOrderVO vo = new WorkOrderVO();
        vo.setWorkOrderId(order.getWorkOrderId());
        vo.setOrderNo(order.getOrderNo());
        vo.setElderName(elder.getName());
        vo.setElderAddress(elder.getAddress());
        vo.setServiceType(order.getServiceType());
        vo.setOrderTitle(order.getOrderTitle());
        vo.setOrderDescription(order.getOrderDescription());
        vo.setUrgencyLevel(order.getUrgencyLevel());
        vo.setStatus(order.getStatus());
        vo.setStatusName(formatOrderStatus(order.getStatus()));
        vo.setDispatchTime(order.getDispatchTime());
        vo.setAcceptTime(order.getAcceptTime());
        vo.setServiceStartTime(order.getServiceStartTime());
        vo.setServiceFinishTime(order.getServiceFinishTime());
        vo.setServiceResultSummary(order.getServiceResultSummary());
        vo.setResultPhotoRequired(order.getResultPhotoRequired());
        vo.setPhotoExemptionReason(order.getPhotoExemptionReason());
        if (StringUtils.hasText(order.getPhotoUrls())) {
            vo.setPhotoUrls(Arrays.asList(order.getPhotoUrls().split(";")));
        } else {
            vo.setPhotoUrls(Collections.emptyList());
        }
        vo.setCreatedAt(order.getCreatedAt());
        vo.setUpdatedAt(order.getUpdatedAt());
        return vo;
    }

    private WorkOrderVO convertToVO(WorkOrder o) {
        WorkOrderVO vo = new WorkOrderVO();
        vo.setWorkOrderId(o.getWorkOrderId());
        vo.setOrderNo(o.getOrderNo());
        vo.setSourceType(o.getSourceType());
        vo.setSourceReferenceId(o.getSourceReferenceId());
        vo.setElderId(o.getElderId());
        vo.setCommunityId(o.getCommunityId());
        vo.setServiceType(o.getServiceType());
        vo.setOrderTitle(o.getOrderTitle());
        vo.setOrderDescription(o.getOrderDescription());
        vo.setUrgencyLevel(o.getUrgencyLevel());
        vo.setStatus(o.getStatus());
        vo.setStatusName(formatOrderStatus(o.getStatus()));
        vo.setCurrentAssigneeId(o.getCurrentAssigneeId());
        vo.setDispatchTime(o.getDispatchTime());
        vo.setAcceptTime(o.getAcceptTime());
        vo.setServiceStartTime(o.getServiceStartTime());
        vo.setServiceFinishTime(o.getServiceFinishTime());
        vo.setServiceResultSummary(o.getServiceResultSummary());
        vo.setResultPhotoRequired(o.getResultPhotoRequired());
        vo.setPhotoExemptionReason(o.getPhotoExemptionReason());
        if (StringUtils.hasText(o.getPhotoUrls())) {
            vo.setPhotoUrls(Arrays.asList(o.getPhotoUrls().split(";")));
        } else {
            vo.setPhotoUrls(Collections.emptyList());
        }
        vo.setReviewerUserId(o.getReviewerUserId());
        vo.setReviewedAt(o.getReviewedAt());
        vo.setReviewComment(o.getReviewComment());
        vo.setCreatedAt(o.getCreatedAt());
        vo.setUpdatedAt(o.getUpdatedAt());

        // 补充老人信息
        ElderProfile elder = elderProfileMapper.selectById(o.getElderId());
        if (elder != null) {
            vo.setElderName(elder.getName());
            vo.setElderPhone(elder.getContactPhone());
            vo.setElderAddress(elder.getAddress());
        }

        // 补充执行人员信息
        if (o.getCurrentAssigneeId() != null) {
            UserAccount worker = userAccountMapper.selectById(o.getCurrentAssigneeId());
            if (worker != null) {
                vo.setAssigneeName("网格员 (" + worker.getUserId() + ")");
                vo.setAssigneePhone(worker.getPhoneNormalized());
            }
        }

        // 补充审核人信息
        if (o.getReviewerUserId() != null) {
            UserAccount reviewer = userAccountMapper.selectById(o.getReviewerUserId());
            if (reviewer != null) {
                vo.setReviewerName("社区管理员 (" + reviewer.getUserId() + ")");
            }
        }

        return vo;
    }

    private String formatOrderStatus(String status) {
        if (status == null) return "未知状态";
        switch (status) {
            case "PENDING_DISPATCH": return "待派单";
            case "DISPATCHED": return "已派单(待接单)";
            case "ACCEPTED": return "已接单(赶往中)";
            case "PROCESSING": return "服务执行中";
            case "PENDING_RESULT_REVIEW": return "已完成待审核";
            case "CLOSED": return "已结案归档";
            case "VOIDED": return "已作废";
            default: return status;
        }
    }

    private static final class CareWorkerScope {
        private final Long userId;
        private final Long communityId;

        private CareWorkerScope(Long userId, Long communityId) {
            this.userId = userId;
            this.communityId = communityId;
        }
    }

    private static final class WorkerActor {
        private final boolean careWorker;
        private final CareWorkerScope careWorkerScope;
        private final String historyRole;

        private WorkerActor(boolean careWorker, CareWorkerScope careWorkerScope, String historyRole) {
            this.careWorker = careWorker;
            this.careWorkerScope = careWorkerScope;
            this.historyRole = historyRole;
        }

        private static WorkerActor forCareWorker(CareWorkerScope scope) {
            return new WorkerActor(true, scope, "CARE_WORKER");
        }

        private static WorkerActor forGridWorker() {
            return new WorkerActor(false, null, "COMMUNITY_WORKER");
        }
    }
}
