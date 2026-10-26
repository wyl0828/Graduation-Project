package cn.communityelderly.modules.simulation;

import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.elder.dto.ElderArchiveVO;
import cn.communityelderly.modules.elder.dto.ElderCreateDTO;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.health.entity.HealthAlert;
import cn.communityelderly.modules.health.entity.VitalRecord;
import cn.communityelderly.modules.health.mapper.HealthAlertMapper;
import cn.communityelderly.modules.health.mapper.VitalRecordMapper;
import cn.communityelderly.modules.simulation.dto.SimulationConfigDTO;
import cn.communityelderly.modules.simulation.dto.SimulationStatusVO;
import cn.communityelderly.modules.simulation.dto.SingleInjectDTO;
import cn.communityelderly.modules.simulation.service.SimulationService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class SimulationIntegrationTest {

    @Autowired
    private SimulationService simulationService;

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private VitalRecordMapper vitalRecordMapper;

    @Autowired
    private HealthAlertMapper healthAlertMapper;

    private CurrentUser adminUser;
    private Long elderId;

    @BeforeEach
    void setUp() {
        adminUser = new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        UserContext.set(adminUser);

        ElderCreateDTO createDTO = new ElderCreateDTO();
        createDTO.setName("钱大妈");
        createDTO.setSex("FEMALE");
        createDTO.setBirthDate(LocalDate.of(1950, 11, 20));
        createDTO.setAddress("南京市玄武区锁金村20幢102");
        createDTO.setContactPhone("13999887766");
        createDTO.setEmergencyContactName("钱小弟");
        createDTO.setEmergencyContactPhone("13999887765");
        createDTO.setEmergencyContactRelation("弟弟");

        ElderArchiveVO elder = elderArchiveService.createElder(createDTO);
        elderId = elder.getElderId();
    }

    @AfterEach
    void tearDown() {
        // 保证测试结束后停止仿真
        try {
            simulationService.stopSimulation(elderId, "BLOOD_PRESSURE");
        } catch (Exception ignored) {
        }
        UserContext.clear();
    }

    @Test
    @DisplayName("AT-21: 单次注入165mmHg高压，来源为SIMULATED并联动触发高血压告警")
    void testSingleInjectTriggersAlert() {
        SingleInjectDTO injectDTO = new SingleInjectDTO();
        injectDTO.setElderId(elderId);
        injectDTO.setVitalType("BLOOD_PRESSURE");
        injectDTO.setValuePrimary(new BigDecimal("165"));
        injectDTO.setValueSecondary(new BigDecimal("98"));

        Long recordId = simulationService.injectSingleAbnormal(injectDTO);
        assertNotNull(recordId);

        // 验证来源为 SIMULATED
        VitalRecord record = vitalRecordMapper.selectById(recordId);
        assertNotNull(record);
        assertEquals("SIMULATED", record.getSourceType());
        assertEquals(0, new BigDecimal("165").compareTo(record.getValuePrimary()));

        // 验证联动触发了告警
        List<HealthAlert> alerts = healthAlertMapper.selectList(
                new LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getElderId, elderId)
        );
        assertFalse(alerts.isEmpty());
        assertEquals("HIGH", alerts.get(0).getAlertLevel());
    }

    @Test
    @DisplayName("AT-22: 仿真配置保存、启动与停止状态流转")
    void testSimulationLifecycle() {
        SimulationConfigDTO config = new SimulationConfigDTO();
        config.setElderId(elderId);
        config.setVitalType("BLOOD_PRESSURE");
        config.setSampleIntervalSec(5);
        config.setBaseValuePrimary(BigDecimal.valueOf(130));
        config.setBaseValueSecondary(BigDecimal.valueOf(82));

        simulationService.startSimulation(config);

        // 查询当前状态
        SimulationStatusVO status = simulationService.getStatus(elderId, "BLOOD_PRESSURE");
        assertNotNull(status);
        assertEquals("RUNNING", status.getStatus());

        // 停止仿真
        simulationService.stopSimulation(elderId, "BLOOD_PRESSURE");

        SimulationStatusVO stoppedStatus = simulationService.getStatus(elderId, "BLOOD_PRESSURE");
        assertNotNull(stoppedStatus);
        assertEquals("STOPPED", stoppedStatus.getStatus());
    }
}
