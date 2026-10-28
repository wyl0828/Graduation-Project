package cn.communityelderly.modules.reservation;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderActivationTicketMapper;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.impl.ElderArchiveServiceImpl;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceCatalog;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.impl.ServiceReservationServiceImpl;
import cn.communityelderly.coordination.ReservationWorkOrderCoordinator;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InactiveElderReservationGateTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    @DisplayName("子女创建预约时按锁定后的档案状态拒绝停用老人且不产生副作用")
    void childCannotCreateReservationForElderDeactivatedAfterAuthorizationRead() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ServiceCatalogMapper catalogMapper = mock(ServiceCatalogMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        CurrentUser child = user(44L, "CHILD", 8L);

        when(authorization.requireCurrentUser()).thenReturn(child);
        when(authorization.requireElderAccess(7L)).thenReturn(elder(7L, 8L, "ACTIVE"));
        when(elderMapper.selectByIdForUpdate(7L)).thenReturn(elder(7L, 8L, "INACTIVE"));

        ServiceReservationServiceImpl service = new ServiceReservationServiceImpl(
                reservationMapper, catalogMapper, mock(ReservationExecutionLinkMapper.class),
                mock(cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper.class),
                elderMapper, mock(ElderFamilyLinkMapper.class), mock(ElderIdentityLinkMapper.class),
                mock(WorkOrderMapper.class), mock(UserAccountMapper.class), authorization,
                mock(WorkOrderService.class), auditLogService, notificationService,
                new ObjectMapper(), CLOCK);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createReservation(createReservation(7L), 44L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(authorization).requireElderAccess(7L);
        verify(elderMapper).selectByIdForUpdate(7L);
        verify(reservationMapper, never()).insert(any(ServiceReservation.class));
        verify(reservationMapper, never()).selectActiveSlotReservationIdsForUpdate(any(), any(), any(), any());
        verify(auditLogService, never()).recordAudit(any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(notificationService, never()).publishEvent(any());
    }

    @Test
    @DisplayName("管理员确认预约前按锁定后的档案状态拒绝停用老人且不生成工单或审计")
    void adminCannotConfirmReservationForInactiveElder() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationExecutionLinkMapper linkMapper = mock(ReservationExecutionLinkMapper.class);
        WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
        WorkOrderActionHistoryMapper historyMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ServiceReservation pending = reservation(103L, 7L, 44L, "PENDING_CONFIRMATION");
        CurrentUser admin = user(10L, "COMMUNITY_ADMIN", 8L);

        when(authorization.requireCurrentUser()).thenReturn(admin);
        when(authorization.requireElderInCurrentCommunity(7L, "COMMUNITY_ADMIN"))
                .thenReturn(elder(7L, 8L, "ACTIVE"));
        when(reservationMapper.selectById(103L)).thenReturn(pending);
        when(reservationMapper.selectByIdForUpdate(103L)).thenReturn(pending);
        when(elderMapper.selectByIdForUpdate(7L)).thenReturn(elder(7L, 8L, "INACTIVE"));
        when(reservationMapper.updateById(any(ServiceReservation.class))).thenReturn(1);
        when(workOrderMapper.insert(any(WorkOrder.class))).thenReturn(1);
        when(linkMapper.insert(any(ReservationExecutionLink.class))).thenReturn(1);
        when(historyMapper.insert(any(WorkOrderActionHistory.class))).thenReturn(1);

        ReservationWorkOrderCoordinator coordinator = new ReservationWorkOrderCoordinator(
                reservationMapper, linkMapper, workOrderMapper, historyMapper, elderMapper,
                mock(cn.communityelderly.modules.reservation.service.ServiceReservationService.class),
                authorization, auditLogService, CLOCK);

        BusinessException error = assertThrows(BusinessException.class,
                () -> coordinator.confirmReservationAndCreateWorkOrder(103L, 10L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(authorization).requireElderInCurrentCommunity(7L, "COMMUNITY_ADMIN");
        verify(elderMapper).selectByIdForUpdate(7L);
        verify(reservationMapper, never()).updateById(any(ServiceReservation.class));
        verify(reservationMapper, never()).selectByIdForUpdate(103L);
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
        verify(linkMapper, never()).insert(any(ReservationExecutionLink.class));
        verify(historyMapper, never()).insert(any(WorkOrderActionHistory.class));
        verify(auditLogService, never()).recordAudit(any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());

        inOrder(elderMapper, reservationMapper).verify(elderMapper).selectByIdForUpdate(7L);
    }

    @Test
    @DisplayName("停用档案先锁定老人行再检查未结工单")
    void deactivationLocksElderBeforeCheckingOpenOrders() {
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        ElderProfile active = elder(7L, 8L, "ACTIVE");
        when(authorization.requireElderInCurrentCommunity(7L, "COMMUNITY_ADMIN")).thenReturn(active);
        when(elderMapper.selectByIdForUpdate(7L)).thenReturn(active);
        when(elderMapper.updateById(any(ElderProfile.class))).thenReturn(1);

        ElderArchiveServiceImpl service = new ElderArchiveServiceImpl(
                elderMapper, mock(ElderActivationTicketMapper.class), authorization, workOrderService);

        service.updateElderStatus(7L, "INACTIVE", "档案核验停用");

        var sequence = inOrder(elderMapper, workOrderService);
        sequence.verify(elderMapper).selectByIdForUpdate(7L);
        sequence.verify(workOrderService).assertElderMayBeDeactivated(7L);
        verify(elderMapper).updateById(active);
    }

    private ReservationCreateDTO createReservation(Long elderId) {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setElderId(elderId);
        dto.setServiceType("DAILY_CARE");
        dto.setServiceDate(LocalDate.now(CLOCK).plusDays(1));
        dto.setSlotCode("MORNING");
        dto.setServiceAddress("测试地址");
        return dto;
    }

    private ServiceReservation reservation(Long id, Long elderId, Long applicantId, String status) {
        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationId(id);
        reservation.setReservationNo("RSV-" + id);
        reservation.setElderId(elderId);
        reservation.setApplicantUserId(applicantId);
        reservation.setServiceType("DAILY_CARE");
        reservation.setServiceDate(LocalDate.now(CLOCK).plusDays(2));
        reservation.setSlotCode("MORNING");
        reservation.setServiceAddress("测试服务地址");
        reservation.setStatus(status);
        reservation.setRowVersion(1L);
        return reservation;
    }

    private ElderProfile elder(Long elderId, Long communityId, String status) {
        ElderProfile elder = new ElderProfile();
        elder.setElderId(elderId);
        elder.setCommunityId(communityId);
        elder.setStatus(status);
        elder.setRowVersion(1L);
        return elder;
    }

    private CurrentUser user(Long userId, String role, Long communityId) {
        return new CurrentUser(String.valueOf(userId), "13800138000", "STAFF", List.of(role), role,
                String.valueOf(communityId));
    }
}
