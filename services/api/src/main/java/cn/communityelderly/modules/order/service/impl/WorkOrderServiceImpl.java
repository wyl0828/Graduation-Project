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
import cn.communityelderly.modules.emergency.entity.WorkOrderHandoff;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderHandoffMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderReviewDTO;
import cn.communityelderly.modules.order.dto.WorkOrderVO;
import cn.communityelderly.modules.order.dto.ServiceStopRequestDTO;
import cn.communityelderly.modules.order.dto.ServiceStopRequestVO;
import cn.communityelderly.modules.order.dto.ServiceStopReviewDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffAcceptDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffRequestDTO;
import cn.communityelderly.modules.order.dto.WorkOrderHandoffRejectDTO;
import cn.communityelderly.modules.order.entity.ServiceStopRequest;
import cn.communityelderly.modules.order.mapper.ServiceStopRequestMapper;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.order.service.FileEvidenceService;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WorkOrderServiceImpl implements WorkOrderService {

    private static final List<String> CARE_WORKER_TERMINAL_STATUSES = List.of(
            "CLOSED", "VOIDED", "TERMINATED_PRE_ACCEPTANCE", "SERVICE_STOPPED");
    private static final List<String> WORK_ORDER_TERMINAL_STATUSES = List.of(
            "CLOSED", "VOIDED", "TERMINATED_PRE_ACCEPTANCE", "SERVICE_STOPPED");
    private static final List<String> HANDOFF_TRANSFERABLE_STATUSES = List.of(
            "DISPATCHED", "ACCEPTED", "PROCESSING", "PENDING_RESULT_REVIEW");

    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderAssignmentHistoryMapper assignmentHistoryMapper;
    private final WorkOrderActionHistoryMapper actionHistoryMapper;
    private final WorkOrderHandoffMapper handoffMapper;
    private final ServiceStopRequestMapper stopRequestMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;
    private final ReservationExecutionLinkMapper executionLinkMapper;
    private final ServiceReservationMapper reservationMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final FileEvidenceService evidenceService;

    public WorkOrderServiceImpl(WorkOrderMapper workOrderMapper,
                                WorkOrderAssignmentHistoryMapper assignmentHistoryMapper,
                                WorkOrderActionHistoryMapper actionHistoryMapper,
                                WorkOrderHandoffMapper handoffMapper,
                                ServiceStopRequestMapper stopRequestMapper,
                                ElderProfileMapper elderProfileMapper,
                                UserAccountMapper userAccountMapper,
                                UserRoleMapper userRoleMapper,
                                ReservationExecutionLinkMapper executionLinkMapper,
                                ServiceReservationMapper reservationMapper,
                                ObjectAuthorizationPolicy authorizationPolicy,
                                AuditLogService auditLogService,
                                NotificationService notificationService,
                                ObjectMapper objectMapper,
                                FileEvidenceService evidenceService,
                                Clock clock) {
        this.workOrderMapper = workOrderMapper;
        this.assignmentHistoryMapper = assignmentHistoryMapper;
        this.actionHistoryMapper = actionHistoryMapper;
        this.handoffMapper = handoffMapper;
        this.stopRequestMapper = stopRequestMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
        this.executionLinkMapper = executionLinkMapper;
        this.reservationMapper = reservationMapper;
        this.authorizationPolicy = authorizationPolicy;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.evidenceService = evidenceService;
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
            query.notIn(WorkOrder::getStatus, WORK_ORDER_TERMINAL_STATUSES);
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
        requireActiveStaffRole(selfId, "GRID_WORKER");
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
            query.notIn(WorkOrder::getStatus, WORK_ORDER_TERMINAL_STATUSES);
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
        order.setDispatchTime(LocalDateTime.now(clock));
        order.setStatus("DISPATCHED");
        updateWorkOrderOrThrow(order);

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
        order.setAcceptTime(LocalDateTime.now(clock));
        updateWorkOrderOrThrow(order);

        recordActionHistory(workOrderId, workerUserId, actor.historyRole, fromStatus, "ACCEPTED",
                "ACCEPT", actor.careWorker ? "护工已确认接单响应" : "网格员已确认接单响应");
    }

    @Override
    @Transactional
    public void rejectOrder(Long workOrderId, String reason, Long workerUserId) {
        requireCurrentActor(workerUserId, "GRID_WORKER");
        requireActiveStaffRole(workerUserId, "GRID_WORKER");
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
        updateWorkOrderOrThrow(order);

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
        order.setServiceStartTime(LocalDateTime.now(clock));
        updateWorkOrderOrThrow(order);

        recordActionHistory(workOrderId, workerUserId, actor.historyRole, fromStatus, "PROCESSING",
                "START_SERVICE", actor.careWorker ? "护工已到达长辈现场并开始提供服务" : "工作人员已到达长辈现场并开始提供服务");
    }

    @Override
    @Transactional
    public void submitExecutionResult(Long workOrderId, WorkOrderExecutionDTO dto, Long workerUserId) {
        WorkerActor actor = requireWorkerActor(workerUserId);
        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "工单不存在");
        }
        requireWorkerOrderAccess(order, actor);
        assertStatus(order, "PROCESSING");
        if (order.getCurrentAssigneeId() == null || !order.getCurrentAssigneeId().equals(workerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "您不是该工单的责任人，无权提交服务总结");
        }

        List<Long> submittedVersions = List.of();
        // 校验照片凭据或免照理由
        if (Boolean.TRUE.equals(dto.getIsExemptPhoto())) {
            if (!StringUtils.hasText(dto.getPhotoExemptionReason()) || dto.getPhotoExemptionReason().trim().length() < 5) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "申请免拍照结案必须详细填写不少于5个字的合规理由");
            }
            order.setResultPhotoRequired(false);
            order.setPhotoExemptionReason(dto.getPhotoExemptionReason().trim());

        } else {
            try {
                if (dto.getEvidenceAssetUuids() == null || dto.getEvidenceAssetUuids().isEmpty())
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "必须提交已校验的文件版本，历史照片地址不能作为新证据");
                submittedVersions = evidenceService.resolveAndLink(workOrderId, dto.getEvidenceAssetUuids());
            } catch (BusinessException exception) {
                evidenceService.rejected(workOrderId, "FILE_EVIDENCE_SUBMIT", exception.getErrorCode());
                throw exception;
            }
            order.setResultPhotoRequired(true);
            order.setPhotoExemptionReason(null);
        }

        String fromStatus = order.getStatus();
        order.setServiceFinishTime(LocalDateTime.now(clock));
        order.setServiceResultSummary(dto.getResultSummary());
        order.setStatus("PENDING_RESULT_REVIEW");
        updateWorkOrderOrThrow(order);

        recordActionHistory(workOrderId, workerUserId, actor.historyRole, fromStatus, "PENDING_RESULT_REVIEW",
                "SUBMIT_RESULT", "服务人员已完成上门履约并提交结案凭据，等待社区复核",
                jsonPayload(payload("evidenceVersionIds", submittedVersions)));
    }

    @Override
    public void assertCurrentMayUploadEvidence(Long workOrderId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        if (isCurrentRole(current, "CARE_WORKER")) {
            requireCareWorkerOrder(order, requireCareWorkerScope(null));
        } else if (isCurrentRole(current, "GRID_WORKER")) {
            Long workerId = requireCurrentActor(currentUserId(current), "GRID_WORKER");
            requireActiveStaffRole(workerId, "GRID_WORKER");
            requireOrderInCurrentCommunity(order, "GRID_WORKER");
            if (!workerId.equals(order.getCurrentAssigneeId()))
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        } else {
            requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        }
        assertStatus(order, "PROCESSING");
    }

    @Override
    public void assertCurrentMayReadEvidence(Long workOrderId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        if (isCurrentRole(current, "CARE_WORKER")) {
            requireCareWorkerOrder(order, requireCareWorkerScope(null));
        } else if (isCurrentRole(current, "GRID_WORKER")) {
            Long workerId = currentUserId(current);
            requireActiveStaffRole(workerId, "GRID_WORKER");
            requireOrderInCurrentCommunity(order, "GRID_WORKER");
            if (!workerId.equals(order.getCurrentAssigneeId()))
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        } else if (isCurrentRole(current, "COMMUNITY_ADMIN")) {
            requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        } else {
            authorizationPolicy.requireRole("ELDER", "CHILD");
            ElderProfile elder = authorizationPolicy.requireElderAccess(order.getElderId());
            requireOrderCommunityMatchesElder(order, elder);
            if (!"CLOSED".equals(order.getStatus()))
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
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
        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
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
            try {
                if (!Boolean.FALSE.equals(order.getResultPhotoRequired())) {
                    List<Long> versions = evidenceService.currentVersions(workOrderId);
                    if (versions == null || versions.isEmpty())
                        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少已校验照片凭据，不能结案");
                    evidenceService.validate(workOrderId, versions, true);
                } else if (!StringUtils.hasText(order.getPhotoExemptionReason()) || order.getPhotoExemptionReason().trim().length() < 5) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "缺少有效免照理由，不能结案");
                }
            } catch (BusinessException exception) {
                evidenceService.rejected(workOrderId, "FILE_EVIDENCE_CLOSE", exception.getErrorCode());
                throw exception;
            }
            order.setStatus("CLOSED");
            order.setReviewerUserId(reviewerId);
            order.setReviewedAt(LocalDateTime.now(clock));
            order.setReviewComment(dto.getReviewComment());
            updateWorkOrderOrThrow(order);

            // 若由预约派生，联动更新 service_reservation 为 COMPLETED
            if ("RESERVATION".equals(order.getSourceType())) {
                ReservationExecutionLink link = executionLinkMapper.selectOne(
                        new LambdaQueryWrapper<ReservationExecutionLink>().eq(ReservationExecutionLink::getWorkOrderId, workOrderId)
                );
                if (link != null) {
                    ServiceReservation r = reservationMapper.selectById(link.getReservationId());
                    if (r != null) {
                        r.setStatus("COMPLETED");
                        if (reservationMapper.updateById(r) < 1) {
                            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                                    "关联预约已被其他操作更新，请刷新后重试");
                        }
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
            updateWorkOrderOrThrow(order);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminateBeforeAcceptance(Long workOrderId, Long operatorId, String reason) {
        Long actorId = requireCurrentActor(operatorId, "COMMUNITY_ADMIN");
        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        if (!"PENDING_DISPATCH".equalsIgnoreCase(order.getStatus())
                && !"DISPATCHED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "工单已接单或已被其他操作更新，不能按未接单取消");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "取消原因不能为空");
        }

        String fromStatus = order.getStatus();
        order.setStatus("TERMINATED_PRE_ACCEPTANCE");
        updateWorkOrderOrThrow(order);

        String details = jsonPayload(payload(
                "reason", reason.trim(),
                "fromStatus", fromStatus,
                "toStatus", "TERMINATED_PRE_ACCEPTANCE"));
        recordActionHistory(workOrderId, actorId, "COMMUNITY_ADMIN", fromStatus,
                "TERMINATED_PRE_ACCEPTANCE", "TERMINATE_PRE_ACCEPTANCE", reason.trim(), details);
        auditLogService.recordAudit(actorId, "COMMUNITY_ADMIN", order.getCommunityId(),
                "WORK_ORDER", String.valueOf(workOrderId), "WORK_ORDER_TERMINATE_PRE_ACCEPTANCE",
                "SUCCESS", "127.0.0.1", details);
        publishNotification("WORK_ORDER_TERMINATED_PRE_ACCEPTANCE",
                "WORK_ORDER_TERMINATED_PRE_ACCEPTANCE:" + workOrderId,
                workOrderId, "预约服务已取消", "工单在工作人员接单前已取消：" + reason.trim(), details,
                cancellationRecipients(order));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markOpenOrdersForWorkerHandoff(Long workerUserId, Long communityId,
                                               Long operatorId, String reason) {
        Long actorId = requireCurrentActor(operatorId, "COMMUNITY_ADMIN");
        if (workerUserId == null || communityId == null || !StringUtils.hasText(reason)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "工作人员、社区和失效原因不能为空");
        }

        List<WorkOrder> affectedOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getCurrentAssigneeId, workerUserId)
                        .eq(WorkOrder::getCommunityId, communityId)
                        .notIn(WorkOrder::getStatus, WORK_ORDER_TERMINAL_STATUSES)
                        .orderByAsc(WorkOrder::getWorkOrderId)
                        .last("FOR UPDATE"));
        if (affectedOrders == null || affectedOrders.isEmpty()) {
            return;
        }
        if (affectedOrders.stream().anyMatch(order -> !isHandoffTransferableStatus(order.getStatus()))) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "工作人员仍有未支持责任交接的工单状态，不能撤销其工作角色");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> administrators = activeCommunityAdminIds(communityId);
        for (WorkOrder order : affectedOrders) {
            WorkOrderHandoff existing = handoffMapper.selectOne(
                    new LambdaQueryWrapper<WorkOrderHandoff>()
                            .eq(WorkOrderHandoff::getWorkOrderId, order.getWorkOrderId())
                            .eq(WorkOrderHandoff::getFromUserId, workerUserId)
                            .in(WorkOrderHandoff::getStatus, List.of("REASSIGNMENT_REQUIRED", "REQUESTED"))
                            .last("LIMIT 1 FOR UPDATE"));
            if (existing != null) {
                continue;
            }

            String fromStatus = order.getStatus();
            WorkOrderHandoff handoff = new WorkOrderHandoff();
            handoff.setWorkOrderId(order.getWorkOrderId());
            handoff.setFromUserId(workerUserId);
            handoff.setToUserId(null);
            handoff.setStatus("REASSIGNMENT_REQUIRED");
            handoff.setHandoffReason(reason.trim());
            handoff.setRequestedAt(now);
            if (handoffMapper.insert(handoff) < 1) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "工单责任交接记录写入失败");
            }

            // Bump row_version without changing the assignee or progress. An in-flight worker
            // mutation loaded before role revocation must lose its optimistic-lock race.
            order.setUpdatedAt(now);
            updateWorkOrderOrThrow(order);

            String details = jsonPayload(payload(
                    "handoffId", handoff.getHandoffId(),
                    "fromUserId", workerUserId,
                    "toUserId", null,
                    "reason", reason.trim(),
                    "status", "REASSIGNMENT_REQUIRED"));
            recordActionHistory(order.getWorkOrderId(), actorId, "COMMUNITY_ADMIN", fromStatus,
                    fromStatus, "HANDOFF_REQUIRED", reason.trim(), details);
            auditLogService.recordAudit(actorId, "COMMUNITY_ADMIN", order.getCommunityId(),
                    "WORK_ORDER", String.valueOf(order.getWorkOrderId()), "WORK_ORDER_HANDOFF_REQUIRED",
                    "SUCCESS", "127.0.0.1", details);
            publishNotification("WORK_ORDER_HANDOFF_REQUIRED",
                    "WORK_ORDER_HANDOFF_REQUIRED:" + handoff.getHandoffId(),
                    order.getWorkOrderId(), "工单待重新交接",
                    "工作人员角色失效，工单需安排新的责任人：" + order.getOrderTitle(), details,
                    administrators);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assertElderMayBeDeactivated(Long elderId) {
        List<WorkOrder> openOrders = workOrderMapper.selectList(
                new LambdaQueryWrapper<WorkOrder>()
                        .eq(WorkOrder::getElderId, elderId)
                        .notIn(WorkOrder::getStatus, WORK_ORDER_TERMINAL_STATUSES)
                        .orderByAsc(WorkOrder::getWorkOrderId)
                        .last("FOR UPDATE"));
        if (openOrders == null || openOrders.isEmpty()) {
            return;
        }
        for (WorkOrder order : openOrders) {
            Long currentAssigneeId = order.getCurrentAssigneeId();
            if (currentAssigneeId == null) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                        "老人档案仍有关联未结工单，请先结案或完成责任交接");
            }
            WorkOrderHandoff latestHandoff = handoffMapper.selectOne(
                    new LambdaQueryWrapper<WorkOrderHandoff>()
                            .eq(WorkOrderHandoff::getWorkOrderId, order.getWorkOrderId())
                            .orderByDesc(WorkOrderHandoff::getHandoffId)
                            .last("LIMIT 1 FOR UPDATE"));
            if (latestHandoff == null
                    || !"ACCEPTED".equalsIgnoreCase(latestHandoff.getStatus())
                    || !currentAssigneeId.equals(latestHandoff.getToUserId())
                    || currentAssigneeId.equals(latestHandoff.getFromUserId())
                    || latestHandoff.getRespondedAt() == null) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                        "老人档案仍有关联未结工单，请先结案或完成责任交接");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long requestHandoff(Long workOrderId, WorkOrderHandoffRequestDTO dto, Long operatorId) {
        Long actorId = requireCurrentActor(operatorId, "COMMUNITY_ADMIN");
        if (dto == null || dto.getToUserId() == null || dto.getToUserId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "交接目标工作人员无效");
        }
        String reason = requireText(dto.getReason(), 255, "责任交接原因");

        // Discover community and current assignee without a row lock, then acquire the
        // target account/role locks before the order lock. Role revocation follows the
        // same role -> order sequence.
        WorkOrder observedOrder = workOrderMapper.selectById(workOrderId);
        if (observedOrder == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(observedOrder, "COMMUNITY_ADMIN");
        assertHandoffTransferableStatus(observedOrder);
        Long observedFromUserId = observedOrder.getCurrentAssigneeId();
        Long toUserId = dto.getToUserId();
        if (observedFromUserId == null || observedFromUserId.equals(toUserId)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "交接目标必须是当前承接人以外的工作人员");
        }

        lockEligibleWorkerAssignment(toUserId, observedOrder.getCommunityId());

        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        assertHandoffTransferableStatus(order);
        Long fromUserId = order.getCurrentAssigneeId();
        if (!java.util.Objects.equals(observedOrder.getCommunityId(), order.getCommunityId())
                || fromUserId == null || !fromUserId.equals(observedFromUserId)
                || fromUserId.equals(toUserId)) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "工单责任人已变化，请刷新后重新发起交接");
        }

        WorkOrderHandoff pending = handoffMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderHandoff>()
                        .eq(WorkOrderHandoff::getWorkOrderId, workOrderId)
                        .eq(WorkOrderHandoff::getFromUserId, fromUserId)
                        .eq(WorkOrderHandoff::getStatus, "REQUESTED")
                        .last("LIMIT 1 FOR UPDATE"));
        if (pending != null) {
            if (toUserId.equals(pending.getToUserId()) && reason.equals(pending.getHandoffReason())) {
                return pending.getHandoffId();
            }
        }

        LocalDateTime now = LocalDateTime.now(clock);
        WorkOrderHandoff handoff;
        String previousReason = null;
        if (pending != null) {
            previousReason = pending.getHandoffReason();
            handoff = new WorkOrderHandoff();
            handoff.setWorkOrderId(workOrderId);
            handoff.setFromUserId(fromUserId);
            handoff.setToUserId(toUserId);
            handoff.setStatus("REQUESTED");
            handoff.setHandoffReason(reason);
            handoff.setRequestedAt(now);
            if (handoffMapper.insert(handoff) < 1 || handoff.getHandoffId() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "责任交接申请写入失败");
            }
            String replacementComment = "管理员调整目标，原请求已关闭并建立新的交接记录";
            int closed = handoffMapper.update(null,
                    new UpdateWrapper<WorkOrderHandoff>()
                            .eq("handoff_id", pending.getHandoffId())
                            .eq("work_order_id", workOrderId)
                            .eq("from_user_id", fromUserId)
                            .eq("status", "REQUESTED")
                            .set("status", "CANCELLED")
                            .set("responded_at", now)
                            .set("response_comment", replacementComment));
            if (closed < 1) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                        "待确认交接已被其他操作处理，请刷新后重试");
            }
            pending.setStatus("CANCELLED");
            pending.setRespondedAt(now);
            pending.setResponseComment(replacementComment);

            String replacementDetails = jsonPayload(payload(
                    "previousHandoffId", pending.getHandoffId(),
                    "previousToUserId", pending.getToUserId(),
                    "previousReason", previousReason,
                    "replacementHandoffId", handoff.getHandoffId(),
                    "replacementToUserId", toUserId,
                    "replacementReason", reason,
                    "status", "CANCELLED",
                    "responseComment", replacementComment,
                    "respondedAt", now));
            recordActionHistory(workOrderId, actorId, "COMMUNITY_ADMIN", "REQUESTED", "CANCELLED",
                    "HANDOFF_TARGET_REPLACED", replacementComment, replacementDetails);
            auditLogService.recordAudit(actorId, "COMMUNITY_ADMIN", order.getCommunityId(),
                    "WORK_ORDER", String.valueOf(workOrderId), "WORK_ORDER_HANDOFF_TARGET_REPLACED",
                    "SUCCESS", "127.0.0.1", replacementDetails);
            List<Long> replacementRecipients = new ArrayList<>(Arrays.asList(fromUserId, pending.getToUserId()));
            replacementRecipients.addAll(activeCommunityAdminIds(order.getCommunityId()));
            publishNotification("WORK_ORDER_HANDOFF_TARGET_REPLACED",
                    "WORK_ORDER_HANDOFF_TARGET_REPLACED:" + pending.getHandoffId(), workOrderId,
                    "工单责任交接目标已调整",
                    "管理员已调整工单责任交接目标，原申请已关闭。", replacementDetails,
                    distinctIds(replacementRecipients));
        } else {
            handoff = handoffMapper.selectOne(
                    new LambdaQueryWrapper<WorkOrderHandoff>()
                            .eq(WorkOrderHandoff::getWorkOrderId, workOrderId)
                            .eq(WorkOrderHandoff::getFromUserId, fromUserId)
                            .eq(WorkOrderHandoff::getStatus, "REASSIGNMENT_REQUIRED")
                            .last("LIMIT 1 FOR UPDATE"));
            if (handoff == null) {
                handoff = new WorkOrderHandoff();
                handoff.setWorkOrderId(workOrderId);
                handoff.setFromUserId(fromUserId);
                handoff.setToUserId(toUserId);
                handoff.setStatus("REQUESTED");
                handoff.setHandoffReason(reason);
                handoff.setRequestedAt(now);
                if (handoffMapper.insert(handoff) < 1 || handoff.getHandoffId() == null) {
                    throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "责任交接申请写入失败");
                }
            } else {
                previousReason = handoff.getHandoffReason();
                int updated = handoffMapper.update(null,
                        new UpdateWrapper<WorkOrderHandoff>()
                                .eq("handoff_id", handoff.getHandoffId())
                                .eq("work_order_id", workOrderId)
                                .eq("from_user_id", fromUserId)
                                .eq("status", "REASSIGNMENT_REQUIRED")
                                .set("to_user_id", toUserId)
                                .set("status", "REQUESTED")
                                .set("handoff_reason", reason)
                                .set("requested_at", now)
                                .set("responded_at", null)
                                .set("response_comment", null));
                if (updated < 1) {
                    throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                            "责任交接状态已变化，请刷新后重试");
                }
                handoff.setToUserId(toUserId);
                handoff.setStatus("REQUESTED");
                handoff.setHandoffReason(reason);
                handoff.setRequestedAt(now);
                handoff.setRespondedAt(null);
                handoff.setResponseComment(null);
            }
        }

        String status = order.getStatus();
        String details = jsonPayload(payload(
                "handoffId", handoff.getHandoffId(),
                "fromUserId", fromUserId,
                "toUserId", toUserId,
                "reason", reason,
                "previousReason", previousReason,
                "replacedHandoffId", pending == null ? null : pending.getHandoffId(),
                "status", "REQUESTED"));
        recordActionHistory(workOrderId, actorId, "COMMUNITY_ADMIN", status, status,
                "HANDOFF_REQUESTED", reason, details);
        auditLogService.recordAudit(actorId, "COMMUNITY_ADMIN", order.getCommunityId(),
                "WORK_ORDER", String.valueOf(workOrderId), "WORK_ORDER_HANDOFF_REQUESTED",
                "SUCCESS", "127.0.0.1", details);
        List<Long> requestRecipients = new ArrayList<>(Arrays.asList(fromUserId, toUserId));
        requestRecipients.addAll(activeCommunityAdminIds(order.getCommunityId()));
        publishNotification("WORK_ORDER_HANDOFF_REQUESTED",
                "WORK_ORDER_HANDOFF_REQUESTED:" + handoff.getHandoffId(), workOrderId,
                "有一项工单责任交接待确认",
                "社区管理员邀请您接手工单：" + order.getOrderTitle(), details,
                distinctIds(requestRecipients));
        return handoff.getHandoffId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptHandoff(Long workOrderId, Long handoffId, WorkOrderHandoffAcceptDTO dto,
                              Long workerUserId) {
        Long actorId = requireCurrentActor(workerUserId, "GRID_WORKER", "CARE_WORKER");
        WorkerActor actor = requireWorkerActor(actorId);
        Long communityId = resolveSessionCommunityId();
        lockEligibleWorkerAssignment(actorId, communityId);

        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(order, actor.authorizationRole);

        WorkOrderHandoff handoff = handoffMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderHandoff>()
                        .eq(WorkOrderHandoff::getHandoffId, handoffId)
                        .eq(WorkOrderHandoff::getWorkOrderId, workOrderId)
                        .last("FOR UPDATE"));
        if (handoff == null || !actorId.equals(handoff.getToUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        if (!"REQUESTED".equalsIgnoreCase(handoff.getStatus())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "责任交接已被其他操作处理");
        }
        if (order.getCurrentAssigneeId() == null
                || !order.getCurrentAssigneeId().equals(handoff.getFromUserId())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "当前承接人已变化，不能接受此责任交接");
        }
        assertHandoffTransferableStatus(order);

        String responseComment = dto == null || !StringUtils.hasText(dto.getResponseComment())
                ? null : requireText(dto.getResponseComment(), 255, "交接接收说明");
        LocalDateTime now = LocalDateTime.now(clock);
        Long previousAssigneeId = order.getCurrentAssigneeId();
        String status = order.getStatus();

        order.setCurrentAssigneeId(actorId);
        updateWorkOrderOrThrow(order);

        int updated = handoffMapper.update(null,
                new UpdateWrapper<WorkOrderHandoff>()
                        .eq("handoff_id", handoffId)
                        .eq("work_order_id", workOrderId)
                        .eq("from_user_id", previousAssigneeId)
                        .eq("to_user_id", actorId)
                        .eq("status", "REQUESTED")
                        .set("status", "ACCEPTED")
                        .set("responded_at", now)
                        .set("response_comment", responseComment));
        if (updated < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "责任交接已被其他操作处理");
        }

        WorkOrderAssignmentHistory assignment = new WorkOrderAssignmentHistory();
        assignment.setWorkOrderId(workOrderId);
        assignment.setActionType("HANDOFF");
        assignment.setOperatorUserId(actorId);
        assignment.setAssignedToUserId(actorId);
        assignment.setReason(handoff.getHandoffReason());
        assignment.setAssignedAt(now);
        if (assignmentHistoryMapper.insert(assignment) < 1) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "责任交接派单历史写入失败");
        }

        String details = jsonPayload(payload(
                "handoffId", handoffId,
                "fromUserId", previousAssigneeId,
                "toUserId", actorId,
                "status", "ACCEPTED",
                "responseComment", responseComment,
                "acceptedAt", now));
        recordActionHistory(workOrderId, actorId, actor.historyRole, status, status,
                "HANDOFF_ACCEPTED", responseComment, details);
        auditLogService.recordAudit(actorId, actor.historyRole, order.getCommunityId(),
                "WORK_ORDER", String.valueOf(workOrderId), "WORK_ORDER_HANDOFF_ACCEPT",
                "SUCCESS", "127.0.0.1", details);
        List<Long> acceptanceRecipients = new ArrayList<>(List.of(previousAssigneeId));
        acceptanceRecipients.addAll(activeCommunityAdminIds(order.getCommunityId()));
        publishNotification("WORK_ORDER_HANDOFF_ACCEPTED",
                "WORK_ORDER_HANDOFF_ACCEPTED:" + handoffId, workOrderId,
                "工单责任交接已确认", "新承接人已确认接手工单：" + order.getOrderTitle(), details,
                distinctIds(acceptanceRecipients));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectHandoff(Long workOrderId, Long handoffId, WorkOrderHandoffRejectDTO dto,
                              Long workerUserId) {
        Long actorId = requireCurrentActor(workerUserId, "GRID_WORKER", "CARE_WORKER");
        WorkerActor actor = requireWorkerActor(actorId);
        Long communityId = resolveSessionCommunityId();
        lockEligibleWorkerAssignment(actorId, communityId);

        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(order, actor.authorizationRole);

        WorkOrderHandoff handoff = handoffMapper.selectOne(
                new LambdaQueryWrapper<WorkOrderHandoff>()
                        .eq(WorkOrderHandoff::getHandoffId, handoffId)
                        .eq(WorkOrderHandoff::getWorkOrderId, workOrderId)
                        .last("FOR UPDATE"));
        if (handoff == null || !actorId.equals(handoff.getToUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        if (!"REQUESTED".equalsIgnoreCase(handoff.getStatus())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "责任交接已被其他操作处理");
        }
        if (!java.util.Objects.equals(order.getCurrentAssigneeId(), handoff.getFromUserId())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "当前承接人已变化，不能拒绝此责任交接");
        }
        assertHandoffTransferableStatus(order);
        if (dto == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "交接拒绝说明不能为空");
        }
        String responseComment = requireText(dto.getResponseComment(), 255, "交接拒绝说明");
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = handoffMapper.update(null,
                new UpdateWrapper<WorkOrderHandoff>()
                        .eq("handoff_id", handoffId)
                        .eq("work_order_id", workOrderId)
                        .eq("to_user_id", actorId)
                        .eq("status", "REQUESTED")
                        .set("status", "REJECTED")
                        .set("responded_at", now)
                        .set("response_comment", responseComment));
        if (updated < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "责任交接已被其他操作处理");
        }

        String status = order.getStatus();
        String details = jsonPayload(payload(
                "handoffId", handoffId,
                "fromUserId", handoff.getFromUserId(),
                "toUserId", actorId,
                "status", "REJECTED",
                "responseComment", responseComment,
                "respondedAt", now));
        recordActionHistory(workOrderId, actorId, actor.historyRole, status, status,
                "HANDOFF_REJECTED", responseComment, details);
        auditLogService.recordAudit(actorId, actor.historyRole, order.getCommunityId(),
                "WORK_ORDER", String.valueOf(workOrderId), "WORK_ORDER_HANDOFF_REJECT",
                "SUCCESS", "127.0.0.1", details);
        List<Long> rejectionRecipients = new ArrayList<>(List.of(handoff.getFromUserId()));
        rejectionRecipients.addAll(activeCommunityAdminIds(order.getCommunityId()));
        publishNotification("WORK_ORDER_HANDOFF_REJECTED",
                "WORK_ORDER_HANDOFF_REJECTED:" + handoffId, workOrderId,
                "工单责任交接被拒绝",
                "指定工作人员拒绝了责任交接：" + order.getOrderTitle(), details,
                distinctIds(rejectionRecipients));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long requestServiceStop(Long workOrderId, ServiceStopRequestDTO dto, Long applicantUserId) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "服务中止申请内容不能为空");
        }
        Long actorId = requireSuppliedCurrentUser(applicantUserId);
        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        ReservationExecutionLink link = findReservationLink(order);
        if (link == null) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "只有预约派生工单支持服务中止审批");
        }
        ServiceReservation reservation = reservationMapper.selectById(link.getReservationId());
        if (reservation == null || !"RESERVATION".equalsIgnoreCase(order.getSourceType())
                || !order.getElderId().equals(reservation.getElderId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        authorizeStopRequester(order, reservation, actorId);
        if (!"CONFIRMED".equalsIgnoreCase(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "关联预约当前状态不支持服务中止申请");
        }
        assertStatus(order, "ACCEPTED", "PROCESSING");

        String stopReason = requireText(dto.getStopReason(), 500, "服务中止原因");
        String completedContent = requireText(dto.getCompletedContent(), 4000, "已完成内容");
        String incompleteContent = requireText(dto.getIncompleteContent(), 4000, "未完成内容");
        String followUpPlan = requireText(dto.getFollowUpPlan(), 4000, "后续安排");
        ServiceStopRequest pending = stopRequestMapper.selectOne(
                new LambdaQueryWrapper<ServiceStopRequest>()
                        .eq(ServiceStopRequest::getWorkOrderId, workOrderId)
                        .eq(ServiceStopRequest::getStatus, "PENDING_REVIEW")
                        .last("LIMIT 1 FOR UPDATE"));
        if (pending != null) {
            if (actorId.equals(pending.getApplicantUserId())
                    && stopReason.equals(pending.getStopReason())
                    && completedContent.equals(pending.getCompletedContent())
                    && incompleteContent.equals(pending.getIncompleteContent())
                    && followUpPlan.equals(pending.getFollowUpPlan())) {
                return pending.getStopId();
            }
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该工单已有待审核的服务中止申请");
        }

        ServiceStopRequest request = new ServiceStopRequest();
        request.setReservationId(reservation.getReservationId());
        request.setWorkOrderId(workOrderId);
        request.setApplicantUserId(actorId);
        request.setStopReason(stopReason);
        request.setCompletedContent(completedContent);
        request.setIncompleteContent(incompleteContent);
        request.setFollowUpPlan(followUpPlan);
        request.setStatus("PENDING_REVIEW");
        request.setCreatedAt(LocalDateTime.now(clock));
        if (stopRequestMapper.insert(request) < 1 || request.getStopId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "服务中止申请写入失败");
        }

        String details = jsonPayload(payload(
                "stopId", request.getStopId(),
                "reservationId", reservation.getReservationId(),
                "stopReason", stopReason,
                "completedContent", completedContent,
                "incompleteContent", incompleteContent,
                "followUpPlan", followUpPlan));
        String fromStatus = order.getStatus();
        recordActionHistory(workOrderId, actorId, authorizationPolicy.requireCurrentUser().getCurrentRole(),
                fromStatus, fromStatus, "SERVICE_STOP_REQUESTED", stopReason, details);
        auditLogService.recordAudit(actorId, authorizationPolicy.requireCurrentUser().getCurrentRole(),
                order.getCommunityId(), "WORK_ORDER", String.valueOf(workOrderId),
                "WORK_ORDER_SERVICE_STOP_REQUEST", "SUCCESS", "127.0.0.1", details);
        publishNotification("WORK_ORDER_SERVICE_STOP_REQUESTED",
                "WORK_ORDER_STOP_REQUESTED:" + request.getStopId(), workOrderId,
                "收到服务中止申请", "工单申请中止服务：" + stopReason, details,
                activeCommunityAdminIds(order.getCommunityId()));
        return request.getStopId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceStopRequestVO> listServiceStopRequests(Long workOrderId) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        WorkOrder order = workOrderMapper.selectById(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        return stopRequestMapper.selectList(
                        new LambdaQueryWrapper<ServiceStopRequest>()
                                .eq(ServiceStopRequest::getWorkOrderId, workOrderId)
                                .orderByDesc(ServiceStopRequest::getCreatedAt)
                                .orderByDesc(ServiceStopRequest::getStopId))
                .stream()
                .map(this::toServiceStopRequestVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewServiceStop(Long workOrderId, Long stopId,
                                  ServiceStopReviewDTO dto, Long reviewerId) {
        Long actorId = requireCurrentActor(reviewerId, "COMMUNITY_ADMIN");
        if (dto == null || !StringUtils.hasText(dto.getReviewComment())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "服务中止审核意见不能为空");
        }
        String action = StringUtils.hasText(dto.getAction()) ? dto.getAction().trim().toUpperCase() : "";
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "审核动作仅支持 APPROVE 或 REJECT");
        }

        WorkOrder initialOrder = workOrderMapper.selectById(workOrderId);
        if (initialOrder == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(initialOrder, "COMMUNITY_ADMIN");
        ReservationExecutionLink link = findReservationLink(initialOrder);
        if (link == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        ServiceReservation reservation = selectReservationForUpdate(link.getReservationId());
        if (reservation == null || !"CONFIRMED".equalsIgnoreCase(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "关联预约状态已变化，不能继续审核服务中止申请");
        }
        WorkOrder order = selectWorkOrderForUpdate(workOrderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        requireOrderInCurrentCommunity(order, "COMMUNITY_ADMIN");
        ServiceStopRequest request = stopRequestMapper.selectOne(
                new LambdaQueryWrapper<ServiceStopRequest>()
                        .eq(ServiceStopRequest::getStopId, stopId)
                        .eq(ServiceStopRequest::getWorkOrderId, workOrderId)
                        .last("FOR UPDATE"));
        if (request == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        if (!"PENDING_REVIEW".equalsIgnoreCase(request.getStatus())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "服务中止申请已被其他操作审核");
        }
        if (!"ACCEPTED".equalsIgnoreCase(order.getStatus())
                && !"PROCESSING".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "工单执行状态已变化，不能继续审核服务中止申请");
        }
        if (link == null || !link.getReservationId().equals(request.getReservationId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String reviewComment = requireText(dto.getReviewComment(), 255, "审核意见");
        String fromStatus = order.getStatus();
        boolean approved = "APPROVE".equals(action);
        UpdateWrapper<ServiceStopRequest> requestUpdate = new UpdateWrapper<ServiceStopRequest>()
                .eq("stop_id", stopId)
                .eq("work_order_id", workOrderId)
                .eq("status", "PENDING_REVIEW")
                .set("status", approved ? "APPROVED" : "REJECTED")
                .set("reviewer_user_id", actorId)
                .set("reviewed_at", now)
                .set("review_comment", reviewComment);
        if (approved) {
            requestUpdate.set("executor_user_id", actorId).set("executed_at", now);
        }
        if (stopRequestMapper.update(null, requestUpdate) < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "服务中止申请已被其他操作审核");
        }

        String toStatus = fromStatus;
        String historyAction;
        String auditAction;
        String notificationType;
        String notificationKey;
        String notificationTitle;
        String notificationContent;
        if (approved) {
            order.setStatus("SERVICE_STOPPED");
            updateWorkOrderOrThrow(order);
            reservation.setStatus("SERVICE_STOPPED");
            if (reservationMapper.updateById(reservation) < 1) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                        "关联预约已被其他操作更新，请刷新后重试");
            }
            toStatus = "SERVICE_STOPPED";
            historyAction = "SERVICE_STOPPED";
            auditAction = "WORK_ORDER_SERVICE_STOP_APPROVE";
            notificationType = "WORK_ORDER_SERVICE_STOPPED";
            notificationKey = "WORK_ORDER_STOPPED:" + stopId;
            notificationTitle = "服务已批准中止";
            notificationContent = "工单服务已中止：" + request.getStopReason();
        } else {
            historyAction = "SERVICE_STOP_REJECTED";
            auditAction = "WORK_ORDER_SERVICE_STOP_REJECT";
            notificationType = "WORK_ORDER_SERVICE_STOP_REJECTED";
            notificationKey = "WORK_ORDER_STOP_REJECTED:" + stopId;
            notificationTitle = "服务中止申请未获批准";
            notificationContent = "服务中止申请未获批准：" + reviewComment;
        }

        String details = jsonPayload(payload(
                "stopId", stopId,
                "reservationId", reservation.getReservationId(),
                "action", action,
                "stopReason", request.getStopReason(),
                "completedContent", request.getCompletedContent(),
                "incompleteContent", request.getIncompleteContent(),
                "followUpPlan", request.getFollowUpPlan(),
                "reviewComment", reviewComment,
                "reviewerUserId", actorId,
                "executorUserId", approved ? actorId : null,
                "reviewedAt", now,
                "executedAt", approved ? now : null));
        recordActionHistory(workOrderId, actorId, "COMMUNITY_ADMIN", fromStatus, toStatus,
                historyAction, reviewComment, details);
        auditLogService.recordAudit(actorId, "COMMUNITY_ADMIN", order.getCommunityId(),
                "WORK_ORDER", String.valueOf(workOrderId), auditAction, "SUCCESS",
                "127.0.0.1", details);
        publishNotification(notificationType, notificationKey, workOrderId,
                notificationTitle, notificationContent, details,
                distinctIds(java.util.Arrays.asList(request.getApplicantUserId(), order.getCurrentAssigneeId())));
    }

    private WorkOrder selectWorkOrderForUpdate(Long workOrderId) {
        return workOrderMapper.selectOne(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getWorkOrderId, workOrderId)
                .last("FOR UPDATE"));
    }

    private ServiceReservation selectReservationForUpdate(Long reservationId) {
        return reservationMapper.selectOne(new LambdaQueryWrapper<ServiceReservation>()
                .eq(ServiceReservation::getReservationId, reservationId)
                .last("FOR UPDATE"));
    }

    private Long requireSuppliedCurrentUser(Long suppliedUserId) {
        Long actorId = currentUserId(authorizationPolicy.requireCurrentUser());
        if (suppliedUserId == null || !suppliedUserId.equals(actorId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "操作身份与当前会话不一致");
        }
        return actorId;
    }

    private void authorizeStopRequester(WorkOrder order, ServiceReservation reservation, Long actorId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        String role = current.getCurrentRole();
        boolean assignedWorker = false;
        if (isCurrentRole(current, "GRID_WORKER") || isCurrentRole(current, "CARE_WORKER")) {
            WorkerActor actor = requireWorkerActor(actorId);
            if (actorId.equals(order.getCurrentAssigneeId())) {
                requireWorkerOrderAccess(order, actor);
                assignedWorker = true;
            }
        }

        boolean originalApplicant = reservation.getApplicantUserId() != null
                && reservation.getApplicantUserId().equals(actorId)
                && (isCurrentRole(current, "CHILD") || isCurrentRole(current, "ELDER"));
        if (originalApplicant) {
            ElderProfile elder = authorizationPolicy.requireElderAccess(order.getElderId());
            requireOrderCommunityMatchesElder(order, elder);
        }
        if (!assignedWorker && !originalApplicant) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
    }

    private ReservationExecutionLink findReservationLink(WorkOrder order) {
        if (order == null || !"RESERVATION".equalsIgnoreCase(order.getSourceType())) {
            return null;
        }
        return executionLinkMapper.selectOne(new LambdaQueryWrapper<ReservationExecutionLink>()
                .eq(ReservationExecutionLink::getWorkOrderId, order.getWorkOrderId())
                .last("LIMIT 1"));
    }

    private String requireText(String value, int maximumLength, String fieldName) {
        if (!StringUtils.hasText(value) || value.trim().length() > maximumLength) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    fieldName + "不能为空且不能超过" + maximumLength + "个字符");
        }
        return value.trim();
    }

    private ServiceStopRequestVO toServiceStopRequestVO(ServiceStopRequest request) {
        return new ServiceStopRequestVO(request.getStopId(), request.getReservationId(),
                request.getWorkOrderId(), request.getApplicantUserId(), request.getStopReason(),
                request.getCompletedContent(), request.getIncompleteContent(), request.getFollowUpPlan(),
                request.getStatus(), request.getReviewerUserId(), request.getReviewedAt(),
                request.getReviewComment(), request.getExecutorUserId(), request.getExecutedAt(),
                request.getCreatedAt());
    }

    private List<Long> activeCommunityAdminIds(Long communityId) {
        if (communityId == null) {
            return List.of();
        }
        List<UserRole> roles = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getCommunityId, communityId)
                .eq(UserRole::getRoleCode, "COMMUNITY_ADMIN")
                .eq(UserRole::getStatus, "ACTIVE"));
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = roles.stream().map(UserRole::getUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            return List.of();
        }
        List<UserAccount> accounts = userAccountMapper.selectBatchIds(userIds);
        if (accounts == null) {
            return List.of();
        }
        return accounts.stream()
                .filter(account -> account != null
                        && "STAFF".equalsIgnoreCase(account.getAccountKind())
                        && "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .map(UserAccount::getUserId).distinct().toList();
    }

    private List<Long> cancellationRecipients(WorkOrder order) {
        List<Long> recipients = new ArrayList<>();
        if (order.getCurrentAssigneeId() != null) {
            recipients.add(order.getCurrentAssigneeId());
        }
        ReservationExecutionLink link = findReservationLink(order);
        if (link != null) {
            ServiceReservation reservation = reservationMapper.selectById(link.getReservationId());
            if (reservation != null && reservation.getApplicantUserId() != null) {
                recipients.add(reservation.getApplicantUserId());
            }
        }
        return recipients.stream().distinct().toList();
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private void publishNotification(String eventType, String dedupKey, Long workOrderId,
                                     String title, String content, String payloadJson,
                                     List<Long> recipients) {
        NotificationCreateEventDTO event = new NotificationCreateEventDTO();
        event.setPolicyScope("N01_BUSINESS");
        event.setEventType(eventType);
        event.setSourceEntityType("WORK_ORDER");
        event.setSourceEntityId(String.valueOf(workOrderId));
        event.setDedupKey(dedupKey);
        event.setTitle(title);
        event.setContent(content);
        event.setPayloadJson(payloadJson);
        event.setRecipientUserIds(distinctIds(recipients));
        notificationService.publishEvent(event);
    }

    private Map<String, Object> payload(Object... fields) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < fields.length; i += 2) {
            payload.put(String.valueOf(fields[i]), fields[i + 1]);
        }
        return payload;
    }

    private String jsonPayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化工单业务历史", exception);
        }
    }

    private void assertStatus(WorkOrder order, String... allowedStatuses) {
        String status = order.getStatus();
        if (status == null || Arrays.stream(allowedStatuses).noneMatch(s -> s.equalsIgnoreCase(status))) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "工单当前状态 [" + status + "] 不支持此操作");
        }
    }

    private boolean isHandoffTransferableStatus(String status) {
        return status != null && HANDOFF_TRANSFERABLE_STATUSES.stream()
                .anyMatch(candidate -> candidate.equalsIgnoreCase(status));
    }

    private void assertHandoffTransferableStatus(WorkOrder order) {
        if (order == null || !isHandoffTransferableStatus(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "工单当前状态不支持责任交接");
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
        Long workerId = requireCurrentActor(suppliedUserId, "GRID_WORKER");
        requireActiveStaffRole(workerId, "GRID_WORKER");
        return WorkerActor.forGridWorker();
    }

    private void requireActiveStaffRole(Long userId, String roleCode) {
        Long communityId = resolveSessionCommunityId();
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null || !"STAFF".equalsIgnoreCase(account.getAccountKind())
                || !"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前工作人员账号已失效");
        }
        UserRole assignment = findActiveDispatchAssignment(userId, communityId, roleCode);
        if (!isActiveDispatchAssignment(assignment, userId, communityId)
                || !roleCode.equalsIgnoreCase(assignment.getRoleCode())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前工作人员社区角色已失效");
        }
    }

    private void lockEligibleWorkerAssignment(Long userId, Long communityId) {
        if (userId == null || communityId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "交接目标不是本社区有效工作人员");
        }
        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId)
                .last("FOR UPDATE"));
        if (account == null || !userId.equals(account.getUserId())
                || !"STAFF".equalsIgnoreCase(account.getAccountKind())
                || !"ACTIVE".equalsIgnoreCase(account.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "交接目标不是本社区有效工作人员");
        }
        List<UserRole> assignments = userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getCommunityId, communityId)
                .eq(UserRole::getStatus, "ACTIVE")
                .in(UserRole::getRoleCode, List.of("GRID_WORKER", "CARE_WORKER"))
                .orderByAsc(UserRole::getAssignmentId)
                .last("FOR UPDATE"));
        if (assignments == null || assignments.stream()
                .noneMatch(assignment -> isActiveDispatchAssignment(assignment, userId, communityId))) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "交接目标不是本社区有效工作人员");
        }
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
        recordActionHistory(workOrderId, operatorId, role, fromStatus, toStatus, actionName, comment, null);
    }

    private void recordActionHistory(Long workOrderId, Long operatorId, String role, String fromStatus,
                                     String toStatus, String actionName, String comment, String actionPayload) {
        WorkOrderActionHistory history = new WorkOrderActionHistory();
        history.setWorkOrderId(workOrderId);
        history.setOperatorUserId(operatorId);
        history.setOperatorRole(role);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setActionName(actionName);
        history.setActionPayload(actionPayload);
        history.setComment(comment);
        String traceId = MDC.get("traceId");
        history.setTraceId(StringUtils.hasText(traceId) ? traceId : "TR-" + UUID.randomUUID().toString().substring(0, 8));
        history.setOccurredAt(LocalDateTime.now(clock));
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
        fillEvidence(vo, order.getWorkOrderId());
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
        fillEvidence(vo, o.getWorkOrderId());
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

    private void fillEvidence(WorkOrderVO vo, Long workOrderId) {
        List<Long> versions = evidenceService.currentVersions(workOrderId);
        vo.setEvidenceAssetUuids(versions == null ? List.of() : evidenceService.assetUuids(workOrderId, versions));
        if (versions != null && !versions.isEmpty()) vo.setPhotoUrls(evidenceService.urls(workOrderId, versions));
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
        private final String authorizationRole;
        private final String historyRole;

        private WorkerActor(boolean careWorker, CareWorkerScope careWorkerScope,
                            String authorizationRole, String historyRole) {
            this.careWorker = careWorker;
            this.careWorkerScope = careWorkerScope;
            this.authorizationRole = authorizationRole;
            this.historyRole = historyRole;
        }

        private static WorkerActor forCareWorker(CareWorkerScope scope) {
            return new WorkerActor(true, scope, "CARE_WORKER", "CARE_WORKER");
        }

        private static WorkerActor forGridWorker() {
            return new WorkerActor(false, null, "GRID_WORKER", "COMMUNITY_WORKER");
        }
    }
}
