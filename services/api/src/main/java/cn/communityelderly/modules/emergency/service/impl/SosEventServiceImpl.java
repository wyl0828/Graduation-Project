package cn.communityelderly.modules.emergency.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.dto.*;
import cn.communityelderly.modules.emergency.entity.SosEvent;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.entity.WorkOrderAssignmentHistory;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.emergency.service.SosEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SosEventServiceImpl implements SosEventService {

    private final SosEventMapper sosEventMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderAssignmentHistoryMapper assignmentHistoryMapper;
    private final WorkOrderActionHistoryMapper actionHistoryMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final UserAccountMapper userAccountMapper;
    private final UserRoleMapper userRoleMapper;

    public SosEventServiceImpl(SosEventMapper sosEventMapper,
                               WorkOrderMapper workOrderMapper,
                               WorkOrderAssignmentHistoryMapper assignmentHistoryMapper,
                               WorkOrderActionHistoryMapper actionHistoryMapper,
                               ElderProfileMapper elderProfileMapper,
                               ObjectAuthorizationPolicy authorizationPolicy,
                               UserAccountMapper userAccountMapper,
                               UserRoleMapper userRoleMapper) {
        this.sosEventMapper = sosEventMapper;
        this.workOrderMapper = workOrderMapper;
        this.assignmentHistoryMapper = assignmentHistoryMapper;
        this.actionHistoryMapper = actionHistoryMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.authorizationPolicy = authorizationPolicy;
        this.userAccountMapper = userAccountMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SosEventVO triggerSos(SosTriggerDTO dto) {
        authorizationPolicy.requireRole("ELDER");
        // 防误触物理校验
        if (dto.getPressDurationMs() == null || dto.getPressDurationMs() < 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "长按触控时长不足2000毫秒（2秒），防误触保护已拦截，不生成求助单");
        }

        Long selfElderId = authorizationPolicy.requireElderIdentityId();
        if (dto.getElderId() != null && !dto.getElderId().equals(selfElderId)) {
            throw inaccessible();
        }
        // 求助对象由服务端会话决定；请求体 ID 只用于拒绝跨老人伪造，不授予访问权。
        ElderProfile elder = authorizationPolicy.requireElderAccess(selfElderId);
        if (elder.getCommunityId() == null) throw inaccessible();

        CurrentUser user = authorizationPolicy.requireCurrentUser();
        Long userId = Long.parseLong(user.getUserId());

        // 幂等防重检查 (BR-07)
        String idempotencyKey = StringUtils.hasText(dto.getIdempotencyKey()) ? dto.getIdempotencyKey().trim() : UUID.randomUUID().toString();
        SosEvent existing = sosEventMapper.selectOne(
                new LambdaQueryWrapper<SosEvent>()
                        .eq(SosEvent::getIdempotencyKey, idempotencyKey)
        );
        if (existing != null) {
            return convertToVO(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        String timestampPrefix = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String orderNo = "WO" + timestampPrefix + randomSuffix;

        // 1. 原子生成紧急救援工单 (URGENT / EMERGENCY_RESCUE)
        Long defaultAssigneeId = findCommunityGridWorker(elder.getCommunityId());
        boolean autoAssigned = defaultAssigneeId != null;
        WorkOrder workOrder = new WorkOrder();
        workOrder.setOrderNo(orderNo);
        workOrder.setSourceType("SOS");
        workOrder.setSourceReferenceId("PENDING"); // 稍后回填 sos_id
        workOrder.setElderId(elder.getElderId());
        workOrder.setCommunityId(elder.getCommunityId());
        workOrder.setServiceType("EMERGENCY_RESCUE");
        workOrder.setOrderTitle("长按SOS紧急呼救：现场快速救援");
        workOrder.setOrderDescription("老人触发长按2秒紧急求助，请网格员立即电话联络并前往现场！地址：" + elder.getAddress());
        workOrder.setUrgencyLevel("URGENT");
        workOrder.setStatus(autoAssigned ? "ACCEPTED" : "PENDING_DISPATCH");
        workOrder.setCurrentAssigneeId(defaultAssigneeId);
        if (autoAssigned) {
            workOrder.setDispatchTime(now);
            workOrder.setAcceptTime(now);
        }
        workOrder.setResultPhotoRequired(true);
        workOrder.setRowVersion(1L);
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);
        workOrderMapper.insert(workOrder);

        // 2. 插入 sos_event 事实表，绑定 initial_work_order_id
        SosEvent sosEvent = new SosEvent();
        sosEvent.setElderId(elder.getElderId());
        sosEvent.setUserId(userId);
        sosEvent.setStatus("ACCEPTED");
        sosEvent.setPressDurationMs(dto.getPressDurationMs());
        sosEvent.setLocationAddress(StringUtils.hasText(dto.getLocationAddress()) ? dto.getLocationAddress() : elder.getAddress());
        sosEvent.setLongitude(dto.getLongitude());
        sosEvent.setLatitude(dto.getLatitude());
        sosEvent.setInitialWorkOrderId(workOrder.getWorkOrderId());
        sosEvent.setIdempotencyKey(idempotencyKey);
        sosEvent.setOccurredAt(now);
        sosEventMapper.insert(sosEvent);

        // 回填 source_reference_id
        workOrder.setSourceReferenceId(String.valueOf(sosEvent.getSosId()));
        workOrderMapper.updateById(workOrder);

        // 3. 记录派单与流转操作审计历史
        if (autoAssigned) {
            WorkOrderAssignmentHistory assignHistory = new WorkOrderAssignmentHistory();
            assignHistory.setWorkOrderId(workOrder.getWorkOrderId());
            assignHistory.setActionType("DISPATCH");
            assignHistory.setOperatorUserId(0L); // 系统自动分派
            assignHistory.setAssignedToUserId(defaultAssigneeId);
            assignHistory.setReason("SOS 紧急呼救自动匹配本社区网格员");
            assignHistory.setAssignedAt(now);
            assignmentHistoryMapper.insert(assignHistory);
        }

        WorkOrderActionHistory actionHistory = new WorkOrderActionHistory();
        actionHistory.setWorkOrderId(workOrder.getWorkOrderId());
        actionHistory.setOperatorUserId(userId);
        actionHistory.setOperatorRole("ELDER");
        actionHistory.setFromStatus("PENDING_DISPATCH");
        actionHistory.setToStatus(workOrder.getStatus());
        actionHistory.setActionName("TRIGGER_SOS");
        actionHistory.setComment("老人长按2秒触发紧急求助，系统原子派生工单");
        actionHistory.setTraceId(UUID.randomUUID().toString());
        actionHistory.setOccurredAt(now);
        actionHistoryMapper.insert(actionHistory);

        return convertToVO(sosEvent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelSosByElder(Long sosId, SosCancelDTO dto) {
        authorizationPolicy.requireRole("ELDER");
        SosEvent sos = sosEventMapper.selectById(sosId);
        if (sos == null) {
            throw inaccessible();
        }

        CurrentUser user = authorizationPolicy.requireCurrentUser();
        ElderProfile elder = authorizationPolicy.requireElderAccess(sos.getElderId());
        if (!user.getUserId().equals(String.valueOf(sos.getUserId()))) throw inaccessible();
        WorkOrder workOrder = sos.getInitialWorkOrderId() == null ? null : workOrderMapper.selectById(sos.getInitialWorkOrderId());
        requireSosWorkOrderOwnership(sos, workOrder, elder);

        if ("CANCELLED".equalsIgnoreCase(sos.getStatus())) {
            return; // 幂等支持
        }

        if ("CLOSED".equalsIgnoreCase(sos.getStatus()) || "VOIDED".equalsIgnoreCase(sos.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该求助单已结案，无法取消");
        }

        LocalDateTime now = LocalDateTime.now();
        sos.setStatus("CANCELLED");
        sosEventMapper.updateById(sos);

        // 工单记录误触取消，转入“需电话回访核实老人安全”
        if (workOrder != null) {
            String note = "\n【老人二次确认误触取消】原因：" + (dto != null ? dto.getCancelReason() : "老人确认误触按错") + "。调度中心标记：待电话回访确认安全。";
            workOrder.setOrderDescription(workOrder.getOrderDescription() + note);
            workOrder.setUpdatedAt(now);
            workOrderMapper.updateById(workOrder);

            WorkOrderActionHistory action = new WorkOrderActionHistory();
            action.setWorkOrderId(workOrder.getWorkOrderId());
            action.setOperatorUserId(sos.getUserId());
            action.setOperatorRole("ELDER");
            action.setFromStatus(workOrder.getStatus());
            action.setToStatus(workOrder.getStatus());
            action.setActionName("ELDER_CANCEL");
            action.setComment("老人点击误触取消二次确认，工单转待电话回访");
            action.setTraceId(UUID.randomUUID().toString());
            action.setOccurredAt(now);
            actionHistoryMapper.insert(action);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dispatchSos(Long sosId, SosDispatchDTO dto) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        SosEvent sos = sosEventMapper.selectById(sosId);
        if (sos == null) {
            throw inaccessible();
        }
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(sos.getElderId(), "COMMUNITY_ADMIN");

        WorkOrder workOrder = workOrderMapper.selectById(sos.getInitialWorkOrderId());
        if (workOrder == null) {
            throw inaccessible();
        }
        requireSosWorkOrderOwnership(sos, workOrder, elder);
        if (!isActiveCommunityGridWorker(dto.getAssigneeId(), elder.getCommunityId())) throw inaccessible();

        Long adminId = Long.parseLong(authorizationPolicy.requireCurrentUser().getUserId());
        LocalDateTime now = LocalDateTime.now();

        workOrder.setCurrentAssigneeId(dto.getAssigneeId());
        workOrder.setStatus("PROCESSING");
        workOrder.setUpdatedAt(now);
        workOrderMapper.updateById(workOrder);

        WorkOrderAssignmentHistory assign = new WorkOrderAssignmentHistory();
        assign.setWorkOrderId(workOrder.getWorkOrderId());
        assign.setActionType("REASSIGN");
        assign.setOperatorUserId(adminId);
        assign.setAssignedToUserId(dto.getAssigneeId());
        assign.setReason(dto.getDispatchReason());
        assign.setAssignedAt(now);
        assignmentHistoryMapper.insert(assign);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeSos(Long sosId, SosCloseDTO dto) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        SosEvent sos = sosEventMapper.selectById(sosId);
        if (sos == null) {
            throw inaccessible();
        }
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(sos.getElderId(), "COMMUNITY_ADMIN");

        WorkOrder workOrder = workOrderMapper.selectById(sos.getInitialWorkOrderId());
        if (workOrder == null) {
            throw inaccessible();
        }
        requireSosWorkOrderOwnership(sos, workOrder, elder);

        boolean falseAlarm = Boolean.TRUE.equals(dto.getIsFalseAlarm());
        if (!"ACCEPTED".equalsIgnoreCase(sos.getStatus())
                && !"CANCELLED".equalsIgnoreCase(sos.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "该求助单已结案，无法重复结案");
        }

        String fromStatus = workOrder.getStatus();
        if (falseAlarm) {
            if (!isBeforeProcessing(fromStatus)) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "工单已进入处理中，不能按误报终止");
            }
        } else if (!"PROCESSING".equalsIgnoreCase(fromStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "工单当前状态不支持正常结案");
        }

        Long adminId = Long.parseLong(authorizationPolicy.requireCurrentUser().getUserId());
        LocalDateTime now = LocalDateTime.now();

        String toStatus;
        String actionName;
        String actionComment;
        if (falseAlarm) {
            toStatus = "VOIDED";
            actionName = "VOID";
            actionComment = "误触复核终止：" + dto.getResultSummary();
            workOrder.setStatus(toStatus);
            sos.setStatus("VOIDED");
        } else {
            toStatus = "CLOSED";
            actionName = "CLOSE";
            actionComment = "结案审核通过：" + dto.getResultSummary();
            workOrder.setStatus(toStatus);
            workOrder.setServiceFinishTime(now);
            workOrder.setServiceResultSummary(dto.getResultSummary());
            workOrder.setReviewerUserId(adminId);
            workOrder.setReviewedAt(now);
            workOrder.setReviewComment(dto.getResultSummary());
            sos.setStatus("CLOSED");
        }
        workOrder.setUpdatedAt(now);
        workOrderMapper.updateById(workOrder);
        sosEventMapper.updateById(sos);

        WorkOrderActionHistory action = new WorkOrderActionHistory();
        action.setWorkOrderId(workOrder.getWorkOrderId());
        action.setOperatorUserId(adminId);
        action.setOperatorRole("COMMUNITY_ADMIN");
        action.setFromStatus(fromStatus);
        action.setToStatus(toStatus);
        action.setActionName(actionName);
        action.setComment(actionComment);
        action.setTraceId(UUID.randomUUID().toString());
        action.setOccurredAt(now);
        actionHistoryMapper.insert(action);
    }

    @Override
    public SosEventVO getActiveSos(Long elderId) {
        authorizationPolicy.requireRole("ELDER", "CHILD", "COMMUNITY_ADMIN", "GRID_WORKER");
        Long resolvedElderId = elderId;
        if (resolvedElderId == null) {
            authorizationPolicy.requireRole("ELDER");
            resolvedElderId = authorizationPolicy.requireElderIdentityId();
        }
        authorizationPolicy.requireElderAccess(resolvedElderId);
        SosEvent sos = sosEventMapper.selectOne(
                new LambdaQueryWrapper<SosEvent>()
                        .eq(SosEvent::getElderId, resolvedElderId)
                        .in(SosEvent::getStatus, "ACCEPTED", "CANCELLED")
                        .orderByDesc(SosEvent::getOccurredAt)
                        .last("LIMIT 1")
        );

        if (sos == null) return null;
        return convertToVO(sos);
    }

    @Override
    public List<SosEventVO> listSosEvents(String status) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN", "GRID_WORKER");
        List<Long> accessibleElderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (accessibleElderIds.isEmpty()) return List.of();
        LambdaQueryWrapper<SosEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SosEvent::getElderId, accessibleElderIds);
        if (StringUtils.hasText(status)) {
            wrapper.eq(SosEvent::getStatus, status.trim().toUpperCase());
        }
        wrapper.orderByDesc(SosEvent::getOccurredAt);

        List<SosEvent> list = sosEventMapper.selectList(wrapper);
        return list.stream().map(this::convertToVO).toList();
    }

    private void requireSosWorkOrderOwnership(SosEvent sos, WorkOrder workOrder, ElderProfile elder) {
        if (workOrder == null || !sos.getElderId().equals(workOrder.getElderId())
                || elder.getCommunityId() == null || !elder.getCommunityId().equals(workOrder.getCommunityId())) {
            throw inaccessible();
        }
    }

    private Long findCommunityGridWorker(Long communityId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getRoleCode, "GRID_WORKER")
                        .eq(UserRole::getCommunityId, communityId)
                        .eq(UserRole::getStatus, "ACTIVE")
                        .orderByAsc(UserRole::getAssignmentId))
                .stream()
                .map(UserRole::getUserId)
                .filter(userId -> userId != null)
                .findFirst()
                .orElse(null);
    }

    private boolean isActiveCommunityGridWorker(Long userId, Long communityId) {
        if (userId == null || communityId == null) return false;
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleCode, "GRID_WORKER")
                .eq(UserRole::getCommunityId, communityId)
                .eq(UserRole::getStatus, "ACTIVE"));
        return count != null && count > 0;
    }

    private boolean isBeforeProcessing(String status) {
        return "PENDING_DISPATCH".equalsIgnoreCase(status)
                || "PENDING_ACCEPT".equalsIgnoreCase(status)
                || "ACCEPTED".equalsIgnoreCase(status);
    }

    private BusinessException inaccessible() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
    }

    private SosEventVO convertToVO(SosEvent sos) {
        SosEventVO vo = new SosEventVO();
        vo.setSosId(sos.getSosId());
        vo.setElderId(sos.getElderId());
        vo.setStatus(sos.getStatus());
        vo.setPressDurationMs(sos.getPressDurationMs());
        vo.setOccurredAt(sos.getOccurredAt());

        ElderProfile elder = elderProfileMapper.selectById(sos.getElderId());
        if (elder != null) {
            vo.setElderName(elder.getName());
            vo.setElderAddress(StringUtils.hasText(sos.getLocationAddress()) ? sos.getLocationAddress() : elder.getAddress());
            if (elder.getBirthDate() != null) {
                vo.setElderAge(Period.between(elder.getBirthDate(), LocalDate.now()).getYears());
            } else {
                vo.setElderAge(78);
            }
            vo.setContactName(elder.getEmergencyContactName());
            vo.setContactPhone(elder.getEmergencyContactPhone());
        }

        WorkOrder workOrder = workOrderMapper.selectById(sos.getInitialWorkOrderId());
        if (workOrder != null) {
            vo.setWorkOrderId(workOrder.getWorkOrderId());
            vo.setWorkOrderNo(workOrder.getOrderNo());
            vo.setWorkOrderStatus(workOrder.getStatus());
            vo.setAssigneeId(workOrder.getCurrentAssigneeId());

            if (workOrder.getCurrentAssigneeId() != null) {
                UserAccount staff = userAccountMapper.selectById(workOrder.getCurrentAssigneeId());
                vo.setAssigneeName("李明（网格员）");
                vo.setAssigneePhone(staff != null ? staff.getPhoneNormalized() : "13800000002");
            }
        }

        if ("CANCELLED".equalsIgnoreCase(sos.getStatus())) {
            vo.setStatusDesc("老人误触取消 · 待电话回访确认");
            vo.setCancelReason("老人确认误触按错");
        } else if ("ACCEPTED".equalsIgnoreCase(sos.getStatus())) {
            vo.setStatusDesc("紧急救援中 · 网格员已接单响应");
        } else {
            vo.setStatusDesc("已结案");
        }

        return vo;
    }
}
