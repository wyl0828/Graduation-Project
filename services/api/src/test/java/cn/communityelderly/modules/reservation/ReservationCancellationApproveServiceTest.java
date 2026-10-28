package cn.communityelderly.modules.reservation;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.coordination.ReservationWorkOrderCoordinator;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderHandoffMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.order.mapper.ServiceStopRequestMapper;
import cn.communityelderly.modules.order.service.impl.WorkOrderServiceImpl;
import cn.communityelderly.modules.reservation.dto.ReservationCancelReviewDTO;
import cn.communityelderly.modules.reservation.entity.ReservationCancelRequest;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.impl.ServiceReservationServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationCancellationApproveServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    @DisplayName("确定性批准取消会原子终止预约和工单并写审批审计通知一次")
    void approvalTerminatesReservationAndWorkOrderWithOneReviewSideEffectSet() {
        Long cancelId = 91L;
        Long reservationId = 71L;
        Long workOrderId = 202L;
        Long applicantId = 44L;
        Long reviewerId = 10L;

        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationCancelRequestMapper cancelMapper = mock(ReservationCancelRequestMapper.class);
        ReservationExecutionLinkMapper linkMapper = mock(ReservationExecutionLinkMapper.class);
        WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
        WorkOrderActionHistoryMapper actionHistoryMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        NotificationService notificationService = mock(NotificationService.class);

        ServiceReservation reservation = reservation(reservationId, 7L, applicantId);
        ReservationCancelRequest request = cancellation(cancelId, reservationId, applicantId);
        ReservationExecutionLink link = new ReservationExecutionLink();
        link.setReservationId(reservationId);
        link.setWorkOrderId(workOrderId);
        WorkOrder order = workOrder(workOrderId, 7L, 8L, 9L, "DISPATCHED");
        ElderProfile elder = elder();
        CurrentUser admin = new CurrentUser("10", "13800138000", "STAFF",
                List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "8");

        when(authorization.requireCurrentUser()).thenReturn(admin);
        when(authorization.requireElderInCurrentCommunity(7L, "COMMUNITY_ADMIN")).thenReturn(elder);
        when(cancelMapper.selectByIdForUpdate(cancelId)).thenReturn(request);
        when(reservationMapper.selectByIdForUpdate(reservationId)).thenReturn(reservation);
        when(reservationMapper.selectById(reservationId)).thenReturn(reservation);
        when(linkMapper.selectOne(any(Wrapper.class))).thenReturn(link);
        when(workOrderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(cancelMapper.reviewPendingRequest(eq(cancelId), eq("APPROVED"), eq(reviewerId),
                any(LocalDateTime.class), eq("确认停用前工单安排"))).thenAnswer(invocation -> {
            request.setStatus("APPROVED");
            request.setReviewerUserId(reviewerId);
            request.setReviewedAt(invocation.getArgument(3));
            request.setReviewReason(invocation.getArgument(4));
            return 1;
        });
        when(reservationMapper.updateById(any(ServiceReservation.class))).thenReturn(1);
        when(workOrderMapper.updateById(any(WorkOrder.class))).thenReturn(1);
        when(actionHistoryMapper.insert(any(WorkOrderActionHistory.class))).thenReturn(1);

        WorkOrderServiceImpl workOrderService = new WorkOrderServiceImpl(
                workOrderMapper, mock(WorkOrderAssignmentHistoryMapper.class), actionHistoryMapper,
                mock(WorkOrderHandoffMapper.class), mock(ServiceStopRequestMapper.class), elderMapper,
                userMapper, mock(UserRoleMapper.class), linkMapper, reservationMapper, authorization,
                auditLogService, notificationService, new ObjectMapper(), CLOCK);
        ServiceReservationServiceImpl reservationService = new ServiceReservationServiceImpl(
                reservationMapper, mock(ServiceCatalogMapper.class), linkMapper, cancelMapper, elderMapper,
                mock(ElderFamilyLinkMapper.class), mock(ElderIdentityLinkMapper.class), workOrderMapper,
                userMapper, authorization, workOrderService, auditLogService, notificationService,
                new ObjectMapper(), CLOCK);
        ReservationCancelReviewDTO dto = new ReservationCancelReviewDTO();
        dto.setDecision("APPROVE");
        dto.setReviewReason("确认停用前工单安排");

        reservationService.reviewCancellationRequest(cancelId, dto, reviewerId);

        assertEquals("APPROVED", request.getStatus());
        assertEquals(reviewerId, request.getReviewerUserId());
        assertEquals("确认停用前工单安排", request.getReviewReason());
        assertEquals("CANCELLED", reservation.getStatus());
        assertEquals("TERMINATED_PRE_ACCEPTANCE", order.getStatus());
        verify(cancelMapper).reviewPendingRequest(eq(cancelId), eq("APPROVED"), eq(reviewerId),
                any(LocalDateTime.class), eq("确认停用前工单安排"));
        verify(reservationMapper).updateById(reservation);
        verify(workOrderMapper).updateById(order);

        ArgumentCaptor<WorkOrderActionHistory> history = ArgumentCaptor.forClass(WorkOrderActionHistory.class);
        verify(actionHistoryMapper).insert(history.capture());
        assertEquals("TERMINATE_PRE_ACCEPTANCE", history.getValue().getActionName());
        verify(auditLogService).recordAudit(eq(reviewerId), eq("COMMUNITY_ADMIN"), eq(8L),
                eq("WORK_ORDER"), eq(String.valueOf(workOrderId)),
                eq("WORK_ORDER_TERMINATE_PRE_ACCEPTANCE"), eq("SUCCESS"), eq("127.0.0.1"), anyString());
        verify(auditLogService).recordAudit(eq(reviewerId), eq("COMMUNITY_ADMIN"), eq(8L),
                eq("SERVICE_RESERVATION"), eq(String.valueOf(reservationId)),
                eq("REVIEW_RESERVATION_CANCELLATION"), eq("SUCCESS"), eq("127.0.0.1"), anyString());
        ArgumentCaptor<NotificationCreateEventDTO> notification =
                ArgumentCaptor.forClass(NotificationCreateEventDTO.class);
        verify(notificationService).publishEvent(notification.capture());
        assertEquals("WORK_ORDER_TERMINATED_PRE_ACCEPTANCE", notification.getValue().getEventType());
        assertEquals("WORK_ORDER_TERMINATED_PRE_ACCEPTANCE:" + workOrderId,
                notification.getValue().getDedupKey());
        assertEquals(List.of(9L, applicantId), notification.getValue().getRecipientUserIds());

        BusinessException replay = assertThrows(BusinessException.class,
                () -> reservationService.reviewCancellationRequest(cancelId, dto, reviewerId));
        assertEquals(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, replay.getErrorCode());
        verify(notificationService, times(1)).publishEvent(any(NotificationCreateEventDTO.class));
        verify(auditLogService, times(2)).recordAudit(any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    private ServiceReservation reservation(Long reservationId, Long elderId, Long applicantId) {
        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationId(reservationId);
        reservation.setReservationNo("RSV-" + reservationId);
        reservation.setElderId(elderId);
        reservation.setApplicantUserId(applicantId);
        reservation.setServiceType("DAILY_CARE");
        reservation.setServiceDate(LocalDate.now(CLOCK).plusDays(2));
        reservation.setSlotCode("MORNING");
        reservation.setStatus("CONFIRMED");
        reservation.setRowVersion(1L);
        return reservation;
    }

    private ReservationCancelRequest cancellation(Long cancelId, Long reservationId, Long applicantId) {
        ReservationCancelRequest request = new ReservationCancelRequest();
        request.setCancelId(cancelId);
        request.setReservationId(reservationId);
        request.setApplicantUserId(applicantId);
        request.setReason("家属临时改变计划");
        request.setStatus("PENDING_REVIEW");
        return request;
    }

    private WorkOrder workOrder(Long workOrderId, Long elderId, Long communityId,
                                Long assigneeId, String status) {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderId(workOrderId);
        order.setOrderNo("WO-" + workOrderId);
        order.setSourceType("RESERVATION");
        order.setSourceReferenceId("71");
        order.setElderId(elderId);
        order.setCommunityId(communityId);
        order.setCurrentAssigneeId(assigneeId);
        order.setStatus(status);
        order.setRowVersion(1L);
        return order;
    }

    private ElderProfile elder() {
        ElderProfile elder = new ElderProfile();
        elder.setElderId(7L);
        elder.setCommunityId(8L);
        elder.setStatus("ACTIVE");
        return elder;
    }
}
