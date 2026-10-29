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
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.reservation.dto.ReservationCancelDTO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.impl.ServiceReservationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import jakarta.validation.constraints.Size;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationCancellationReasonLengthTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T03:00:00Z"),
            ZoneId.of("Asia/Shanghai"));
    private static final String DIRECT_CANCEL_PREFIX = "用户主动撤回: ";
    private static final int MAX_STORED_REASON_LENGTH = 255;

    @Test
    @DisplayName("取消 DTO 的校验上限扣除直接撤回原因固定前缀")
    void dtoCapsReasonToAvailableStorageLength() throws NoSuchFieldException {
        Size size = ReservationCancelDTO.class.getDeclaredField("reason").getAnnotation(Size.class);

        assertEquals(MAX_STORED_REASON_LENGTH - DIRECT_CANCEL_PREFIX.length(), size.max());
    }

    @Test
    @DisplayName("待确认预约直接取消时前缀加原因正好不超过数据库列长")
    void acceptsLongestReasonThatFitsWithAuditPrefix() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        String reason = "r".repeat(MAX_STORED_REASON_LENGTH - DIRECT_CANCEL_PREFIX.length());
        ServiceReservation reservation = reservation(88L, 7L, 44L);
        when(authorization.requireCurrentUser()).thenReturn(child());
        when(authorization.requireElderAccess(7L)).thenReturn(elder());
        when(reservationMapper.selectByIdForUpdate(88L)).thenReturn(reservation);
        when(reservationMapper.updateById(any(ServiceReservation.class))).thenReturn(1);

        service(reservationMapper, authorization, auditLogService)
                .cancelReservation(88L, reason, 44L);

        assertEquals(MAX_STORED_REASON_LENGTH, reservation.getRejectReason().length());
        assertEquals(DIRECT_CANCEL_PREFIX + reason, reservation.getRejectReason());
        verify(reservationMapper).updateById(reservation);
        verify(auditLogService).recordAudit(any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("待确认预约直接取消原因超出带前缀列长时在任何写入前拒绝")
    void rejectsReasonThatWouldOverflowPrefixedColumn() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ObjectAuthorizationPolicy authorization = mock(ObjectAuthorizationPolicy.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ServiceReservation reservation = reservation(89L, 7L, 44L);
        when(authorization.requireCurrentUser()).thenReturn(child());
        when(authorization.requireElderAccess(7L)).thenReturn(elder());
        when(reservationMapper.selectByIdForUpdate(89L)).thenReturn(reservation);
        when(reservationMapper.updateById(any(ServiceReservation.class))).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service(reservationMapper, authorization, auditLogService)
                        .cancelReservation(89L,
                                "r".repeat(MAX_STORED_REASON_LENGTH - DIRECT_CANCEL_PREFIX.length() + 1), 44L));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verify(reservationMapper, never()).updateById(any(ServiceReservation.class));
        verify(auditLogService, never()).recordAudit(any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    private ServiceReservationServiceImpl service(ServiceReservationMapper reservationMapper,
                                                  ObjectAuthorizationPolicy authorization,
                                                  AuditLogService auditLogService) {
        return new ServiceReservationServiceImpl(
                reservationMapper, mock(ServiceCatalogMapper.class), mock(ReservationExecutionLinkMapper.class),
                mock(ReservationCancelRequestMapper.class), mock(ElderProfileMapper.class),
                mock(ElderFamilyLinkMapper.class), mock(ElderIdentityLinkMapper.class), mock(WorkOrderMapper.class),
                mock(UserAccountMapper.class), authorization, mock(WorkOrderService.class), auditLogService,
                mock(NotificationService.class), new ObjectMapper(), CLOCK);
    }

    private ServiceReservation reservation(Long reservationId, Long elderId, Long applicantId) {
        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationId(reservationId);
        reservation.setElderId(elderId);
        reservation.setApplicantUserId(applicantId);
        reservation.setStatus("PENDING_CONFIRMATION");
        reservation.setRowVersion(1L);
        reservation.setRejectReason(null);
        reservation.setReservationNo("RSV-" + reservationId);
        reservation.setServiceDate(LocalDate.now(CLOCK).plusDays(2));
        return reservation;
    }

    private CurrentUser child() {
        return new CurrentUser("44", "13800138000", "FAMILY", List.of("CHILD"), "CHILD", "8");
    }

    private ElderProfile elder() {
        ElderProfile elder = new ElderProfile();
        elder.setElderId(7L);
        elder.setCommunityId(8L);
        elder.setStatus("ACTIVE");
        return elder;
    }
}
