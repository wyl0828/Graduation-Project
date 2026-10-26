package cn.communityelderly.modules.medication;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.elder.dto.BoundElderVO;
import cn.communityelderly.modules.elder.dto.ElderArchiveVO;
import cn.communityelderly.modules.elder.dto.ElderCreateDTO;
import cn.communityelderly.modules.elder.dto.KinshipApplyDTO;
import cn.communityelderly.modules.elder.dto.KinshipFreezeDTO;
import cn.communityelderly.modules.elder.dto.KinshipReviewDTO;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.elder.service.KinshipService;
import cn.communityelderly.modules.medication.dto.*;
import cn.communityelderly.modules.medication.entity.MedicationTask;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import cn.communityelderly.modules.medication.service.MedicationPlanService;
import cn.communityelderly.modules.medication.service.MedicationTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class MedicationPlanAndTaskIntegrationTest {

    @Autowired
    private MedicationPlanService planService;

    @Autowired
    private MedicationTaskService taskService;

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private KinshipService kinshipService;

    @Autowired
    private MedicationTaskMapper taskMapper;

    @Autowired
    private ElderIdentityLinkMapper identityLinkMapper;

    private CurrentUser adminUser;
    private CurrentUser childUser;
    private CurrentUser elderUser;
    private Long elderId;

    @BeforeEach
    void setUp() {
        adminUser = new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        childUser = new CurrentUser("4", "13800000004", "FAMILY", List.of("CHILD"), "CHILD", "1");
        elderUser = new CurrentUser("3", "13800000003", "ELDER", List.of("ELDER"), "ELDER", "1");

        UserContext.set(adminUser);
        ElderCreateDTO createDTO = new ElderCreateDTO();
        createDTO.setName("沈老伯");
        createDTO.setSex("MALE");
        createDTO.setBirthDate(LocalDate.of(1947, 4, 10));
        createDTO.setAddress("南京市玄武区锁金村8幢302");
        createDTO.setContactPhone("13966554433");
        createDTO.setEmergencyContactName("沈建平");
        createDTO.setEmergencyContactPhone("13800000004");
        createDTO.setEmergencyContactRelation("儿子");

        ElderArchiveVO elder = elderArchiveService.createElder(createDTO);
        elderId = elder.getElderId();

        // 本用例的老人口令账号(user 3)需要绑定到本次新建的老人档案，服务端只认会话身份
        ElderIdentityLink identity = identityLinkMapper.selectOne(
                new LambdaQueryWrapper<ElderIdentityLink>()
                        .eq(ElderIdentityLink::getUserId, 3L));
        if (identity == null) {
            identity = new ElderIdentityLink();
            identity.setUserId(3L);
            identity.setElderId(elderId);
            identity.setLinkedAt(LocalDateTime.now());
            identityLinkMapper.insert(identity);
        } else {
            identity.setElderId(elderId);
            identityLinkMapper.updateById(identity);
        }

        // 建立生效的亲情绑定
        UserContext.set(childUser);
        KinshipApplyDTO applyDTO = new KinshipApplyDTO();
        applyDTO.setElderId(elderId);
        applyDTO.setRelationship("儿子");
        applyDTO.setApplyReason("日常监护长辈用药");
        Long appId = kinshipService.applyKinship(applyDTO);

        UserContext.set(adminUser);
        KinshipReviewDTO reviewDTO = new KinshipReviewDTO();
        reviewDTO.setApproved(true);
        reviewDTO.setDecisionReason("户籍证明核对无误");
        kinshipService.reviewApplication(appId, reviewDTO);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("AT-23: 用药计划创建(v1)、调整版本自增(v2)与当日应急刷新流转")
    void testMedicationPlanVersioningAndTodayEmergencyRefresh() {
        UserContext.set(childUser);

        // 1. 创建降压药用药计划 (v1, 当日应急生效并生成今日任务)
        MedicationPlanCreateDTO createDTO = new MedicationPlanCreateDTO();
        createDTO.setElderId(elderId);
        createDTO.setMedicineName("苯磺酸氨氯地平片");
        createDTO.setDosage("5mg 1片");
        createDTO.setUsageMethod("早餐后温水送服");
        createDTO.setCategoryTag("降压药");

        MedicationPlanCreateDTO.ReminderTimeItem morningTime = new MedicationPlanCreateDTO.ReminderTimeItem();
        morningTime.setReminderTime("08:00");
        morningTime.setTimeLabel("早餐后");
        createDTO.setReminderTimes(List.of(morningTime));
        createDTO.setRefreshTodayPending(true); // 当日立即生效

        MedicationPlanVO plan = planService.createPlan(createDTO);
        assertNotNull(plan);
        assertEquals(1, plan.getCurrentVersionNo());
        assertEquals("降压药", plan.getCategoryTag());

        // 验证今日任务已生成
        List<MedicationTask> todayTasks = taskMapper.selectList(
                new LambdaQueryWrapper<MedicationTask>()
                        .eq(MedicationTask::getPlanId, plan.getPlanId())
                        .eq(MedicationTask::getTaskDate, LocalDate.now())
        );
        assertEquals(1, todayTasks.size());
        assertEquals("PENDING", todayTasks.get(0).getStatus());

        // 2. 调整方案：遵医嘱调整为每日早晚两次 (v2，勾选立即刷新今日未开始时段)
        MedicationPlanUpdateDTO updateDTO = new MedicationPlanUpdateDTO();
        updateDTO.setDosage("5mg 半片");
        updateDTO.setChangeReason("遵医嘱平稳减量，改为早晚各一次");
        updateDTO.setRefreshTodayPending(true);

        MedicationPlanCreateDTO.ReminderTimeItem eveningTime = new MedicationPlanCreateDTO.ReminderTimeItem();
        eveningTime.setReminderTime("19:00");
        eveningTime.setTimeLabel("晚餐后");
        updateDTO.setReminderTimes(List.of(morningTime, eveningTime));

        MedicationPlanVO updatedPlan = planService.updatePlan(plan.getPlanId(), updateDTO);
        assertEquals(2, updatedPlan.getCurrentVersionNo());
        assertEquals("5mg 半片", updatedPlan.getDosage());

        // 验证历史版本追溯 (应有 v1, v2 两条不可变版本)
        List<MedicationPlanVersionVO> versions = planService.listPlanVersions(plan.getPlanId());
        assertEquals(2, versions.size());

        // 验证今日任务已被原子替换为新版本（早晚共2次待服任务）
        List<MedicationTask> refreshedTasks = taskMapper.selectList(
                new LambdaQueryWrapper<MedicationTask>()
                        .eq(MedicationTask::getPlanId, plan.getPlanId())
                        .eq(MedicationTask::getTaskDate, LocalDate.now())
        );
        assertEquals(2, refreshedTasks.size());
        assertTrue(refreshedTasks.stream().allMatch(t -> t.getVersionNo().equals(2)));
    }

    @Test
    @DisplayName("AT-24: 老人端今日药盒打卡确认与幂等防重拦截")
    void testElderMedicationCheckinAndIdempotency() {
        UserContext.set(childUser);
        // 先建立一个今日晚间服药计划 (20:00 晚于当前时间，处于待服用 PENDING 态)
        MedicationPlanCreateDTO createDTO = new MedicationPlanCreateDTO();
        createDTO.setElderId(elderId);
        createDTO.setMedicineName("二甲双胍缓释片");
        createDTO.setDosage("0.5g 1片");
        createDTO.setCategoryTag("降糖药");
        MedicationPlanCreateDTO.ReminderTimeItem tItem = new MedicationPlanCreateDTO.ReminderTimeItem();
        tItem.setReminderTime("20:00");
        tItem.setTimeLabel("晚餐后");
        createDTO.setReminderTimes(List.of(tItem));
        createDTO.setRefreshTodayPending(true);
        MedicationPlanVO plan = planService.createPlan(createDTO);

        // 老人端查看今日任务
        UserContext.set(elderUser);
        List<MedicationTaskVO> tasks = taskService.getTodayTasks(elderId);
        assertFalse(tasks.isEmpty());
        MedicationTaskVO targetTask = tasks.get(0);
        assertEquals("PENDING", targetTask.getStatus());
        assertEquals("降糖药", targetTask.getCategoryTag());

        // 老人执行一键打卡
        String idempKey = UUID.randomUUID().toString();
        MedicationCheckinDTO checkinDTO = new MedicationCheckinDTO();
        checkinDTO.setTaskId(targetTask.getTaskId());
        checkinDTO.setCheckinMode("SELF_CONFIRMED");
        checkinDTO.setIdempotencyKey(idempKey);

        MedicationTaskVO confirmed = taskService.checkin(checkinDTO);
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertNotNull(confirmed.getConfirmedAt());

        // 幂等防重：重复提交相同 idempotencyKey 返回已完成且不报错
        MedicationTaskVO duplicate = taskService.checkin(checkinDTO);
        assertEquals("CONFIRMED", duplicate.getStatus());
    }

    @Test
    @DisplayName("AT-25: 亲情争议冻结(FROZEN)后子女端编辑用药计划被拦截403")
    void testKinshipFrozenBlocksMedicationEdit() {
        UserContext.set(childUser);
        MedicationPlanCreateDTO createDTO = new MedicationPlanCreateDTO();
        createDTO.setElderId(elderId);
        createDTO.setMedicineName("阿司匹林肠溶片");
        createDTO.setDosage("100mg 1片");
        MedicationPlanCreateDTO.ReminderTimeItem tItem = new MedicationPlanCreateDTO.ReminderTimeItem();
        tItem.setReminderTime("08:00");
        createDTO.setReminderTimes(List.of(tItem));
        MedicationPlanVO plan = planService.createPlan(createDTO);

        // 管理员触发亲情争议冻结
        UserContext.set(childUser);
        List<BoundElderVO> boundList = kinshipService.listBoundElders(4L);
        BoundElderVO boundElder = boundList.stream().filter(e -> e.getElderId().equals(elderId)).findFirst().orElseThrow();
        UserContext.set(adminUser);
        KinshipFreezeDTO freezeDTO = new KinshipFreezeDTO();
        freezeDTO.setFreezeReasonCode("FAMILY_DISPUTE");
        freezeDTO.setFreezeReasonText("家属纠纷核实期间暂冻结用药权限");
        kinshipService.freezeKinship(boundElder.getLinkId(), freezeDTO);

        // 子女再次尝试调整用药计划，应抛出 KINSHIP_NOT_ACTIVE
        UserContext.set(childUser);
        MedicationPlanUpdateDTO updateDTO = new MedicationPlanUpdateDTO();
        updateDTO.setDosage("100mg 2片");
        updateDTO.setChangeReason("家属自主加量");
        updateDTO.setReminderTimes(List.of(tItem));

        BusinessException ex = assertThrows(BusinessException.class, () -> planService.updatePlan(plan.getPlanId(), updateDTO));
        assertEquals(ErrorCode.KINSHIP_NOT_ACTIVE, ex.getErrorCode());
    }
}
