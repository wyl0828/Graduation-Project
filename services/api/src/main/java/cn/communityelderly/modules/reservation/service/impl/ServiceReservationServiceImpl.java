package cn.communityelderly.modules.reservation.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
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
import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceReservationServiceImpl implements ServiceReservationService {

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

    public ServiceReservationServiceImpl(ServiceReservationMapper reservationMapper,
                                        ServiceCatalogMapper catalogMapper,
                                        ReservationExecutionLinkMapper executionLinkMapper,
                                        ReservationCancelRequestMapper cancelRequestMapper,
                                        ElderProfileMapper elderProfileMapper,
                                        ElderFamilyLinkMapper familyLinkMapper,
                                        ElderIdentityLinkMapper identityLinkMapper,
                                        WorkOrderMapper workOrderMapper,
                                        UserAccountMapper userAccountMapper,
                                        ObjectAuthorizationPolicy authorizationPolicy) {
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
    }

    @Override
    @Transactional
    public Long createReservation(ReservationCreateDTO dto, Long applicantUserId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("CHILD");
        requireActorId(applicantUserId, current);

        // Resolve the elder and active family link again inside the create transaction.
        authorizationPolicy.requireElderAccess(dto.getElderId());
        if (dto.getServiceDate() == null || dto.getServiceDate().isBefore(LocalDate.now())) {
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
        Long conflictCount = reservationMapper.selectCount(
                new LambdaQueryWrapper<ServiceReservation>()
                        .eq(ServiceReservation::getElderId, dto.getElderId())
                        .eq(ServiceReservation::getServiceType, dto.getServiceType())
                        .eq(ServiceReservation::getServiceDate, dto.getServiceDate())
                        .eq(ServiceReservation::getSlotCode, dto.getSlotCode())
                        .notIn(ServiceReservation::getStatus, List.of("REJECTED", "CANCELLED", "SERVICE_STOPPED"))
        );
        if (conflictCount > 0) {
            throw new BusinessException(ErrorCode.RESERVATION_SLOT_CONFLICT, "长辈在选定时段已存在同类预约，请勿重复预约");
        }

        // 4. 组装实体
        String timestampPrefix = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
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
    @Transactional
    public void cancelReservation(Long reservationId, String reason, Long userId) {
        CurrentUser current = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("CHILD");
        requireActorId(userId, current);
        ServiceReservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        authorizationPolicy.requireElderAccess(reservation.getElderId());
        if (!java.util.Objects.equals(userId, reservation.getApplicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }

        if ("PENDING_CONFIRMATION".equals(reservation.getStatus())) {
            // 接单/确认前直接撤回
            reservation.setStatus("CANCELLED");
            reservation.setRejectReason("用户主动撤回: " + reason);
            reservationMapper.updateById(reservation);
        } else if ("CONFIRMED".equals(reservation.getStatus())) {
            // 已确认预约转为取消审批流
            ReservationCancelRequest request = new ReservationCancelRequest();
            request.setReservationId(reservationId);
            request.setApplicantUserId(userId);
            request.setReason(reason);
            request.setStatus("PENDING_REVIEW");
            cancelRequestMapper.insert(request);
        } else {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前预约状态不支持撤销操作");
        }
    }

    private void requireActorId(Long suppliedUserId, CurrentUser current) {
        if (suppliedUserId == null || !suppliedUserId.equals(currentUserId(current))) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "操作身份与当前会话不一致");
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
