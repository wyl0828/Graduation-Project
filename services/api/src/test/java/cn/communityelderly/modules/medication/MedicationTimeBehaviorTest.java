package cn.communityelderly.modules.medication;

import cn.communityelderly.common.config.ApplicationTimeConfig;
import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.medication.dto.MedicationPlanCreateDTO;
import cn.communityelderly.modules.medication.dto.MedicationPlanUpdateDTO;
import cn.communityelderly.modules.medication.dto.MedicationPlanVO;
import cn.communityelderly.modules.medication.dto.MedicationCheckinDTO;
import cn.communityelderly.modules.medication.dto.MedicationTaskVO;
import cn.communityelderly.modules.medication.entity.MedicationCheckin;
import cn.communityelderly.modules.medication.entity.MedicationPlan;
import cn.communityelderly.modules.medication.entity.MedicationPlanReminderTime;
import cn.communityelderly.modules.medication.entity.MedicationPlanVersion;
import cn.communityelderly.modules.medication.entity.MedicationTask;
import cn.communityelderly.modules.medication.mapper.MedicationCheckinMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanReminderTimeMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanVersionMapper;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.medication.service.impl.MedicationPlanServiceImpl;
import cn.communityelderly.modules.medication.service.impl.MedicationTaskServiceImpl;
import cn.communityelderly.modules.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;

class MedicationTimeBehaviorTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Long ELDER_ID = 88L;

    @Test
    void applicationClockUsesShanghaiZone() {
        assertEquals(SHANGHAI, new ApplicationTimeConfig().applicationClock().getZone());
    }

    @Test
    void taskGenerationUsesShanghaiDateAfterUtcMidnightBoundary() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-01T16:05:00Z"), SHANGHAI);
        MedicationPlan plan = plan(11L, "ACTIVE", 1);
        MedicationPlanVersion version = version(101L, 11L, 1, LocalDate.of(2026, 3, 1));
        TaskFixture fixture = taskFixture(clock, List.of(plan), List.of(), version,
                List.of(reminder(101L, "23:50")));

        List<MedicationTaskVO> tasks = fixture.service.getTodayTasks(ELDER_ID);

        assertEquals(1, tasks.size());
        assertEquals(LocalDate.of(2026, 3, 2), tasks.get(0).getTaskDate());
        assertEquals(LocalTime.of(23, 50), tasks.get(0).getScheduledTime());
        assertEquals(LocalDateTime.of(2026, 3, 2, 0, 5), fixture.insertedTasks.get(0).getCreatedAt());
    }

    @Test
    void taskGenerationFillsOnlyReminderSlotsMissingFromAnExistingDay() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T01:00:00Z"), SHANGHAI);
        MedicationPlan plan = plan(12L, "ACTIVE", 1);
        MedicationPlanVersion version = version(102L, 12L, 1, LocalDate.of(2026, 4, 1));
        MedicationTask existingMorning = task(12L, LocalDate.of(2026, 4, 10), "08:00");
        TaskFixture fixture = taskFixture(clock, List.of(plan), List.of(existingMorning), version,
                List.of(reminder(102L, "08:00"), reminder(102L, "20:00")));

        List<MedicationTaskVO> tasks = fixture.service.getTodayTasks(ELDER_ID);

        assertEquals(2, tasks.size());
        assertEquals(Set.of(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                tasks.stream().map(MedicationTaskVO::getScheduledTime).collect(Collectors.toSet()));
        assertEquals(1, fixture.insertedTasks.size());
        assertEquals(LocalTime.of(20, 0), fixture.insertedTasks.get(0).getScheduledTime());
        assertEquals(LocalDate.of(2026, 4, 10), fixture.insertedTasks.get(0).getTaskDate());
    }

    @Test
    void pausedDaysAreNotBackfilledAndAnActivePlanResumesOnTheCurrentDay() {
        MedicationPlan plan = plan(13L, "PAUSED", 1);
        MedicationPlanVersion version = version(103L, 13L, 1, LocalDate.of(2026, 4, 1));
        TaskFixture paused = taskFixture(Clock.fixed(Instant.parse("2026-04-11T02:00:00Z"), SHANGHAI),
                List.of(plan), List.of(), version, List.of(reminder(103L, "20:00")));

        assertEquals(0, paused.service.getTodayTasks(ELDER_ID).size());
        verify(paused.taskMapper, never()).insert(any(MedicationTask.class));

        plan.setStatus("ACTIVE");
        TaskFixture resumed = taskFixture(Clock.fixed(Instant.parse("2026-04-14T02:00:00Z"), SHANGHAI),
                List.of(plan), List.of(), version, List.of(reminder(103L, "20:00")));

        List<MedicationTaskVO> tasks = resumed.service.getTodayTasks(ELDER_ID);

        assertEquals(1, tasks.size());
        assertEquals(LocalDate.of(2026, 4, 14), tasks.get(0).getTaskDate());
        assertEquals(List.of(LocalDate.of(2026, 4, 14)), resumed.insertedTasks.stream()
                .map(MedicationTask::getTaskDate).toList());
    }

    @Test
    void inactiveElderKeepsExistingTasksWithoutBackfillOrNewCareNotification() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T05:30:00Z"), SHANGHAI);
        MedicationPlan plan = plan(15L, "ACTIVE", 1);
        MedicationPlanVersion version = version(105L, 15L, 1, LocalDate.of(2026, 4, 1));
        MedicationTask existingMorning = task(15L, LocalDate.of(2026, 4, 10), "09:00");
        ElderProfile inactiveElder = elder();
        inactiveElder.setStatus("INACTIVE");
        TaskFixture fixture = taskFixture(clock, List.of(plan), List.of(existingMorning), version,
                List.of(reminder(105L, "09:00"), reminder(105L, "20:00")), inactiveElder);
        ElderProfile staleAuthorizationProfile = elder();
        when(fixture.policy.requireElderAccess(ELDER_ID)).thenReturn(staleAuthorizationProfile);

        List<MedicationTaskVO> tasks = fixture.service.getTodayTasks(ELDER_ID);

        assertEquals(1, tasks.size());
        assertEquals("OVER_WINDOW_UNCONFIRMED", tasks.get(0).getStatus());
        assertEquals("OVER_WINDOW_UNCONFIRMED", existingMorning.getStatus());
        assertEquals(0, fixture.insertedTasks.size());
        verify(fixture.elderProfileMapper).selectByIdForUpdate(ELDER_ID);
        verify(fixture.planMapper, never()).selectList(any());
        verify(fixture.familyMapper, never()).selectList(any());
        verify(fixture.notificationService, never()).publishEvent(any(NotificationCreateEventDTO.class));
        InOrder lockBeforeTaskRead = inOrder(fixture.elderProfileMapper, fixture.taskMapper);
        lockBeforeTaskRead.verify(fixture.elderProfileMapper).selectByIdForUpdate(ELDER_ID);
        lockBeforeTaskRead.verify(fixture.taskMapper).selectList(any());
    }

    @Test
    void inactiveArchiveRejectsNewPlanBeforeWritingAnyPlanOrTaskRows() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T01:00:00Z"), SHANGHAI);
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        ObjectAuthorizationPolicy policy = mock(ObjectAuthorizationPolicy.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
        ElderProfile inactiveElder = elder();
        inactiveElder.setStatus("INACTIVE");
        when(policy.requireElderAccess(ELDER_ID)).thenReturn(elder());
        when(elderProfileMapper.selectByIdForUpdate(ELDER_ID)).thenReturn(inactiveElder);
        when(policy.requireCurrentUser()).thenReturn(childUser());
        doAnswer(invocation -> {
            ((MedicationPlan) invocation.getArgument(0)).setPlanId(150L);
            return 1;
        }).when(planMapper).insert(any(MedicationPlan.class));
        doAnswer(invocation -> {
            ((MedicationPlanVersion) invocation.getArgument(0)).setVersionId(1501L);
            return 1;
        }).when(versionMapper).insert(any(MedicationPlanVersion.class));
        when(planMapper.selectById(150L)).thenReturn(plan(150L, "ACTIVE", 1));
        when(versionMapper.selectOne(any())).thenReturn(version(1501L, 150L, 1,
                LocalDate.of(2026, 4, 10)));
        when(reminderMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>());

        MedicationPlanCreateDTO dto = new MedicationPlanCreateDTO();
        dto.setElderId(ELDER_ID);
        dto.setMedicineName("测试药品");
        dto.setDosage("1片");
        dto.setRefreshTodayPending(true);
        MedicationPlanCreateDTO.ReminderTimeItem reminder = new MedicationPlanCreateDTO.ReminderTimeItem();
        reminder.setReminderTime("20:00");
        dto.setReminderTimes(List.of(reminder));

        MedicationPlanServiceImpl service = planService(clock, planMapper, versionMapper, reminderMapper,
                taskMapper, policy, userMapper, elderProfileMapper);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createPlan(dto));

        verify(planMapper, never()).insert(any(MedicationPlan.class));
        verify(versionMapper, never()).insert(any(MedicationPlanVersion.class));
        verify(reminderMapper, never()).insert(any(MedicationPlanReminderTime.class));
        verify(taskMapper, never()).insert(any(MedicationTask.class));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(elderProfileMapper).selectByIdForUpdate(ELDER_ID);
    }

    @Test
    void inactiveArchiveRejectsTodayRefreshBeforeChangingPlanVersionOrTasks() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T01:00:00Z"), SHANGHAI);
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        ObjectAuthorizationPolicy policy = mock(ObjectAuthorizationPolicy.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
        MedicationPlan plan = plan(16L, "ACTIVE", 1);
        MedicationPlanVersion oldVersion = version(1601L, 16L, 1, LocalDate.of(2026, 4, 1));
        MedicationPlanVersion newVersion = version(1602L, 16L, 2, LocalDate.of(2026, 4, 10));
        ElderProfile inactiveElder = elder();
        inactiveElder.setStatus("INACTIVE");
        when(planMapper.selectById(16L)).thenReturn(plan);
        when(policy.requireElderAccess(ELDER_ID)).thenReturn(elder());
        when(elderProfileMapper.selectByIdForUpdate(ELDER_ID)).thenReturn(inactiveElder);
        when(policy.requireCurrentUser()).thenReturn(childUser());
        when(versionMapper.selectOne(any())).thenReturn(oldVersion, newVersion);
        doAnswer(invocation -> {
            ((MedicationPlanVersion) invocation.getArgument(0)).setVersionId(1602L);
            return 1;
        }).when(versionMapper).insert(any(MedicationPlanVersion.class));
        when(taskMapper.selectList(any())).thenReturn(List.of(task(16L, LocalDate.of(2026, 4, 10), "09:00")));
        when(reminderMapper.selectList(any())).thenReturn(List.of());

        MedicationPlanUpdateDTO dto = new MedicationPlanUpdateDTO();
        dto.setChangeReason("调整剂量");
        dto.setRefreshTodayPending(true);
        MedicationPlanCreateDTO.ReminderTimeItem reminder = new MedicationPlanCreateDTO.ReminderTimeItem();
        reminder.setReminderTime("20:00");
        dto.setReminderTimes(List.of(reminder));
        MedicationPlanServiceImpl service = planService(clock, planMapper, versionMapper, reminderMapper,
                taskMapper, policy, userMapper, elderProfileMapper);

        BusinessException error = assertThrows(BusinessException.class, () -> service.updatePlan(16L, dto));

        verify(versionMapper, never()).updateById(any(MedicationPlanVersion.class));
        verify(versionMapper, never()).insert(any(MedicationPlanVersion.class));
        verify(reminderMapper, never()).insert(any(MedicationPlanReminderTime.class));
        verify(planMapper, never()).updateById(any(MedicationPlan.class));
        verify(taskMapper, never()).deleteById(any(Long.class));
        verify(taskMapper, never()).insert(any(MedicationTask.class));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        verify(elderProfileMapper).selectByIdForUpdate(ELDER_ID);
    }

    @Test
    void inactiveArchiveAllowsExistingPlanUpdateWhenItDoesNotRefreshToday() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T01:00:00Z"), SHANGHAI);
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        ObjectAuthorizationPolicy policy = mock(ObjectAuthorizationPolicy.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
        MedicationPlan plan = plan(17L, "ACTIVE", 1);
        MedicationPlanVersion oldVersion = version(1701L, 17L, 1, LocalDate.of(2026, 4, 1));
        MedicationPlanVersion newVersion = version(1702L, 17L, 2, LocalDate.of(2026, 4, 11));
        ElderProfile inactiveElder = elder();
        inactiveElder.setStatus("INACTIVE");
        when(planMapper.selectById(17L)).thenReturn(plan);
        when(policy.requireElderAccess(ELDER_ID)).thenReturn(elder());
        when(elderProfileMapper.selectByIdForUpdate(ELDER_ID)).thenReturn(inactiveElder);
        when(policy.requireCurrentUser()).thenReturn(childUser());
        when(versionMapper.selectOne(any())).thenReturn(oldVersion, newVersion);
        doAnswer(invocation -> {
            ((MedicationPlanVersion) invocation.getArgument(0)).setVersionId(1702L);
            return 1;
        }).when(versionMapper).insert(any(MedicationPlanVersion.class));
        when(reminderMapper.selectList(any())).thenReturn(List.of());

        MedicationPlanUpdateDTO dto = new MedicationPlanUpdateDTO();
        dto.setDosage("半片");
        dto.setChangeReason("按医嘱调整");
        dto.setRefreshTodayPending(false);
        dto.setReminderTimes(List.of());
        MedicationPlanServiceImpl service = planService(clock, planMapper, versionMapper, reminderMapper,
                taskMapper, policy, userMapper, elderProfileMapper);

        MedicationPlanVO updated = service.updatePlan(17L, dto);

        assertEquals(2, updated.getCurrentVersionNo());
        assertEquals(LocalDate.of(2026, 4, 10), oldVersion.getEndDate());
        assertEquals(LocalDate.of(2026, 4, 11), newVersion.getStartDate());
        assertEquals("半片", plan.getDosage());
        verify(versionMapper).updateById(oldVersion);
        ArgumentCaptor<MedicationPlanVersion> insertedVersion = ArgumentCaptor.forClass(MedicationPlanVersion.class);
        verify(versionMapper).insert(insertedVersion.capture());
        assertEquals(17L, insertedVersion.getValue().getPlanId());
        assertEquals(2, insertedVersion.getValue().getVersionNo());
        assertEquals("半片", insertedVersion.getValue().getDosage());
        assertEquals(LocalDate.of(2026, 4, 11), insertedVersion.getValue().getStartDate());
        assertEquals("按医嘱调整", insertedVersion.getValue().getChangeReason());
        assertEquals(4L, insertedVersion.getValue().getCreatedByUserId());
        assertEquals(LocalDateTime.of(2026, 4, 10, 9, 0), insertedVersion.getValue().getCreatedAt());
        verify(taskMapper, never()).deleteById(any(Long.class));
        verify(taskMapper, never()).insert(any(MedicationTask.class));
        verify(elderProfileMapper).selectByIdForUpdate(ELDER_ID);
    }

    @Test
    void checkinLocksElderBeforeWritingCheckinOrTaskStatus() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T01:00:00Z"), SHANGHAI);
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        MedicationCheckinMapper checkinMapper = mock(MedicationCheckinMapper.class);
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
        ObjectAuthorizationPolicy policy = mock(ObjectAuthorizationPolicy.class);
        NotificationService notificationService = mock(NotificationService.class);
        MedicationTask existingTask = task(18L, LocalDate.of(2026, 4, 10), "09:00");
        existingTask.setTaskId(1801L);
        when(taskMapper.selectById(1801L)).thenReturn(existingTask);
        when(policy.requireElderAccess(ELDER_ID)).thenReturn(elder());
        when(elderProfileMapper.selectByIdForUpdate(ELDER_ID)).thenReturn(elder());

        MedicationTaskServiceImpl service = new MedicationTaskServiceImpl(taskMapper, checkinMapper, planMapper,
                versionMapper, reminderMapper, familyMapper, elderProfileMapper, policy, notificationService, clock);
        MedicationCheckinDTO dto = new MedicationCheckinDTO();
        dto.setTaskId(1801L);
        dto.setIdempotencyKey("checkin-1801");

        MedicationTaskVO result = service.checkin(dto);

        assertEquals("CONFIRMED", result.getStatus());
        InOrder lockBeforeWrites = inOrder(elderProfileMapper, checkinMapper, taskMapper);
        lockBeforeWrites.verify(elderProfileMapper).selectByIdForUpdate(ELDER_ID);
        lockBeforeWrites.verify(checkinMapper).insert(any(MedicationCheckin.class));
        lockBeforeWrites.verify(taskMapper).updateById(existingTask);
    }

    @Test
    void updatingPlanClosesOldVersionImmediatelyBeforeNewVersionStarts() {
        assertVersionUpdate(false, LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 11));
        assertVersionUpdate(true, LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 10));
    }

    private void assertVersionUpdate(boolean refreshToday, LocalDate expectedOldEndDate,
                                     LocalDate expectedNewStartDate) {
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T01:00:00Z"), SHANGHAI);
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        ObjectAuthorizationPolicy policy = mock(ObjectAuthorizationPolicy.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);

        MedicationPlan plan = plan(14L, "ACTIVE", 1);
        plan.setDosage("1片");
        plan.setUsageMethod("口服");
        MedicationPlanVersion oldVersion = version(104L, 14L, 1, LocalDate.of(2026, 4, 1));
        MedicationPlanVersion newVersion = version(105L, 14L, 2, expectedNewStartDate);
        when(planMapper.selectById(14L)).thenReturn(plan);
        when(policy.requireElderAccess(ELDER_ID)).thenReturn(elder());
        when(policy.requireCurrentUser()).thenReturn(new CurrentUser(
                "4", "13800000004", "FAMILY", List.of("CHILD"), "CHILD", "1"));
        when(versionMapper.selectOne(any())).thenReturn(oldVersion, newVersion);
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>());
        doAnswer(invocation -> {
            MedicationPlanVersion inserted = invocation.getArgument(0);
            inserted.setVersionId(105L);
            return 1;
        }).when(versionMapper).insert(any(MedicationPlanVersion.class));
        when(reminderMapper.selectList(any())).thenReturn(List.of());

        MedicationPlanUpdateDTO dto = new MedicationPlanUpdateDTO();
        dto.setChangeReason("按医嘱调整");
        dto.setRefreshTodayPending(refreshToday);
        MedicationPlanCreateDTO.ReminderTimeItem time = new MedicationPlanCreateDTO.ReminderTimeItem();
        time.setReminderTime("20:00");
        dto.setReminderTimes(List.of(time));

        MedicationPlanServiceImpl service = planService(clock, planMapper, versionMapper, reminderMapper,
                taskMapper, policy, userMapper);
        MedicationPlanVO updated = service.updatePlan(14L, dto);

        assertEquals(2, updated.getCurrentVersionNo());
        assertEquals(expectedOldEndDate, oldVersion.getEndDate());
        assertEquals(expectedNewStartDate, newVersion.getStartDate());
        assertEquals(LocalDateTime.of(2026, 4, 10, 9, 0), plan.getUpdatedAt());
    }

    private static TaskFixture taskFixture(Clock clock, List<MedicationPlan> plans,
                                          List<MedicationTask> persistedTasks,
                                          MedicationPlanVersion version,
                                          List<MedicationPlanReminderTime> reminders) {
        return taskFixture(clock, plans, persistedTasks, version, reminders, elder());
    }

    private static TaskFixture taskFixture(Clock clock, List<MedicationPlan> plans,
                                          List<MedicationTask> persistedTasks,
                                          MedicationPlanVersion version,
                                          List<MedicationPlanReminderTime> reminders,
                                          ElderProfile elder) {
        MedicationTaskMapper taskMapper = mock(MedicationTaskMapper.class);
        MedicationCheckinMapper checkinMapper = mock(MedicationCheckinMapper.class);
        MedicationPlanMapper planMapper = mock(MedicationPlanMapper.class);
        MedicationPlanVersionMapper versionMapper = mock(MedicationPlanVersionMapper.class);
        MedicationPlanReminderTimeMapper reminderMapper = mock(MedicationPlanReminderTimeMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        ObjectAuthorizationPolicy policy = mock(ObjectAuthorizationPolicy.class);
        NotificationService notificationService = mock(NotificationService.class);
        ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
        List<MedicationTask> insertedTasks = new ArrayList<>();

        when(policy.requireElderAccess(ELDER_ID)).thenReturn(elder);
        when(elderProfileMapper.selectByIdForUpdate(ELDER_ID)).thenReturn(elder);
        ElderFamilyLink activeFamilyLink = new ElderFamilyLink();
        activeFamilyLink.setElderId(ELDER_ID);
        activeFamilyLink.setChildUserId(99L);
        activeFamilyLink.setStatus("ACTIVE");
        when(familyMapper.selectList(any())).thenReturn(List.of(activeFamilyLink));
        when(taskMapper.selectList(any())).thenReturn(new ArrayList<>(persistedTasks));
        List<MedicationPlan> activePlans = plans.stream()
                .filter(plan -> "ACTIVE".equalsIgnoreCase(plan.getStatus()))
                .collect(Collectors.toCollection(ArrayList::new));
        when(planMapper.selectList(any())).thenReturn(activePlans);
        when(versionMapper.selectOne(any())).thenReturn(version);
        when(reminderMapper.selectList(any())).thenReturn(reminders);
        doAnswer(invocation -> {
            insertedTasks.add(invocation.getArgument(0));
            return 1;
        }).when(taskMapper).insert(any(MedicationTask.class));

        MedicationTaskServiceImpl service = new MedicationTaskServiceImpl(taskMapper, checkinMapper, planMapper,
                versionMapper, reminderMapper, familyMapper, elderProfileMapper, policy, notificationService, clock);
        return new TaskFixture(service, taskMapper, planMapper, familyMapper, elderProfileMapper, policy,
                notificationService, insertedTasks);
    }

    private static MedicationPlanServiceImpl planService(Clock clock, MedicationPlanMapper planMapper,
                                                          MedicationPlanVersionMapper versionMapper,
                                                          MedicationPlanReminderTimeMapper reminderMapper,
                                                          MedicationTaskMapper taskMapper,
                                                          ObjectAuthorizationPolicy policy,
                                                          UserAccountMapper userMapper) {
        ElderProfileMapper elderProfileMapper = mock(ElderProfileMapper.class);
        when(elderProfileMapper.selectByIdForUpdate(any(Long.class))).thenReturn(elder());
        return new MedicationPlanServiceImpl(planMapper, versionMapper, reminderMapper, taskMapper,
                policy, userMapper, elderProfileMapper, clock);
    }

    private static MedicationPlanServiceImpl planService(Clock clock, MedicationPlanMapper planMapper,
                                                          MedicationPlanVersionMapper versionMapper,
                                                          MedicationPlanReminderTimeMapper reminderMapper,
                                                          MedicationTaskMapper taskMapper,
                                                          ObjectAuthorizationPolicy policy,
                                                          UserAccountMapper userMapper,
                                                          ElderProfileMapper elderProfileMapper) {
        return new MedicationPlanServiceImpl(planMapper, versionMapper, reminderMapper, taskMapper,
                policy, userMapper, elderProfileMapper, clock);
    }

    private static CurrentUser childUser() {
        return new CurrentUser("4", "13800000004", "FAMILY", List.of("CHILD"), "CHILD", "1");
    }

    private static MedicationPlan plan(Long id, String status, int versionNo) {
        MedicationPlan plan = new MedicationPlan();
        plan.setPlanId(id);
        plan.setElderId(ELDER_ID);
        plan.setMedicineName("测试药品");
        plan.setDosage("1片");
        plan.setUsageMethod("口服");
        plan.setStatus(status);
        plan.setCurrentVersionNo(versionNo);
        return plan;
    }

    private static MedicationPlanVersion version(Long id, Long planId, int versionNo, LocalDate startDate) {
        MedicationPlanVersion version = new MedicationPlanVersion();
        version.setVersionId(id);
        version.setPlanId(planId);
        version.setVersionNo(versionNo);
        version.setStartDate(startDate);
        version.setDosage("1片");
        return version;
    }

    private static MedicationPlanReminderTime reminder(Long versionId, String time) {
        MedicationPlanReminderTime reminder = new MedicationPlanReminderTime();
        reminder.setVersionId(versionId);
        reminder.setReminderTime(LocalTime.parse(time));
        reminder.setTimeLabel("常规服药");
        return reminder;
    }

    private static MedicationTask task(Long planId, LocalDate date, String scheduledTime) {
        MedicationTask task = new MedicationTask();
        task.setTaskId(1001L);
        task.setElderId(ELDER_ID);
        task.setPlanId(planId);
        task.setVersionNo(1);
        task.setTaskDate(date);
        task.setScheduledTime(LocalTime.parse(scheduledTime));
        task.setMedicineName("测试药品");
        task.setDosage("1片");
        task.setStatus("PENDING");
        return task;
    }

    private static ElderProfile elder() {
        ElderProfile elder = new ElderProfile();
        elder.setElderId(ELDER_ID);
        elder.setName("测试长辈");
        elder.setStatus("ACTIVE");
        return elder;
    }

    private record TaskFixture(MedicationTaskServiceImpl service, MedicationTaskMapper taskMapper,
                               MedicationPlanMapper planMapper, ElderFamilyLinkMapper familyMapper,
                               ElderProfileMapper elderProfileMapper, ObjectAuthorizationPolicy policy,
                               NotificationService notificationService,
                               List<MedicationTask> insertedTasks) {
    }
}
