package cn.communityelderly.common.security;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.elder.controller.ElderSelfProfileController;
import cn.communityelderly.modules.elder.controller.KinshipController;
import cn.communityelderly.modules.elder.dto.ElderCreateDTO;
import cn.communityelderly.modules.elder.dto.ElderQueryDTO;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderActivationTicketMapper;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.elder.service.KinshipService;
import cn.communityelderly.modules.elder.service.impl.ElderArchiveServiceImpl;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.emergency.dto.SosCancelDTO;
import cn.communityelderly.modules.emergency.dto.SosTriggerDTO;
import cn.communityelderly.modules.emergency.entity.SosEvent;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.emergency.service.impl.SosEventServiceImpl;
import cn.communityelderly.modules.health.dto.AlertHandleDTO;
import cn.communityelderly.modules.health.dto.VitalRecordCreateDTO;
import cn.communityelderly.modules.health.dto.VitalTrendQueryDTO;
import cn.communityelderly.modules.health.entity.HealthAlert;
import cn.communityelderly.modules.health.entity.VitalRecord;
import cn.communityelderly.modules.health.mapper.AlertTriggerEvidenceMapper;
import cn.communityelderly.modules.health.mapper.HealthAlertMapper;
import cn.communityelderly.modules.health.mapper.VitalRecordMapper;
import cn.communityelderly.modules.health.service.AssessmentService;
import cn.communityelderly.modules.health.service.impl.HealthAlertServiceImpl;
import cn.communityelderly.modules.health.service.impl.VitalRecordServiceImpl;
import cn.communityelderly.modules.medication.dto.MedicationCheckinDTO;
import cn.communityelderly.modules.medication.dto.MedicationPlanCreateDTO;
import cn.communityelderly.modules.medication.entity.MedicationPlan;
import cn.communityelderly.modules.medication.entity.MedicationPlanReminderTime;
import cn.communityelderly.modules.medication.entity.MedicationPlanVersion;
import cn.communityelderly.modules.medication.entity.MedicationTask;
import cn.communityelderly.modules.medication.mapper.MedicationCheckinMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanReminderTimeMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanVersionMapper;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import cn.communityelderly.modules.medication.service.impl.MedicationPlanServiceImpl;
import cn.communityelderly.modules.medication.service.impl.MedicationTaskServiceImpl;
import cn.communityelderly.modules.notification.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectAuthorizationServiceTest {

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未绑定本人档案时，/profile/me 拒绝且不读取或更新任何档案")
    void unboundElderCannotReadOrUpdateSelfProfile() {
        ElderArchiveService archiveService = mock(ElderArchiveService.class);
        ElderIdentityLinkMapper identityMapper = mock(ElderIdentityLinkMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper, identityMapper, familyMapper);
        ElderSelfProfileController controller = new ElderSelfProfileController(archiveService, policy);
        UserContext.set(elder("31", "1"));

        BusinessException error = assertThrows(BusinessException.class, controller::getMyProfile);

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(archiveService, never()).getElderById(any());

        BusinessException updateError = assertThrows(BusinessException.class,
                () -> controller.updateMyProfile(new cn.communityelderly.modules.elder.dto.ElderProfileUpdateDTO()));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, updateError.getErrorCode());
        verify(archiveService, never()).updateElderProfileSelf(any(Long.class), any());
    }

    @Test
    @DisplayName("未检测到登录身份时，亲情关系列表不回退到默认子女账号")
    void missingCurrentUserCannotReadDefaultChildLinks() {
        KinshipService kinshipService = mock(KinshipService.class);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(mock(ElderProfileMapper.class),
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class));
        KinshipController controller = new KinshipController(kinshipService, policy);
        UserContext.clear();

        BusinessException error = assertThrows(BusinessException.class, controller::getMyBoundElders);

        assertEquals(ErrorCode.AUTHENTICATION_REQUIRED, error.getErrorCode());
        verify(kinshipService, never()).listBoundElders(anyLong());
    }

    @Test
    @DisplayName("本社区管理员新建老人档案时从当前会话分配社区，不写固定社区")
    void newElderArchiveUsesCurrentCommunity() {
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderActivationTicketMapper activationMapper = mock(ElderActivationTicketMapper.class);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class));
        ElderArchiveServiceImpl service = new ElderArchiveServiceImpl(elderMapper, activationMapper, policy,
                mock(WorkOrderService.class));
        ElderCreateDTO dto = new ElderCreateDTO();
        dto.setName("测试老人");
        dto.setAddress("测试地址");
        dto.setContactPhoneMissingReason("无手机");
        dto.setEmergencyContactPhoneMissingReason("未提供");
        UserContext.set(new CurrentUser("10", "13800000010", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "2"));

        service.createElder(dto);

        ArgumentCaptor<ElderProfile> saved = ArgumentCaptor.forClass(ElderProfile.class);
        verify(elderMapper).insert(saved.capture());
        assertEquals(2L, saved.getValue().getCommunityId());
        assertEquals(10L, saved.getValue().getCreatedBy());
    }

    @Test
    @DisplayName("网格员不能新建老人档案，数据库不产生档案行")
    void gridWorkerCannotCreateElderArchive() {
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderArchiveServiceImpl service = new ElderArchiveServiceImpl(elderMapper,
                mock(ElderActivationTicketMapper.class), new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class)), mock(WorkOrderService.class));
        ElderCreateDTO dto = new ElderCreateDTO();
        dto.setName("测试老人");
        dto.setAddress("测试地址");
        dto.setContactPhoneMissingReason("无手机");
        dto.setEmergencyContactPhoneMissingReason("未提供");
        UserContext.set(new CurrentUser("20", "13800000020", "STAFF", List.of("GRID_WORKER"), "GRID_WORKER", "2"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.createElder(dto));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(elderMapper, never()).insert(any(ElderProfile.class));
    }

    @Test
    @DisplayName("档案分页从当前会话约束社区并返回正确分页元数据")
    void elderArchivePageIsScopedToCurrentCommunity() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), ElderProfile.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderArchiveServiceImpl service = new ElderArchiveServiceImpl(elderMapper,
                mock(ElderActivationTicketMapper.class), new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class)), mock(WorkOrderService.class));
        UserContext.set(new CurrentUser("10", "13800000010", "STAFF",
                List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "2"));
        Page<ElderProfile> dbPage = new Page<>(3, 10);
        dbPage.setTotal(42);
        when(elderMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(dbPage);
        ElderQueryDTO query = new ElderQueryDTO();
        query.setPage(3);
        query.setSize(10);

        var result = service.pageElders(query);

        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ElderProfile>> filter =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(elderMapper).selectPage(any(Page.class), filter.capture());
        assertEquals(true, filter.getValue().getSqlSegment().contains("community_id"));
        assertEquals(List.of(2L), List.copyOf(filter.getValue().getParamNameValuePairs().values()));
        assertEquals(3L, result.getPage());
        assertEquals(10L, result.getPageSize());
        assertEquals(42L, result.getTotal());
    }

    @Test
    @DisplayName("本社区管理员不能改变外社区老人档案状态")
    void adminCannotChangeAnotherCommunityElderStatus() {
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderArchiveServiceImpl service = new ElderArchiveServiceImpl(elderMapper,
                mock(ElderActivationTicketMapper.class), new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class)), mock(WorkOrderService.class));
        when(elderMapper.selectById(77L)).thenReturn(elderProfile(77L, 3L));
        UserContext.set(new CurrentUser("10", "13800000010", "STAFF",
                List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "2"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateElderStatus(77L, "INACTIVE", "测试跨社区状态写入"));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(elderMapper, never()).updateById(any(ElderProfile.class));
    }

    @Test
    @DisplayName("老人用他人 taskId 打卡被拒绝，打卡流水与任务状态均不变")
    void elderCannotCheckInAnotherEldersTask() {
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        MedicationCheckinMapper checkinMapper = mock(MedicationCheckinMapper.class);
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderIdentityLinkMapper identityMapper = mock(ElderIdentityLinkMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        NotificationService notificationService = mock(NotificationService.class);

        MedicationTask task = new MedicationTask();
        task.setTaskId(202L);
        task.setElderId(2L);
        task.setStatus("PENDING");
        when(taskMapper.selectById(202L)).thenReturn(task);
        when(elderMapper.selectById(2L)).thenReturn(elderProfile(2L, 1L));
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(identityLink(31L, 1L));

        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper, identityMapper, familyMapper);
        MedicationTaskServiceImpl service = new MedicationTaskServiceImpl(taskMapper, checkinMapper, planMapper,
                versionMapper, reminderMapper, familyMapper, policy, notificationService);
        MedicationCheckinDTO dto = new MedicationCheckinDTO();
        dto.setTaskId(202L);
        UserContext.set(elder("31", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.checkin(dto));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(checkinMapper, never()).insert(any(cn.communityelderly.modules.medication.entity.MedicationCheckin.class));
        verify(taskMapper, never()).updateById(any(MedicationTask.class));
    }

    @Test
    @DisplayName("老人不能指定他人 elderId 触发 SOS，也不能取消他人的 SOS")
    void elderCannotTriggerOrCancelAnotherEldersSos() {
        SosEventMapper sosMapper = mock(SosEventMapper.class);
        WorkOrderMapper workOrderMapper = mock(WorkOrderMapper.class);
        WorkOrderAssignmentHistoryMapper assignmentMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        WorkOrderActionHistoryMapper actionMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderIdentityLinkMapper identityMapper = mock(ElderIdentityLinkMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(identityLink(31L, 1L));
        when(elderMapper.selectById(2L)).thenReturn(elderProfile(2L, 1L));
        SosEvent otherSos = new SosEvent();
        otherSos.setSosId(77L);
        otherSos.setElderId(2L);
        otherSos.setStatus("ACCEPTED");
        otherSos.setInitialWorkOrderId(88L);
        when(sosMapper.selectById(77L)).thenReturn(otherSos);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper, identityMapper,
                mock(ElderFamilyLinkMapper.class));
        SosEventServiceImpl service = new SosEventServiceImpl(sosMapper, workOrderMapper, assignmentMapper,
                actionMapper, elderMapper, policy, userMapper, userRoleMapper);
        UserContext.set(elder("31", "1"));

        SosTriggerDTO trigger = new SosTriggerDTO();
        trigger.setElderId(2L);
        trigger.setPressDurationMs(2500);
        BusinessException triggerError = assertThrows(BusinessException.class, () -> service.triggerSos(trigger));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, triggerError.getErrorCode());
        verify(sosMapper, never()).insert(any(SosEvent.class));
        verify(workOrderMapper, never()).insert(any(WorkOrder.class));

        BusinessException cancelError = assertThrows(BusinessException.class,
                () -> service.cancelSosByElder(77L, new SosCancelDTO()));
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, cancelError.getErrorCode());
        verify(sosMapper, never()).updateById(any(SosEvent.class));
        verify(workOrderMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    @DisplayName("PENDING 亲情不能创建用药计划，且不插入计划、版本或提醒")
    void pendingKinshipCannotCreateMedicationPlan() {
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        when(elderMapper.selectById(2L)).thenReturn(elderProfile(2L, 1L));
        ElderFamilyLink pending = new ElderFamilyLink();
        pending.setElderId(2L);
        pending.setChildUserId(44L);
        pending.setStatus("PENDING");
        when(familyMapper.selectOne(any(Wrapper.class))).thenReturn(pending);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), familyMapper);
        MedicationPlanServiceImpl service = new MedicationPlanServiceImpl(planMapper, versionMapper, reminderMapper,
                taskMapper, policy, userMapper);
        MedicationPlanCreateDTO dto = new MedicationPlanCreateDTO();
        dto.setElderId(2L);
        dto.setMedicineName("测试药品");
        dto.setDosage("1片");
        dto.setReminderTimes(List.of());
        UserContext.set(new CurrentUser("44", "13800000044", "FAMILY", List.of("CHILD"), "CHILD", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.createPlan(dto));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(planMapper, never()).insert(any(MedicationPlan.class));
        verify(versionMapper, never()).insert(any(MedicationPlanVersion.class));
        verify(reminderMapper, never()).insert(any(MedicationPlanReminderTime.class));
    }

    @Test
    @DisplayName("FROZEN 亲情关系明确拒绝，保留冻结状态的 403 契约")
    void frozenKinshipIsRejected() {
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderFamilyLink frozen = new ElderFamilyLink();
        frozen.setElderId(2L);
        frozen.setChildUserId(44L);
        frozen.setStatus("FROZEN");
        when(familyMapper.selectOne(any(Wrapper.class))).thenReturn(frozen);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), familyMapper);
        UserContext.set(new CurrentUser("44", "13800000044", "FAMILY", List.of("CHILD"), "CHILD", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> policy.requireActiveChildLink(2L));

        assertEquals(ErrorCode.KINSHIP_NOT_ACTIVE, error.getErrorCode());
    }

    @Test
    @DisplayName("UNLINKED 亲情关系按不可访问资源返回 404，且不读取老人档案")
    void unlinkedKinshipIsHiddenAsInaccessible() {
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderFamilyLink unlinked = new ElderFamilyLink();
        unlinked.setElderId(2L);
        unlinked.setChildUserId(44L);
        unlinked.setStatus("UNLINKED");
        when(familyMapper.selectOne(any(Wrapper.class))).thenReturn(unlinked);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), familyMapper);
        UserContext.set(new CurrentUser("44", "13800000044", "FAMILY", List.of("CHILD"), "CHILD", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> policy.requireActiveChildLink(2L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(elderMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("本社区管理员不能给其他社区老人录入体征，且不生成记录或评估")
    void administratorCannotWriteVitalsAcrossCommunities() {
        VitalRecordMapper recordMapper = mock(VitalRecordMapper.class);
        AssessmentService assessmentService = mock(AssessmentService.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        when(elderMapper.selectById(2L)).thenReturn(elderProfile(2L, 2L));
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), familyMapper);
        VitalRecordServiceImpl service = new VitalRecordServiceImpl(recordMapper, assessmentService, policy);
        VitalRecordCreateDTO dto = new VitalRecordCreateDTO();
        dto.setElderId(2L);
        dto.setVitalType("BLOOD_PRESSURE");
        dto.setValuePrimary(new BigDecimal("130"));
        dto.setValueSecondary(new BigDecimal("80"));
        dto.setUnit("mmHg");
        dto.setSourceType("MANUAL");
        dto.setRecordedAt(LocalDateTime.now());
        UserContext.set(new CurrentUser("10", "13800000010", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.recordVital(dto));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(recordMapper, never()).insert(any(VitalRecord.class));
        verify(assessmentService, never()).evaluateVital(any());
    }

    @Test
    @DisplayName("本社区管理员不能读取其他社区体征趋势，且不查询体征记录")
    void administratorCannotReadVitalsAcrossCommunities() {
        VitalRecordMapper recordMapper = mock(VitalRecordMapper.class);
        AssessmentService assessmentService = mock(AssessmentService.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        when(elderMapper.selectById(2L)).thenReturn(elderProfile(2L, 2L));
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class));
        VitalRecordServiceImpl service = new VitalRecordServiceImpl(recordMapper, assessmentService, policy);
        VitalTrendQueryDTO query = new VitalTrendQueryDTO();
        query.setElderId(2L);
        query.setVitalType("BLOOD_PRESSURE");
        query.setDays(7);
        UserContext.set(new CurrentUser("10", "13800000010", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.queryTrend(query));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(recordMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("本社区管理员不能结案其他社区告警，且告警状态不变")
    void administratorCannotHandleAlertAcrossCommunities() {
        HealthAlertMapper alertMapper = mock(HealthAlertMapper.class);
        AlertTriggerEvidenceMapper evidenceMapper = mock(AlertTriggerEvidenceMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        VitalRecordMapper recordMapper = mock(VitalRecordMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        HealthAlert alert = new HealthAlert();
        alert.setAlertId(99L);
        alert.setElderId(2L);
        alert.setStatus("PROCESSING");
        when(alertMapper.selectById(99L)).thenReturn(alert);
        when(elderMapper.selectById(2L)).thenReturn(elderProfile(2L, 2L));
        HealthAlertServiceImpl service = new HealthAlertServiceImpl(alertMapper, evidenceMapper, elderMapper,
                recordMapper, userMapper,
                new ObjectAuthorizationPolicy(elderMapper, mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class)));
        AlertHandleDTO dto = new AlertHandleDTO();
        dto.setHandlingResult("已核实");
        UserContext.set(new CurrentUser("10", "13800000010", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.handleAlert(99L, dto));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(alertMapper, never()).updateById(any(HealthAlert.class));
    }

    private static CurrentUser elder(String userId, String communityId) {
        return new CurrentUser(userId, "13800000031", "ELDER", List.of("ELDER"), "ELDER", communityId);
    }

    private static ElderIdentityLink identityLink(Long userId, Long elderId) {
        ElderIdentityLink link = new ElderIdentityLink();
        link.setUserId(userId);
        link.setElderId(elderId);
        return link;
    }

    private static ElderProfile elderProfile(Long elderId, Long communityId) {
        ElderProfile elder = new ElderProfile();
        elder.setElderId(elderId);
        elder.setCommunityId(communityId);
        elder.setName("测试老人" + elderId);
        elder.setAddress("测试地址");
        elder.setStatus("ACTIVE");
        return elder;
    }
}
