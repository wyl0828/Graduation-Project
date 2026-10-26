package cn.communityelderly.modules.dashboard;

import cn.communityelderly.modules.dashboard.dto.DashboardSummaryVO;
import cn.communityelderly.modules.dashboard.service.impl.CommunityDashboardServiceImpl;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.health.mapper.HealthAlertMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanMapper;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityDashboardServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("dashboard-test");
        TableInfoHelper.initTableInfo(assistant, ElderProfile.class);
        TableInfoHelper.initTableInfo(assistant, WorkOrder.class);
    }

    @Mock
    private ElderProfileMapper elderProfileMapper;

    @Mock
    private WorkOrderMapper workOrderMapper;

    @Mock
    private HealthAlertMapper healthAlertMapper;

    @Mock
    private SosEventMapper sosEventMapper;

    @Mock
    private MedicationTaskMapper medicationTaskMapper;

    @Mock
    private MedicationPlanMapper medicationPlanMapper;

    @InjectMocks
    private CommunityDashboardServiceImpl dashboardService;

    @Test
    void shouldReturnNullRatesWhenTheirDenominatorsAreEmpty() {
        when(elderProfileMapper.selectCount(any())).thenReturn(0L);
        when(elderProfileMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectCount(any())).thenReturn(0L);

        DashboardSummaryVO summary = dashboardService.getSummary(1L);

        assertNull(summary.getWorkOrderFulfillmentRate(), "无工单时不应呈现 100% 履约率");
        assertNull(summary.getMedicationComplianceRate(), "今日无服药任务时不应呈现 100% 合规率");
        assertEquals(0L, summary.getMonthlyClosedOrders());
        assertEquals(0L, summary.getTodayConfirmedCheckins());
    }
}
