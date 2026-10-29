package cn.communityelderly.modules.reservation;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.dto.ReservationCancelReviewDTO;
import cn.communityelderly.modules.reservation.entity.ReservationCancelRequest;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.impl.ServiceReservationServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationCancellationReviewServiceTest {

    @Test
    @DisplayName("拒绝取消申请保留预约与工单，保存审批人时间理由并只通知一次")
    void rejectionRetainsReservationAndPublishesOneDeduplicatedNotification() {
        Long cancelId = 501L;
        Long reservationId = 33L;
        Long applicantId = 44L;
        Long reviewerId = 5L;
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 10, 28, 11, 0);

        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationCancelRequestMapper cancelMapper = mock(ReservationCancelRequestMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        NotificationService notificationService = mock(NotificationService.class);
        WorkOrderService workOrderService = mock(WorkOrderService.class);
        ServiceReservation reservation = reservation(reservationId, 22L, applicantId);
        ReservationCancelRequest request = cancellation(cancelId, reservationId, applicantId);
        ElderProfile elder = new ElderProfile();
        elder.setElderId(22L);
        elder.setCommunityId(8L);
        Clock clock = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"), ZoneId.of("Asia/Shanghai"));

        when(authorization.requireCurrentUser()).thenReturn(adminUser(reviewerId));
        when(authorization.requireElderInCurrentCommunity(22L, "COMMUNITY_ADMIN")).thenReturn(elder);
        when(cancelMapper.selectByIdForUpdate(cancelId)).thenReturn(request);
        when(reservationMapper.selectByIdForUpdate(reservationId)).thenReturn(reservation);
        when(cancelMapper.reviewPendingRequest(cancelId, "REJECTED", reviewerId, reviewedAt,
                "继续安排原服务")).thenAnswer(invocation -> {
            request.setStatus("REJECTED");
            request.setReviewerUserId(reviewerId);
            request.setReviewedAt(reviewedAt);
            request.setReviewReason("继续安排原服务");
            return 1;
        });

        ServiceReservationServiceImpl service = new ServiceReservationServiceImpl(
                reservationMapper,
                mock(ServiceCatalogMapper.class),
                mock(ReservationExecutionLinkMapper.class),
                cancelMapper,
                mock(ElderProfileMapper.class),
                mock(ElderFamilyLinkMapper.class),
                mock(ElderIdentityLinkMapper.class),
                mock(WorkOrderMapper.class),
                mock(UserAccountMapper.class),
                authorization,
                workOrderService,
                auditLogService,
                notificationService,
                new ObjectMapper(),
                clock);

        ReservationCancelReviewDTO dto = new ReservationCancelReviewDTO();
        dto.setDecision("REJECT");
        dto.setReviewReason("继续安排原服务");

        service.reviewCancellationRequest(cancelId, dto, reviewerId);

        assertEquals("CONFIRMED", reservation.getStatus());
        assertEquals("REJECTED", request.getStatus());
        assertEquals(reviewerId, request.getReviewerUserId());
        assertEquals(reviewedAt, request.getReviewedAt());
        assertEquals("继续安排原服务", request.getReviewReason());
        verify(reservationMapper, never()).updateById(any(ServiceReservation.class));
        verify(workOrderService, never()).terminateBeforeAcceptance(any(), any(), anyString());

        ArgumentCaptor<NotificationCreateEventDTO> notification =
                ArgumentCaptor.forClass(NotificationCreateEventDTO.class);
        verify(notificationService, times(1)).publishEvent(notification.capture());
        assertEquals("RESERVATION_CANCEL_REJECTED", notification.getValue().getEventType());
        assertEquals("RESERVATION_CANCEL_REJECTED:501", notification.getValue().getDedupKey());
        assertEquals(List.of(applicantId), notification.getValue().getRecipientUserIds());

        verify(auditLogService).recordAudit(eq(reviewerId), eq("COMMUNITY_ADMIN"), eq(8L),
                eq("SERVICE_RESERVATION"), eq(String.valueOf(reservationId)),
                eq("REVIEW_RESERVATION_CANCELLATION"), eq("SUCCESS"), eq("127.0.0.1"), anyString());

        BusinessException retry = assertThrows(BusinessException.class,
                () -> service.reviewCancellationRequest(cancelId, dto, reviewerId));
        assertEquals(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, retry.getErrorCode());
        verify(notificationService, times(1)).publishEvent(any(NotificationCreateEventDTO.class));
    }

    private ServiceReservation reservation(Long reservationId, Long elderId, Long applicantId) {
        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationId(reservationId);
        reservation.setReservationNo("RSV-" + reservationId);
        reservation.setElderId(elderId);
        reservation.setApplicantUserId(applicantId);
        reservation.setServiceType("DAILY_CARE");
        reservation.setServiceDate(LocalDate.of(2026, 11, 20));
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

    private CurrentUser adminUser(Long userId) {
        return new CurrentUser(String.valueOf(userId), "13800000000", "STAFF",
                List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "8");
    }
}
