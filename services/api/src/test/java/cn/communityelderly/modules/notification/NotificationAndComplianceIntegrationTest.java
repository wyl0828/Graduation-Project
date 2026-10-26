package cn.communityelderly.modules.notification;

import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.elder.dto.ElderArchiveVO;
import cn.communityelderly.modules.elder.dto.ElderCreateDTO;
import cn.communityelderly.modules.elder.dto.KinshipApplyDTO;
import cn.communityelderly.modules.elder.dto.KinshipReviewDTO;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.elder.service.KinshipService;
import cn.communityelderly.modules.medication.dto.MedicationComplianceVO;
import cn.communityelderly.modules.medication.entity.MedicationPlan;
import cn.communityelderly.modules.medication.entity.MedicationTask;
import cn.communityelderly.modules.medication.mapper.MedicationPlanMapper;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import cn.communityelderly.modules.medication.service.MedicationComplianceService;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.dto.NotificationItemVO;
import cn.communityelderly.modules.notification.dto.NotificationQueryDTO;
import cn.communityelderly.modules.notification.entity.NotificationEvent;
import cn.communityelderly.modules.notification.mapper.NotificationEventMapper;
import cn.communityelderly.modules.notification.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class NotificationAndComplianceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MedicationComplianceService complianceService;

    @Autowired
    private cn.communityelderly.modules.medication.service.MedicationPlanService planService;

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private KinshipService kinshipService;

    @Autowired
    private MedicationTaskMapper taskMapper;

    @Autowired
    private MedicationPlanMapper planMapper;

    @Autowired
    private NotificationEventMapper eventMapper;

    private CurrentUser adminUser;
    private CurrentUser childUser;
    private Long elderId;

    @BeforeEach
    void setUp() {
        adminUser = new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        childUser = new CurrentUser("4", "13800000004", "FAMILY", List.of("CHILD"), "CHILD", "1");

        UserContext.set(adminUser);
        ElderCreateDTO createDTO = new ElderCreateDTO();
        createDTO.setName("梁大爷");
        createDTO.setSex("MALE");
        createDTO.setBirthDate(LocalDate.of(1946, 7, 20));
        createDTO.setAddress("南京市玄武区锁金村12幢101");
        createDTO.setContactPhone("13988776655");
        createDTO.setEmergencyContactName("梁小红");
        createDTO.setEmergencyContactPhone("13800000004");
        createDTO.setEmergencyContactRelation("女儿");

        ElderArchiveVO elder = elderArchiveService.createElder(createDTO);
        elderId = elder.getElderId();

        // 建立生效的亲情绑定，使子女可以读取该老人的依从率
        UserContext.set(childUser);
        KinshipApplyDTO applyDTO = new KinshipApplyDTO();
        applyDTO.setElderId(elderId);
        applyDTO.setRelationship("女儿");
        applyDTO.setApplyReason("日常看护长辈服药");
        Long appId = kinshipService.applyKinship(applyDTO);

        UserContext.set(adminUser);
        KinshipReviewDTO reviewDTO = new KinshipReviewDTO();
        reviewDTO.setApproved(true);
        reviewDTO.setDecisionReason("核验通过");
        kinshipService.reviewApplication(appId, reviewDTO);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("AT-26: 逻辑通知事件去重防骚扰与子女端已读流转")
    void testNotificationDedupAndReadFlow() {
        // 1. 发布一条超时未服关怀提醒
        NotificationCreateEventDTO notify = new NotificationCreateEventDTO();
        notify.setPolicyScope("N01_BUSINESS");
        notify.setEventType("MEDICATION_REMINDER");
        notify.setSourceEntityType("MEDICATION_TASK");
        notify.setSourceEntityId("9999");
        notify.setDedupKey("NOTIFY_MED_TIMEOUT:9999:2026-10-25");
        notify.setTitle("【服药超时关怀】梁大爷今日降压药尚未打卡");
        notify.setContent("长辈计划于 08:00 服用苯磺酸氨氯地平片，目前已超时未打卡，请电话提醒。");
        notify.setRecipientUserIds(List.of(4L)); // 投递给子女 4

        Long eventId = notificationService.publishEvent(notify);
        assertNotNull(eventId);

        // 2. 幂等去重测试：相同 dedupKey 重复发送，不会产生重复事件
        Long dupEventId = notificationService.publishEvent(notify);
        assertEquals(eventId, dupEventId);
        Long count = eventMapper.selectCount(
                new LambdaQueryWrapper<NotificationEvent>()
                        .eq(NotificationEvent::getDedupKey, notify.getDedupKey())
        );
        assertEquals(1, count);

        // 3. 子女端查看通知与未读数量
        UserContext.set(childUser);
        Long unreadCount = notificationService.countUnread();
        assertTrue(unreadCount >= 1);

        NotificationQueryDTO query = new NotificationQueryDTO();
        query.setType("MEDICATION_REMINDER");
        List<NotificationItemVO> list = notificationService.listMyNotifications(query);
        assertFalse(list.isEmpty());
        NotificationItemVO item = list.get(0);
        assertFalse(item.getReadStatus());

        // 4. 标为已读
        notificationService.markAsRead(item.getDeliveryId());
        Long afterUnread = notificationService.countUnread();
        assertEquals(unreadCount - 1, afterUnread);
    }

    @Test
    @DisplayName("AT-27: 近30天服药遵从率算法统计准确性")
    void testMedicationComplianceRateCalculation() {
        LocalDate today = LocalDate.now();

        // 1. 先建立一个合法的用药计划满足外键约束 (直接落库，本用例只验证依从率算法)
        MedicationPlan plan = new MedicationPlan();
        plan.setElderId(elderId);
        plan.setMedicineName("降压药");
        plan.setDosage("5mg");
        plan.setUsageMethod("口服");
        plan.setStatus("ACTIVE");
        plan.setCurrentVersionNo(1);
        plan.setCreatedByUserId(4L);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        planMapper.insert(plan);

        // 2. 预置 10 条历史服药任务：9 条已确认，1 条漏服
        for (int i = 1; i <= 10; i++) {
            MedicationTask t = new MedicationTask();
            t.setElderId(elderId);
            t.setPlanId(plan.getPlanId());
            t.setVersionNo(1);
            t.setTaskDate(today.minusDays(i));
            t.setScheduledTime(LocalTime.of(8, 0));
            t.setMedicineName("降压药");
            t.setDosage("5mg");
            if (i < 10) {
                t.setStatus("CONFIRMED");
                t.setConfirmedAt(LocalDateTime.now().minusDays(i));
            } else {
                t.setStatus("OVER_WINDOW_UNCONFIRMED");
            }
            t.setCreatedAt(LocalDateTime.now().minusDays(i));
            taskMapper.insert(t);
        }

        // 3. 验证遵从度统计 (10条中9条打卡 -> 90.0%)
        UserContext.set(childUser);
        MedicationComplianceVO compliance = complianceService.getCompliance(elderId, 30);
        assertNotNull(compliance);
        assertEquals(10L, compliance.getExpectedDosesCount());
        assertEquals(9L, compliance.getConfirmedDosesCount());
        assertEquals(1L, compliance.getMissedDosesCount());
        assertEquals(0, BigDecimal.valueOf(90.0).compareTo(compliance.getComplianceRate()));
        assertEquals("优秀遵从", compliance.getRatingText());
    }
}
