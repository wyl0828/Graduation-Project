package cn.communityelderly.modules.health;

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
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.elder.service.KinshipService;
import cn.communityelderly.modules.health.dto.AlertHandleDTO;
import cn.communityelderly.modules.health.dto.VitalRecordCreateDTO;
import cn.communityelderly.modules.health.dto.VitalTrendQueryDTO;
import cn.communityelderly.modules.health.dto.VitalTrendVO;
import cn.communityelderly.modules.health.entity.HealthAlert;
import cn.communityelderly.modules.health.mapper.HealthAlertMapper;
import cn.communityelderly.modules.health.service.HealthAlertService;
import cn.communityelderly.modules.health.service.VitalRecordService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class VitalRecordAndAlertIntegrationTest {

    @Autowired
    private VitalRecordService vitalRecordService;

    @Autowired
    private HealthAlertService healthAlertService;

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private KinshipService kinshipService;

    @Autowired
    private HealthAlertMapper healthAlertMapper;

    private CurrentUser adminUser;
    private CurrentUser childUser;
    private Long elderId;

    @BeforeEach
    void setUp() {
        adminUser = new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        childUser = new CurrentUser("4", "13800000004", "FAMILY", List.of("CHILD"), "CHILD", "1");

        UserContext.set(adminUser);

        // 预置建档一个测试老人
        ElderCreateDTO createDTO = new ElderCreateDTO();
        createDTO.setName("周老先生");
        createDTO.setSex("MALE");
        createDTO.setBirthDate(LocalDate.of(1945, 8, 1));
        createDTO.setAddress("南京市玄武区锁金二村10幢101");
        createDTO.setContactPhone("13955667788");
        createDTO.setEmergencyContactName("周小敏");
        createDTO.setEmergencyContactPhone("13800000004");
        createDTO.setEmergencyContactRelation("女儿");

        ElderArchiveVO elder = elderArchiveService.createElder(createDTO);
        elderId = elder.getElderId();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("AT-17: 录入收缩压165mmHg超标体征，规则引擎判定生成HIGH预警并固化证据快照")
    void testVitalRecordTriggersAlertAndSnapshot() {
        UserContext.set(adminUser);

        VitalRecordCreateDTO dto = new VitalRecordCreateDTO();
        dto.setElderId(elderId);
        dto.setVitalType("BLOOD_PRESSURE");
        dto.setValuePrimary(new BigDecimal("165")); // 超标 >= 160 -> HIGH
        dto.setValueSecondary(new BigDecimal("95"));
        dto.setUnit("mmHg");
        dto.setSourceType("MANUAL");
        dto.setRecordedAt(LocalDateTime.now());
        dto.setRemark("社区工作人员上门测量");

        Long recordId = vitalRecordService.recordVital(dto);
        assertNotNull(recordId);

        // 验证生成了 HIGH 级别预警
        List<HealthAlert> alerts = healthAlertMapper.selectList(
                new LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getElderId, elderId)
                        .eq(HealthAlert::getAlertLevel, "HIGH")
        );
        assertFalse(alerts.isEmpty());
        HealthAlert alert = alerts.get(0);
        assertEquals("PENDING", alert.getStatus());
        assertEquals("HIGH", alert.getAlertLevel());
        assertNotNull(alert.getAssessmentTaskId());

        // 管理员结案处理
        AlertHandleDTO handleDTO = new AlertHandleDTO();
        handleDTO.setHandlingResult("已电话联系家属提醒服药，血压目前稳定");
        healthAlertService.handleAlert(alert.getAlertId(), handleDTO);

        HealthAlert handled = healthAlertMapper.selectById(alert.getAlertId());
        assertEquals("CLOSED", handled.getStatus());
        assertNotNull(handled.getHandledAt());
        assertEquals("已电话联系家属提醒服药，血压目前稳定", handled.getHandlingResult());
    }

    @Test
    @DisplayName("AT-18: 子女端查询体征趋势正常，争议冻结(FROZEN)后穿透拦截返回403")
    void testKinshipFrozenBlocksVitalTrendQuery() {
        // 1. 子女发起亲情绑定申请
        UserContext.set(childUser);
        KinshipApplyDTO applyDTO = new KinshipApplyDTO();
        applyDTO.setElderId(elderId);
        applyDTO.setRelationship("女儿");
        applyDTO.setApplyReason("长辈独居需要照看，申请亲情关联");
        Long appId = kinshipService.applyKinship(applyDTO);
        assertNotNull(appId);

        // 2. 管理员审核通过
        UserContext.set(adminUser);
        KinshipReviewDTO reviewDTO = new KinshipReviewDTO();
        reviewDTO.setApproved(true);
        reviewDTO.setDecisionReason("户口本与亲情证明核对无误");
        kinshipService.reviewApplication(appId, reviewDTO);

        // 3. 先录入一条体征
        VitalRecordCreateDTO dto = new VitalRecordCreateDTO();
        dto.setElderId(elderId);
        dto.setVitalType("BLOOD_PRESSURE");
        dto.setValuePrimary(new BigDecimal("125"));
        dto.setValueSecondary(new BigDecimal("82"));
        dto.setUnit("mmHg");
        dto.setSourceType("MANUAL");
        dto.setRecordedAt(LocalDateTime.now());
        vitalRecordService.recordVital(dto);

        // 4. 子女正常查询走势
        UserContext.set(childUser);
        VitalTrendQueryDTO queryDTO = new VitalTrendQueryDTO();
        queryDTO.setElderId(elderId);
        queryDTO.setVitalType("BLOOD_PRESSURE");
        queryDTO.setDays(7);
        VitalTrendVO trend = vitalRecordService.queryTrend(queryDTO);
        assertNotNull(trend);
        assertNotNull(trend.getDataPoints());
        assertEquals(1, trend.getDataPoints().size());

        // 5. 关系被争议冻结
        UserContext.set(childUser);
        List<BoundElderVO> boundList = kinshipService.listBoundElders(4L);
        BoundElderVO boundElder = boundList.stream().filter(e -> e.getElderId().equals(elderId)).findFirst().orElseThrow();

        UserContext.set(adminUser);
        KinshipFreezeDTO freezeDTO = new KinshipFreezeDTO();
        freezeDTO.setFreezeReasonCode("FAMILY_DISPUTE");
        freezeDTO.setFreezeReasonText("亲属间存在监护抚养权争议，社区介入核实期间暂冻结");
        kinshipService.freezeKinship(boundElder.getLinkId(), freezeDTO);

        // 6. 子女再次查询，应被 403 阻断
        UserContext.set(childUser);
        BusinessException ex = assertThrows(BusinessException.class, () -> vitalRecordService.queryTrend(queryDTO));
        assertEquals(ErrorCode.KINSHIP_NOT_ACTIVE, ex.getErrorCode());
    }
}
