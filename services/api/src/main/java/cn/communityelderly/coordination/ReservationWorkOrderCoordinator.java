package cn.communityelderly.coordination;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.reservation.dto.ReservationVO;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationWorkOrderCoordinator {

    private final ServiceReservationMapper reservationMapper;
    private final ReservationExecutionLinkMapper executionLinkMapper;
    private final WorkOrderMapper workOrderMapper;
    private final WorkOrderActionHistoryMapper actionHistoryMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ServiceReservationService reservationService;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ReservationWorkOrderCoordinator(ServiceReservationMapper reservationMapper,
                                          ReservationExecutionLinkMapper executionLinkMapper,
                                          WorkOrderMapper workOrderMapper,
                                          WorkOrderActionHistoryMapper actionHistoryMapper,
                                          ElderProfileMapper elderProfileMapper,
                                          ServiceReservationService reservationService,
                                          ObjectAuthorizationPolicy authorizationPolicy,
                                          AuditLogService auditLogService,
                                          Clock clock) {
        this.reservationMapper = reservationMapper;
        this.executionLinkMapper = executionLinkMapper;
        this.workOrderMapper = workOrderMapper;
        this.actionHistoryMapper = actionHistoryMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.reservationService = reservationService;
        this.authorizationPolicy = authorizationPolicy;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /**
     * 协调事务：管理员确认预约 -> 原子生成工单 -> 记录唯一关联映射
     */
    @Transactional
    public Long confirmReservationAndCreateWorkOrder(Long reservationId, Long reviewerUserId) {
        requireCommunityAdministrator(reviewerUserId);
        ServiceReservation reservationSnapshot = reservationMapper.selectById(reservationId);
        if (reservationSnapshot == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }

        // Serialize confirmation against archive deactivation with lock order:
        // elder -> reservation -> generated work order.
        ElderProfile authorizedElder = authorizationPolicy.requireElderInCurrentCommunity(
                reservationSnapshot.getElderId(), "COMMUNITY_ADMIN");
        ElderProfile elder = elderProfileMapper.selectByIdForUpdate(reservationSnapshot.getElderId());
        if (elder == null || authorizedElder == null || authorizedElder.getCommunityId() == null
                || !authorizedElder.getCommunityId().equals(elder.getCommunityId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        if (!"ACTIVE".equalsIgnoreCase(elder.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "老人档案已停用，不能确认预约");
        }

        ServiceReservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null || !reservationSnapshot.getElderId().equals(reservation.getElderId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        if (!"PENDING_CONFIRMATION".equals(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "只有待确认状态的预约才可审核受理");
        }

        // 1. 推进预约状态
        reservation.setStatus("CONFIRMED");
        if (reservationMapper.updateById(reservation) < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "预约已被其他操作更新，请刷新后重试");
        }

        // 2. 查取老人信息
        Long communityId = requireElderCommunityId(elder);

        // 3. 原子生成待派发工单
        String timestampPrefix = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now(clock));
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String orderNo = "WO" + timestampPrefix + randomSuffix;

        WorkOrder order = new WorkOrder();
        order.setOrderNo(orderNo);
        order.setSourceType("RESERVATION");
        order.setSourceReferenceId(reservationId.toString());
        order.setElderId(reservation.getElderId());
        order.setCommunityId(communityId);
        order.setServiceType(reservation.getServiceType());
        order.setOrderTitle("【预约服务】" + reservation.getServiceType());
        order.setOrderDescription("上门地址: " + reservation.getServiceAddress() +
                "；预约日期: " + reservation.getServiceDate() + " (" + reservation.getSlotCode() + ")" +
                "；特殊叮嘱: " + (StringUtils.hasText(reservation.getSpecialNotes()) ? reservation.getSpecialNotes() : "无"));
        order.setUrgencyLevel("NORMAL");
        order.setStatus("PENDING_DISPATCH");
        order.setResultPhotoRequired(true);

        workOrderMapper.insert(order);

        // 4. 固化关联映射表
        ReservationExecutionLink link = new ReservationExecutionLink();
        link.setReservationId(reservationId);
        link.setWorkOrderId(order.getWorkOrderId());
        executionLinkMapper.insert(link);

        // 5. 记录建单流转审计
        WorkOrderActionHistory history = new WorkOrderActionHistory();
        history.setWorkOrderId(order.getWorkOrderId());
        history.setOperatorUserId(reviewerUserId);
        history.setOperatorRole("COMMUNITY_ADMIN");
        history.setFromStatus("NONE");
        history.setToStatus("PENDING_DISPATCH");
        history.setActionName("CREATE_FROM_RESERVATION");
        history.setComment("社区管理员已受理预约单 " + reservation.getReservationNo() + "，系统自动派生助老工单");
        String traceId = MDC.get("traceId");
        history.setTraceId(StringUtils.hasText(traceId) ? traceId : "TR-" + UUID.randomUUID().toString().substring(0, 8));
        actionHistoryMapper.insert(history);

        // 6. 记录系统安全审计日志
        auditLogService.recordAudit(
                reviewerUserId,
                "COMMUNITY_ADMIN",
                communityId,
                "SERVICE_RESERVATION",
                String.valueOf(reservationId),
                "APPROVE_RESERVATION",
                "SUCCESS",
                "127.0.0.1",
                "{\"reservationId\":" + reservationId + ",\"orderNo\":\"" + orderNo + "\",\"serviceType\":\"" + reservation.getServiceType() + "\"}"
        );

        return order.getWorkOrderId();
    }

    /**
     * 协调事务：管理员退回/拒绝预约
     */
    @Transactional
    public void rejectReservation(Long reservationId, String reason, Long reviewerUserId) {
        requireCommunityAdministrator(reviewerUserId);
        ServiceReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(
                reservation.getElderId(), "COMMUNITY_ADMIN");
        if (!"PENDING_CONFIRMATION".equals(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前预约状态不支持退回操作");
        }

        reservation.setStatus("REJECTED");
        reservation.setRejectReason(reason);
        if (reservationMapper.updateById(reservation) < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "预约已被其他操作更新，请刷新后重试");
        }

        Long communityId = requireElderCommunityId(elder);

        auditLogService.recordAudit(
                reviewerUserId,
                "COMMUNITY_ADMIN",
                communityId,
                "SERVICE_RESERVATION",
                String.valueOf(reservationId),
                "REJECT_RESERVATION",
                "SUCCESS",
                "127.0.0.1",
                "{\"reservationId\":" + reservationId + ",\"reason\":\"" + (reason != null ? reason : "") + "\"}"
        );
    }

    /**
     * 查询全辖区待确认受理的预约单
     */
    public List<ReservationVO> getPendingReservations() {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        java.util.List<Long> scopedElderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (scopedElderIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<ServiceReservation> list = reservationMapper.selectList(
                new LambdaQueryWrapper<ServiceReservation>()
                        .eq(ServiceReservation::getStatus, "PENDING_CONFIRMATION")
                        .in(ServiceReservation::getElderId, scopedElderIds)
                        .orderByDesc(ServiceReservation::getCreatedAt)
        );
        List<ReservationVO> result = new ArrayList<>();
        for (ServiceReservation r : list) {
            result.add(reservationService.getReservationDetail(r.getReservationId()));
        }
        return result;
    }

    private CurrentUser requireCommunityAdministrator(Long suppliedUserId) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        try {
            if (suppliedUserId == null || !suppliedUserId.equals(Long.parseLong(current.getUserId()))) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, "操作身份与当前会话不一致");
            }
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
        return current;
    }

    private Long requireElderCommunityId(ElderProfile elder) {
        if (elder == null || elder.getCommunityId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        return elder.getCommunityId();
    }
}
