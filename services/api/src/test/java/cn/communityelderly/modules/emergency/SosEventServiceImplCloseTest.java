package cn.communityelderly.modules.emergency;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.dto.SosCloseDTO;
import cn.communityelderly.modules.emergency.entity.SosEvent;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.emergency.service.impl.SosEventServiceImpl;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SosEventServiceImplCloseTest {

    private static final long SOS_ID = 101L;
    private static final long ELDER_ID = 202L;
    private static final long WORK_ORDER_ID = 303L;

    private final SosEventMapper sosEventMapper = mock(SosEventMapper.class);
    private final WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
    private final WorkOrderAssignmentHistoryMapper assignmentHistoryMapper = mock(WorkOrderAssignmentHistoryMapper.class);
    private final WorkOrderActionHistoryMapper actionHistoryMapper = mock(WorkOrderActionHistoryMapper.class);
    private final ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
    private final ObjectAuthorizationPolicy authorizationPolicy = mock(ObjectAuthorizationPolicy.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);

    private SosEventServiceImpl service;
    private SosEvent sos;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new SosEventServiceImpl(
                sosEventMapper,
                workOrderMapper,
                assignmentHistoryMapper,
                actionHistoryMapper,
                elderProfileMapper,
                authorizationPolicy,
                userAccountMapper,
                userRoleMapper);

        sos = new SosEvent();
        sos.setSosId(SOS_ID);
        sos.setElderId(ELDER_ID);
        sos.setInitialWorkOrderId(WORK_ORDER_ID);

        workOrder = new WorkOrder();
        workOrder.setWorkOrderId(WORK_ORDER_ID);
        workOrder.setElderId(ELDER_ID);
        workOrder.setCommunityId(1L);

        ElderProfile elder = new ElderProfile();
        elder.setElderId(ELDER_ID);
        elder.setCommunityId(1L);

        when(sosEventMapper.selectById(SOS_ID)).thenReturn(sos);
        when(authorizationPolicy.requireElderInCurrentCommunity(ELDER_ID, "COMMUNITY_ADMIN")).thenReturn(elder);
        when(workOrderMapper.selectById(WORK_ORDER_ID)).thenReturn(workOrder);
        when(authorizationPolicy.requireCurrentUser()).thenReturn(
                new CurrentUser("42", "13800000042", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1"));
    }

    @Test
    void normalCloseSetsSosClosedAndRecordsProcessingToClosed() {
        sos.setStatus("ACCEPTED");
        workOrder.setStatus("PROCESSING");

        service.closeSos(SOS_ID, closeRequest(false));

        assertEquals("CLOSED", sos.getStatus());
        assertEquals("CLOSED", workOrder.getStatus());
        ArgumentCaptor<WorkOrderActionHistory> actionCaptor = ArgumentCaptor.forClass(WorkOrderActionHistory.class);
        verify(actionHistoryMapper).insert(actionCaptor.capture());
        assertEquals("PROCESSING", actionCaptor.getValue().getFromStatus());
        assertEquals("CLOSED", actionCaptor.getValue().getToStatus());
        assertEquals("CLOSE", actionCaptor.getValue().getActionName());
    }

    @Test
    void cancelledSosCanBeClosedAfterPhoneFollowUp() {
        sos.setStatus("CANCELLED");
        workOrder.setStatus("PROCESSING");

        service.closeSos(SOS_ID, closeRequest(false));

        assertEquals("CLOSED", sos.getStatus());
        assertEquals("CLOSED", workOrder.getStatus());
    }

    @Test
    void falseAlarmBeforeProcessingVoidsSosAndWorkOrderWithActualFromStatus() {
        sos.setStatus("CANCELLED");
        workOrder.setStatus("ACCEPTED");

        service.closeSos(SOS_ID, closeRequest(true));

        assertEquals("VOIDED", sos.getStatus());
        assertEquals("VOIDED", workOrder.getStatus());
        ArgumentCaptor<WorkOrderActionHistory> actionCaptor = ArgumentCaptor.forClass(WorkOrderActionHistory.class);
        verify(actionHistoryMapper).insert(actionCaptor.capture());
        assertEquals("ACCEPTED", actionCaptor.getValue().getFromStatus());
        assertEquals("VOIDED", actionCaptor.getValue().getToStatus());
        assertEquals("VOID", actionCaptor.getValue().getActionName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "VOIDED"})
    void terminalSosCannotBeClosedAgain(String terminalStatus) {
        sos.setStatus(terminalStatus);
        workOrder.setStatus("PROCESSING");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.closeSos(SOS_ID, closeRequest(false)));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(sosEventMapper, never()).updateById(any(SosEvent.class));
        verify(actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    void normalCloseRequiresProcessingWorkOrder() {
        sos.setStatus("ACCEPTED");
        workOrder.setStatus("ACCEPTED");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.closeSos(SOS_ID, closeRequest(false)));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(sosEventMapper, never()).updateById(any(SosEvent.class));
        verify(actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    void falseAlarmCannotBeVoidedAfterProcessingStarts() {
        sos.setStatus("ACCEPTED");
        workOrder.setStatus("PROCESSING");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.closeSos(SOS_ID, closeRequest(true)));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, ex.getErrorCode());
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(sosEventMapper, never()).updateById(any(SosEvent.class));
        verify(actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    void unauthorizedRoleCannotCloseSos() {
        doThrow(new BusinessException(ErrorCode.ACCESS_DENIED))
                .when(authorizationPolicy).requireRole(eq("COMMUNITY_ADMIN"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.closeSos(SOS_ID, closeRequest(false)));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getErrorCode());
        verify(sosEventMapper, never()).selectById(SOS_ID);
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(sosEventMapper, never()).updateById(any(SosEvent.class));
        verify(actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    private SosCloseDTO closeRequest(boolean falseAlarm) {
        SosCloseDTO dto = new SosCloseDTO();
        dto.setResultSummary("已完成现场核查并确认后续处置安排");
        dto.setIsFalseAlarm(falseAlarm);
        return dto;
    }
}
