package cn.communityelderly.modules.emergency;

import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.dto.SosEventVO;
import cn.communityelderly.modules.emergency.dto.SosTriggerDTO;
import cn.communityelderly.modules.emergency.entity.SosEvent;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.emergency.service.impl.SosEventServiceImpl;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SosEventInactiveMarkerTest {

    private static final long ELDER_ID = 202L;
    private static final long USER_ID = 303L;
    private static final long WORK_ORDER_ID = 404L;
    private static final long SOS_ID = 505L;

    private final SosEventMapper sosEventMapper = mock(SosEventMapper.class);
    private final WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
    private final WorkOrderAssignmentHistoryMapper assignmentHistoryMapper = mock(WorkOrderAssignmentHistoryMapper.class);
    private final WorkOrderActionHistoryMapper actionHistoryMapper = mock(WorkOrderActionHistoryMapper.class);
    private final ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
    private final ObjectAuthorizationPolicy authorizationPolicy = mock(ObjectAuthorizationPolicy.class);
    private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);

    private SosEventServiceImpl service;
    private ElderProfile elder;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        service = new SosEventServiceImpl(sosEventMapper, workOrderMapper, assignmentHistoryMapper,
                actionHistoryMapper, elderProfileMapper, authorizationPolicy, userAccountMapper, userRoleMapper);
        elder = new ElderProfile();
        elder.setElderId(ELDER_ID);
        elder.setCommunityId(1L);
        elder.setStatus("INACTIVE");
        workOrder = new WorkOrder();
        workOrder.setCommunityId(1L);

        when(authorizationPolicy.requireElderIdentityId()).thenReturn(ELDER_ID);
        when(authorizationPolicy.requireElderAccess(ELDER_ID)).thenReturn(elder);
        when(elderProfileMapper.selectById(ELDER_ID)).thenReturn(elder);
        when(elderProfileMapper.selectByIdForUpdate(ELDER_ID)).thenReturn(elder);
        when(sosEventMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(workOrderMapper.insert(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder inserted = invocation.getArgument(0);
            inserted.setWorkOrderId(WORK_ORDER_ID);
            return 1;
        });
        when(sosEventMapper.insert(any(SosEvent.class))).thenAnswer(invocation -> {
            SosEvent inserted = invocation.getArgument(0);
            inserted.setSosId(SOS_ID);
            return 1;
        });
        when(workOrderMapper.selectById(WORK_ORDER_ID)).thenAnswer(invocation -> {
            workOrder.setWorkOrderId(WORK_ORDER_ID);
            workOrder.setElderId(ELDER_ID);
            workOrder.setStatus("PENDING_DISPATCH");
            return workOrder;
        });
    }

    @Test
    void inactiveElderAcceptancePersistsSnapshotOnEventAndReturnedView() {
        CurrentUser elderUser = new CurrentUser(String.valueOf(USER_ID), "13800000003", "ELDER",
                List.of("ELDER"), "ELDER", "1");
        ElderProfile staleAuthorizedRead = new ElderProfile();
        staleAuthorizedRead.setElderId(ELDER_ID);
        staleAuthorizedRead.setCommunityId(1L);
        staleAuthorizedRead.setStatus("ACTIVE");
        when(authorizationPolicy.requireElderAccess(ELDER_ID)).thenReturn(staleAuthorizedRead);
        when(authorizationPolicy.requireCurrentUser()).thenReturn(elderUser);

        SosTriggerDTO request = triggerRequest();
        SosEventVO accepted = service.triggerSos(request);

        ArgumentCaptor<SosEvent> persistedCaptor = ArgumentCaptor.forClass(SosEvent.class);
        verify(sosEventMapper).insert(persistedCaptor.capture());
        assertEquals(Boolean.TRUE, marker(persistedCaptor.getValue()));
        assertEquals(Boolean.TRUE, marker(accepted));
        InOrder insertOrder = org.mockito.Mockito.inOrder(elderProfileMapper, workOrderMapper, sosEventMapper);
        insertOrder.verify(elderProfileMapper).selectByIdForUpdate(ELDER_ID);
        insertOrder.verify(workOrderMapper).insert(any(WorkOrder.class));
        insertOrder.verify(sosEventMapper).insert(any(SosEvent.class));

        // A later profile reactivation must not rewrite the snapshot on an idempotent replay.
        elder.setStatus("ACTIVE");
        when(sosEventMapper.selectOne(any(Wrapper.class))).thenReturn(persistedCaptor.getValue());
        SosEventVO replay = service.triggerSos(request);
        assertEquals(Boolean.TRUE, marker(replay));
        verify(sosEventMapper, org.mockito.Mockito.times(1)).insert(any(SosEvent.class));
    }

    @Test
    void activeElderAcceptanceStoresFalseSnapshot() {
        elder.setStatus("ACTIVE");
        when(authorizationPolicy.requireCurrentUser()).thenReturn(
                new CurrentUser(String.valueOf(USER_ID), "13800000003", "ELDER",
                        List.of("ELDER"), "ELDER", "1"));

        SosEventVO accepted = service.triggerSos(triggerRequest());

        ArgumentCaptor<SosEvent> persistedCaptor = ArgumentCaptor.forClass(SosEvent.class);
        verify(sosEventMapper).insert(persistedCaptor.capture());
        assertEquals(Boolean.FALSE, marker(persistedCaptor.getValue()));
        assertEquals(Boolean.FALSE, marker(accepted));
    }

    private SosTriggerDTO triggerRequest() {
        SosTriggerDTO dto = new SosTriggerDTO();
        dto.setPressDurationMs(2200);
        dto.setIdempotencyKey(UUID.randomUUID().toString());
        return dto;
    }

    private Boolean marker(Object value) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        assertTrue(wrapper.isReadableProperty("elderInactiveAtAcceptance"),
                "SOS event and review view must expose the archived-at-acceptance snapshot");
        return (Boolean) wrapper.getPropertyValue("elderInactiveAtAcceptance");
    }
}
