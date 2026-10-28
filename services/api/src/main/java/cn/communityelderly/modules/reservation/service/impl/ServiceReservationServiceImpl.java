package cn.communityelderly.modules.reservation.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.dto.ReservationCancelRequestVO;
import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
import cn.communityelderly.modules.reservation.dto.ReservationCancelReviewDTO;
import cn.communityelderly.modules.reservation.dto.ReservationVO;
import cn.communityelderly.modules.reservation.entity.ReservationCancelRequest;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceCatalog;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ServiceReservationServiceImpl implements ServiceReservationService {

    private static final String PENDING_CANCEL_REASON_PREFIX = "用户主动撤回: ";
    private static final int MAX_CANCEL_REASON_LENGTH = 255 - PENDING_CANCEL_REASON_PREFIX.length();

    private final ServiceReservationMapper reservationMapper;
    private final ServiceCatalogMapper catalogMapper;
    private final ReservationExecutionLinkMapper executionLinkMapper;
    private final ReservationCancelRequestMapper cancelRequestMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ElderFamilyLinkMapper familyLinkMapper;
    private final ElderIdentityLinkMapper identityLinkMapper;
    private final WorkOrderMapper workOrderMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final WorkOrderService workOrderService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ServiceReservationServiceImpl(ServiceReservationMapper reservationMapper,
                                        ServiceCatalogMapper catalogMapper,
                                        ReservationExecutionLinkMapper executionLinkMapper,
                                        ReservationCancelRequestMapper cancelRequestMapper,
                                        ElderProfileMapper elderProfileMapper,
                                        ElderFamilyLinkMapper familyLinkMapper,
                                        ElderIdentityLinkMapper identityLinkMapper,
                                        WorkOrderMapper workOrderMapper,
                                        UserAccountMapper userAccountMapper,
                                        ObjectAuthorizationPolicy authorizationPolicy,
                                        WorkOrderService workOrderService,
                                        AuditLogService auditLogService,
                                        NotificationService notificationService,
                                        ObjectMapper objectMapper,
                                        Clock clock) {
        this.reservationMapper = reservationMapper;
        this.catalogMapper = catalogMapper;
        this.executionLinkMapper = executionLinkMapper;
        this.cancelRequestMapper = cancelRequestMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.familyLinkMapper = familyLinkMapper;
        this.identityLinkMapper = identityLinkMapper;
        this.workOrderMapper = workOrderMapper;
        this.userAccountMapper = userAccountMapper;
        this.authorizationPolicy = authorizationPolicy;
        this.workOrderService = workOrderService;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Long createReservation(ReservationCreateDTO dto, Long applicantUserId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("CHILD");
        requireActorId(applicantUserId, current);

        // Resolve the elder and active family link again inside the create transaction.
        authorizationPolicy.requireElderAccess(dto.getElderId());
        ElderProfile elder = selectElderForUpdate(dto.getElderId());
        requireActiveElderForNewBusiness(elder);
        if (dto.getServiceDate() == null || dto.getServiceDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "预约日期不能早于今天");
        }
        if (!"MORNING".equalsIgnoreCase(dto.getSlotCode()) && !"AFTERNOON".equalsIgnoreCase(dto.getSlotCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "预约时段仅支持 MORNING 或 AFTERNOON");
        }

        // 2. 校验服务目录
        ServiceCatalog catalog = catalogMapper.selectOne(
                new LambdaQueryWrapper<ServiceCatalog>()
                        .eq(ServiceCatalog::getServiceType, dto.getServiceType())
                        .eq(ServiceCatalog::getStatus, "ACTIVE")
                        .last("LIMIT 1")
        );
        if (catalog == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "选定的助老照护项目不存在或暂未开放预约");
        }

        // 3. 时段防重校验 (同一老人、同服务、同日期、同时段不能重复预约)
        List<Long> activeReservations = reservationMapper.selectActiveSlotReservationIdsForUpdate(
                dto.getElderId(), dto.getServiceType(), dto.getServiceDate(), dto.getSlotCode());
        if (activeReservations != null && !activeReservations.isEmpty()) {
            throw new BusinessException(ErrorCode.RESERVATION_SLOT_CONFLICT, "长辈在选定时段已存在同类预约，请勿重复预约");
        }

        // 4. 组装实体
        String timestampPrefix = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now(clock));
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String reservationNo = "RSV" + timestampPrefix + randomSuffix;

        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationNo(reservationNo);
        reservation.setElderId(dto.getElderId());
        reservation.setApplicantUserId(applicantUserId);
        reservation.setServiceType(dto.getServiceType());
        reservation.setServiceDate(dto.getServiceDate());
        reservation.setSlotCode(dto.getSlotCode());
        reservation.setServiceAddress(dto.getServiceAddress());
        reservation.setSpecialNotes(dto.getSpecialNotes());
        reservation.setStatus("PENDING_CONFIRMATION");
        reservation.setRowVersion(1L);

        reservationMapper.insert(reservation);
        return reservation.getReservationId();
    }

    @Override
    public List<ReservationVO> listMyReservations(Long applicantUserId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("CHILD", "ELDER");
        long selfId = currentUserId(current);
        if (applicantUserId != null && applicantUserId != selfId) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "只能查看本人的预约");
        }
        List<Long> elderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (elderIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ServiceReservation> query = new LambdaQueryWrapper<ServiceReservation>()
                .in(ServiceReservation::getElderId, elderIds)
                .orderByDesc(ServiceReservation::getCreatedAt);
        if ("CHILD".equalsIgnoreCase(current.getCurrentRole())) {
            query.eq(ServiceReservation::getApplicantUserId, selfId);
        }
        List<ServiceReservation> list = reservationMapper.selectList(query);
        return convertToVOList(list);
    }

    @Override
    public List<ReservationVO> listElderReservations(Long elderId) {
        authorizationPolicy.requireElderAccess(elderId);
        List<ServiceReservation> list = reservationMapper.selectList(
                new LambdaQueryWrapper<ServiceReservation>()
                        .eq(ServiceReservation::getElderId, elderId)
                        .orderByDesc(ServiceReservation::getCreatedAt)
        );
        return convertToVOList(list);
    }

    @Override
    public ReservationVO getReservationDetail(Long reservationId) {
        ServiceReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        authorizationPolicy.requireElderAccess(reservation.getElderId());
        return convertToVO(reservation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReservation(Long reservationId, String reason, Long userId) {
        if (!StringUtils.hasText(reason) || reason.trim().length() > MAX_CANCEL_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "取消原因不能为空且不能超过" + MAX_CANCEL_REASON_LENGTH + "个字符");
        }
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("CHILD");
        requireActorId(userId, current);
        ServiceReservation reservation = reservationMapper.selectByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        ElderProfile elder = authorizationPolicy.requireElderAccess(reservation.getElderId());
        if (!userId.equals(reservation.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }

        if ("PENDING_CONFIRMATION".equals(reservation.getStatus())) {
            // 接单/确认前直接撤回
            reservation.setStatus("CANCELLED");
            reservation.setRejectReason(PENDING_CANCEL_REASON_PREFIX + reason.trim());
            if (reservationMapper.updateById(reservation) < 1) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                        "预约已被其他操作更新，请刷新后重试");
            }
            auditLogService.recordAudit(userId, "CHILD", elder.getCommunityId(),
                    "SERVICE_RESERVATION", String.valueOf(reservationId), "CANCEL_PENDING_RESERVATION",
                    "SUCCESS", "127.0.0.1", toJson(Map.of(
                            "reservationId", reservationId,
                            "reason", reason.trim())));
        } else if ("CONFIRMED".equals(reservation.getStatus())) {
            ReservationExecutionLink link = executionLinkMapper.selectOne(
                    new LambdaQueryWrapper<ReservationExecutionLink>()
                            .eq(ReservationExecutionLink::getReservationId, reservationId));
            if (link == null) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                        "已确认预约缺少关联工单，暂不能申请取消");
            }
            WorkOrder order = workOrderMapper.selectByIdForUpdate(link.getWorkOrderId());
            if (order == null || !isPreAcceptanceStatus(order.getStatus())) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                        "关联工单已接单或不处于可取消状态，请按服务中止流程处理");
            }

            ReservationCancelRequest pendingRequest = cancelRequestMapper.selectOne(
                    new LambdaQueryWrapper<ReservationCancelRequest>()
                            .eq(ReservationCancelRequest::getReservationId, reservationId)
                            .eq(ReservationCancelRequest::getStatus, "PENDING_REVIEW"));
            if (pendingRequest != null) {
                throw new BusinessException(ErrorCode.DUPLICATE_OPERATION,
                        "该预约已有待审批的取消申请");
            }

            // 已确认预约转为取消审批流，预约与工单均保持有效直至管理员决定。
            ReservationCancelRequest request = new ReservationCancelRequest();
            request.setReservationId(reservationId);
            request.setApplicantUserId(userId);
            request.setReason(reason.trim());
            request.setStatus("PENDING_REVIEW");
            cancelRequestMapper.insert(request);
            auditLogService.recordAudit(userId, "CHILD", elder.getCommunityId(),
                    "SERVICE_RESERVATION", String.valueOf(reservationId), "REQUEST_RESERVATION_CANCELLATION",
                    "SUCCESS", "127.0.0.1", toJson(Map.of(
                            "reservationId", reservationId,
                            "cancelId", request.getCancelId(),
                            "reason", reason.trim())));
        } else {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前预约状态不支持撤销操作");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationCancelRequestVO> listPendingCancellationRequests() {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        List<Long> elderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (elderIds.isEmpty()) {
            return List.of();
        }

        List<ServiceReservation> reservations = reservationMapper.selectList(
                new LambdaQueryWrapper<ServiceReservation>()
                        .in(ServiceReservation::getElderId, elderIds));
        if (reservations.isEmpty()) {
            return List.of();
        }

        Map<Long, ServiceReservation> reservationsById = new LinkedHashMap<>();
        for (ServiceReservation reservation : reservations) {
            reservationsById.put(reservation.getReservationId(), reservation);
        }
        List<ReservationCancelRequest> requests = cancelRequestMapper.selectList(
                new LambdaQueryWrapper<ReservationCancelRequest>()
                        .eq(ReservationCancelRequest::getStatus, "PENDING_REVIEW")
                        .in(ReservationCancelRequest::getReservationId, reservationsById.keySet())
                        .orderByDesc(ReservationCancelRequest::getCreatedAt));

        List<ReservationCancelRequestVO> result = new ArrayList<>();
        for (ReservationCancelRequest request : requests) {
            ServiceReservation reservation = reservationsById.get(request.getReservationId());
            if (reservation != null) {
                result.add(toCancelRequestVO(request, reservation));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCancellationRequest(Long cancelId, ReservationCancelReviewDTO dto, Long reviewerUserId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        requireActorId(reviewerUserId, current);
        if (cancelId == null || dto == null || !StringUtils.hasText(dto.getDecision())
                || !StringUtils.hasText(dto.getReviewReason()) || dto.getReviewReason().trim().length() > 255) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "取消审批决定和意见不能为空且意见不能超过255个字符");
        }

        String decision = dto.getDecision().trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "审批决定仅支持 APPROVE 或 REJECT");
        }
        String reviewReason = dto.getReviewReason().trim();

        ReservationCancelRequest request = cancelRequestMapper.selectByIdForUpdate(cancelId);
        if (request == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        if (!"PENDING_REVIEW".equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "取消申请已被其他管理员处理，请刷新后重试");
        }

        ServiceReservation reservation = reservationMapper.selectByIdForUpdate(request.getReservationId());
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(
                reservation.getElderId(), "COMMUNITY_ADMIN");
        if (!request.getApplicantUserId().equals(reservation.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        if (!"CONFIRMED".equals(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "预约状态已变化，不能继续审批取消申请");
        }

        LocalDateTime reviewedAt = LocalDateTime.now(clock);
        String requestStatus = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
        if (cancelRequestMapper.reviewPendingRequest(cancelId, requestStatus, reviewerUserId,
                reviewedAt, reviewReason) < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "取消申请已被其他管理员处理，请刷新后重试");
        }

        if ("APPROVE".equals(decision)) {
            ReservationExecutionLink link = executionLinkMapper.selectOne(
                    new LambdaQueryWrapper<ReservationExecutionLink>()
                            .eq(ReservationExecutionLink::getReservationId, reservation.getReservationId()));
            if (link == null) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                        "预约关联工单不存在，不能批准取消");
            }

            reservation.setStatus("CANCELLED");
            if (reservationMapper.updateById(reservation) < 1) {
                throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                        "预约已被其他操作更新，请刷新后重试");
            }

            String terminationReason = buildWorkOrderTerminationReason(request.getReason(), reviewReason);
            workOrderService.terminateBeforeAcceptance(link.getWorkOrderId(), reviewerUserId, terminationReason);
        } else {
            NotificationCreateEventDTO notification = new NotificationCreateEventDTO();
            notification.setPolicyScope("N01_BUSINESS");
            notification.setEventType("RESERVATION_CANCEL_REJECTED");
            notification.setSourceEntityType("SERVICE_RESERVATION");
            notification.setSourceEntityId(String.valueOf(reservation.getReservationId()));
            notification.setDedupKey("RESERVATION_CANCEL_REJECTED:" + cancelId);
            notification.setTitle("预约取消申请审核结果");
            notification.setContent("您为预约 " + reservation.getReservationNo()
                    + " 提交的取消申请未获批准，管理员意见：" + reviewReason);
            notification.setPayloadJson(toJson(Map.of(
                    "cancelId", cancelId,
                    "reservationId", reservation.getReservationId(),
                    "status", "REJECTED")));
            notification.setRecipientUserIds(List.of(request.getApplicantUserId()));
            notificationService.publishEvent(notification);
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("cancelId", cancelId);
        auditDetails.put("reservationId", reservation.getReservationId());
        auditDetails.put("decision", decision);
        auditDetails.put("requestReason", request.getReason());
        auditDetails.put("reviewReason", reviewReason);
        auditLogService.recordAudit(reviewerUserId, "COMMUNITY_ADMIN", elder.getCommunityId(),
                "SERVICE_RESERVATION", String.valueOf(reservation.getReservationId()),
                "REVIEW_RESERVATION_CANCELLATION", "SUCCESS", "127.0.0.1", toJson(auditDetails));
    }

    private ReservationCancelRequestVO toCancelRequestVO(ReservationCancelRequest request,
                                                          ServiceReservation reservation) {
        ReservationCancelRequestVO vo = new ReservationCancelRequestVO();
        vo.setCancelId(request.getCancelId());
        vo.setReservationId(reservation.getReservationId());
        vo.setReservationNo(reservation.getReservationNo());
        vo.setElderId(reservation.getElderId());
        vo.setApplicantUserId(request.getApplicantUserId());
        vo.setReason(request.getReason());
        vo.setStatus(request.getStatus());
        vo.setCreatedAt(request.getCreatedAt());
        vo.setServiceType(reservation.getServiceType());
        vo.setServiceDate(reservation.getServiceDate());
        vo.setSlotCode(reservation.getSlotCode());

        ElderProfile elder = elderProfileMapper.selectById(reservation.getElderId());
        if (elder != null) {
            vo.setElderName(elder.getName());
        }
        ReservationExecutionLink link = executionLinkMapper.selectOne(
                new LambdaQueryWrapper<ReservationExecutionLink>()
                        .eq(ReservationExecutionLink::getReservationId, reservation.getReservationId()));
        if (link != null) {
            vo.setWorkOrderId(link.getWorkOrderId());
            WorkOrder order = workOrderMapper.selectById(link.getWorkOrderId());
            if (order != null) {
                vo.setWorkOrderStatus(order.getStatus());
            }
        }
        return vo;
    }

    private boolean isPreAcceptanceStatus(String status) {
        return "PENDING_DISPATCH".equals(status) || "DISPATCHED".equals(status);
    }

    private String buildWorkOrderTerminationReason(String requestReason, String reviewReason) {
        String prefix = "预约取消获批；申请原因：";
        String separator = "；管理员意见：";
        int remainingRequestCodePoints = 500
                - prefix.codePointCount(0, prefix.length())
                - separator.codePointCount(0, separator.length())
                - reviewReason.codePointCount(0, reviewReason.length());
        String limitedRequestReason = truncateCodePoints(requestReason, Math.max(0, remainingRequestCodePoints));
        return prefix + limitedRequestReason + separator + reviewReason;
    }

    private String truncateCodePoints(String value, int maxCodePoints) {
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "取消审批审计数据序列化失败");
        }
    }

    private void requireActorId(Long suppliedUserId, CurrentUser current) {
        if (suppliedUserId == null || !suppliedUserId.equals(currentUserId(current))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "操作身份与当前会话不一致");
        }
    }

    private ElderProfile selectElderForUpdate(Long elderId) {
        ElderProfile elder = elderProfileMapper.selectByIdForUpdate(elderId);
        if (elder == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        return elder;
    }

    private void requireActiveElderForNewBusiness(ElderProfile elder) {
        if (!"ACTIVE".equalsIgnoreCase(elder.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "老人档案已停用，不能提交新预约");
        }
    }

    private long currentUserId(CurrentUser current) {
        try {
            return Long.parseLong(current.getUserId());
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
    }

    private List<ReservationVO> convertToVOList(List<ServiceReservation> list) {
        List<ReservationVO> result = new ArrayList<>();
        for (ServiceReservation r : list) {
            result.add(convertToVO(r));
        }
        return result;
    }

    private ReservationVO convertToVO(ServiceReservation r) {
        ReservationVO vo = new ReservationVO();
        vo.setReservationId(r.getReservationId());
        vo.setReservationNo(r.getReservationNo());
        vo.setElderId(r.getElderId());
        vo.setApplicantUserId(r.getApplicantUserId());
        vo.setServiceType(r.getServiceType());
        vo.setServiceDate(r.getServiceDate());
        vo.setSlotCode(r.getSlotCode());
        vo.setSlotName("MORNING".equals(r.getSlotCode()) ? "上午 (09:00-11:30)" : "下午 (14:00-17:00)");
        vo.setServiceAddress(r.getServiceAddress());
        vo.setSpecialNotes(r.getSpecialNotes());
        vo.setStatus(r.getStatus());
        vo.setStatusName(formatReservationStatus(r.getStatus()));
        vo.setRejectReason(r.getRejectReason());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setUpdatedAt(r.getUpdatedAt());

        // 补充老人信息
        ElderProfile elder = elderProfileMapper.selectById(r.getElderId());
        if (elder != null) {
            vo.setElderName(elder.getName());
            vo.setElderPhone(elder.getContactPhone());
        }

        // 补充服务大类名称
        ServiceCatalog catalog = catalogMapper.selectOne(
                new LambdaQueryWrapper<ServiceCatalog>().eq(ServiceCatalog::getServiceType, r.getServiceType())
        );
        if (catalog != null) {
            vo.setServiceTypeName(catalog.getServiceName());
        }

        // 补充关联工单与执行人
        ReservationExecutionLink link = executionLinkMapper.selectOne(
                new LambdaQueryWrapper<ReservationExecutionLink>().eq(ReservationExecutionLink::getReservationId, r.getReservationId())
        );
        if (link != null) {
            WorkOrder order = workOrderMapper.selectById(link.getWorkOrderId());
            if (order != null) {
                vo.setAssociatedWorkOrderId(order.getWorkOrderId());
                vo.setAssociatedWorkOrderNo(order.getOrderNo());
                vo.setAssociatedWorkOrderStatus(order.getStatus());

                if (order.getCurrentAssigneeId() != null) {
                    UserAccount worker = userAccountMapper.selectById(order.getCurrentAssigneeId());
                    if (worker != null) {
                        vo.setAssigneeName("网格责任员 (" + worker.getUserId() + ")");
                        vo.setAssigneePhone(worker.getPhoneNormalized());
                    }
                }
            }
        }

        return vo;
    }

    private String formatReservationStatus(String status) {
        if (status == null) return "未知状态";
        switch (status) {
            case "PENDING_CONFIRMATION": return "待社区确认";
            case "CONFIRMED": return "已受理排单";
            case "REJECTED": return "已退回";
            case "CANCELLED": return "已撤回";
            case "COMPLETED": return "已完成履约";
            case "SERVICE_STOPPED": return "已中止";
            default: return status;
        }
    }
}
