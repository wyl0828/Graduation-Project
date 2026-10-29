package cn.communityelderly.modules.reservation;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.coordination.ReservationWorkOrderCoordinator;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.entity.ReservationCancelRequest;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import cn.communityelderly.modules.reservation.service.impl.ServiceReservationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationCancellationConcurrencyTest {

    @AfterEach
    void clearUserContext() {
        cn.communityelderly.common.security.UserContext.clear();
    }

    @Test
    @DisplayName("并发更新使预约确认失败时不得继续创建工单或关联")
    void confirmationStopsWhenReservationOptimisticUpdateLoses() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationExecutionLinkMapper linkMapper = mock(ReservationExecutionLinkMapper.class);
        WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
        WorkOrderActionHistoryMapper historyMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        ServiceReservation reservation = reservation(31L, 22L, 44L, "PENDING_CONFIRMATION");
        ElderProfile elder = new ElderProfile();
        elder.setElderId(22L);
        elder.setCommunityId(8L);
        elder.setStatus("ACTIVE");
        when(reservationMapper.selectById(31L)).thenReturn(reservation);
        when(reservationMapper.selectByIdForUpdate(31L)).thenReturn(reservation);
        when(elderMapper.selectByIdForUpdate(22L)).thenReturn(elder);
        when(reservationMapper.updateById(any(ServiceReservation.class))).thenReturn(0);
        when(authorization.requireCurrentUser()).thenReturn(adminUser(5L, 8L));
        when(authorization.requireElderInCurrentCommunity(22L, "COMMUNITY_ADMIN")).thenReturn(elder);

        ReservationWorkOrderCoordinator coordinator = new ReservationWorkOrderCoordinator(
                reservationMapper, linkMapper, workOrderMapper, historyMapper, elderMapper,
                mock(ServiceReservationService.class), authorization, auditLogService,
                Clock.system(ZoneId.of("Asia/Shanghai")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> coordinator.confirmReservationAndCreateWorkOrder(31L, 5L));

        assertEquals(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, error.getErrorCode());
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));
        verify(linkMapper, never()).insert(any(ReservationExecutionLink.class));
        verify(historyMapper, never()).insert(any(WorkOrderActionHistory.class));
        verify(auditLogService, never()).recordAudit(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("并发更新使待确认预约撤销失败时返回冲突而不谎报成功")
    void directCancellationReportsOptimisticUpdateConflict() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationCancelRequestMapper cancelMapper = mock(ReservationCancelRequestMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        ServiceReservation reservation = reservation(32L, 22L, 4L, "PENDING_CONFIRMATION");

        when(authorization.requireCurrentUser()).thenReturn(childUser(4L));
        when(reservationMapper.selectByIdForUpdate(32L)).thenReturn(reservation);
        when(reservationMapper.updateById(any(ServiceReservation.class))).thenReturn(0);

        ServiceReservationServiceImpl service = reservationService(reservationMapper, cancelMapper, authorization);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.cancelReservation(32L, "临时改期", 4L));

        assertEquals(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, error.getErrorCode());
        verify(cancelMapper, never()).insert(any(ReservationCancelRequest.class));
    }

    @Test
    @DisplayName("预约工单已接单后不再允许新建预约取消申请")
    void cancellationRequestIsRejectedAfterWorkOrderAcceptance() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationCancelRequestMapper cancelMapper = mock(ReservationCancelRequestMapper.class);
        ReservationExecutionLinkMapper linkMapper = mock(ReservationExecutionLinkMapper.class);
        WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        ServiceReservation reservation = reservation(33L, 22L, 4L, "CONFIRMED");
        ReservationExecutionLink link = new ReservationExecutionLink();
        link.setReservationId(33L);
        link.setWorkOrderId(66L);
        WorkOrder order = new WorkOrder();
        order.setWorkOrderId(66L);
        order.setStatus("ACCEPTED");

        when(authorization.requireCurrentUser()).thenReturn(childUser(4L));
        when(reservationMapper.selectByIdForUpdate(33L)).thenReturn(reservation);
        when(linkMapper.selectOne(any())).thenReturn(link);
        when(workOrderMapper.selectByIdForUpdate(66L)).thenReturn(order);

        ServiceReservationServiceImpl service = reservationService(
                reservationMapper, cancelMapper, linkMapper, workOrderMapper, authorization);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.cancelReservation(33L, "临时改期", 4L));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(cancelMapper, never()).insert(any(ReservationCancelRequest.class));
    }

    private ServiceReservationServiceImpl reservationService(ServiceReservationMapper reservationMapper,
                                                              ReservationCancelRequestMapper cancelMapper,
                                                              ObjectAuthorizationPolicy authorization) {
        return reservationService(reservationMapper, cancelMapper,
                mock(ReservationExecutionLinkMapper.class), mock(WorkOrderMapper.class), authorization);
    }

    private ServiceReservationServiceImpl reservationService(ServiceReservationMapper reservationMapper,
                                                              ReservationCancelRequestMapper cancelMapper,
                                                              ReservationExecutionLinkMapper linkMapper,
                                                              WorkOrderMapper workOrderMapper,
                                                              ObjectAuthorizationPolicy authorization) {
        return new ServiceReservationServiceImpl(
                reservationMapper,
                mock(ServiceCatalogMapper.class),
                linkMapper,
                cancelMapper,
                mock(ElderProfileMapper.class),
                mock(ElderFamilyLinkMapper.class),
                mock(ElderIdentityLinkMapper.class),
                workOrderMapper,
                mock(UserAccountMapper.class),
                authorization,
                mock(WorkOrderService.class),
                mock(AuditLogService.class),
                mock(NotificationService.class),
                new ObjectMapper(),
                Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    private ServiceReservation reservation(Long id, Long elderId, Long applicantId, String status) {
        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationId(id);
        reservation.setElderId(elderId);
        reservation.setApplicantUserId(applicantId);
        reservation.setReservationNo("RSV-" + id);
        reservation.setServiceType("DAILY_CARE");
        reservation.setServiceDate(LocalDate.of(2026, 12, 1));
        reservation.setSlotCode("MORNING");
        reservation.setServiceAddress("社区服务地址");
        reservation.setStatus(status);
        reservation.setRowVersion(1L);
        return reservation;
    }

    private CurrentUser adminUser(Long userId, Long communityId) {
        return new CurrentUser(String.valueOf(userId), "13800000000", "STAFF",
                List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", String.valueOf(communityId));
    }

    private CurrentUser childUser(Long userId) {
        return new CurrentUser(String.valueOf(userId), "13900000000", "FAMILY",
                List.of("CHILD"), "CHILD", "8");
    }
}
