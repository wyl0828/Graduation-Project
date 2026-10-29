package cn.communityelderly.modules.dashboard;

import cn.communityelderly.modules.dashboard.dto.DashboardSummaryVO;
import cn.communityelderly.modules.dashboard.service.impl.CommunityDashboardServiceImpl;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.health.mapper.HealthAlertMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanMapper;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CommunityDashboardServiceImplTest {

    @BeforeAll
    static void initializeLambdaMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("dashboard-test");
        TableInfoHelper.initTableInfo(assistant, ElderProfile.class);
        TableInfoHelper.initTableInfo(assistant, WorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, WorkOrderActionHistory.class);
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

    @Mock
    private WorkOrderActionHistoryMapper actionHistoryMapper;

    private CommunityDashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new CommunityDashboardServiceImpl(elderProfileMapper, workOrderMapper,
                healthAlertMapper, sosEventMapper, medicationTaskMapper, medicationPlanMapper,
                actionHistoryMapper, Clock.fixed(Instant.parse("2026-10-27T16:05:00Z"), ZoneId.of("UTC")));
    }

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

    @Test
    void shouldReturnNullResponseAveragesWhenThereAreNoAcceptedOrders() {
        when(elderProfileMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of());

        var trend = dashboardService.getCharts(1L, "7D").getWorkOrderTrend();

        assertEquals(7, trend.getAvgResponseMinutes().size());
        trend.getAvgResponseMinutes().forEach(value -> assertNull(value, "无有效首次接单样本必须返回 null"));
    }

    @Test
    void shouldGroupFirstAcceptancesByCreationDateAndKeepCategoriesAndUnacceptedCountsVisible() {
        when(elderProfileMapper.selectList(any())).thenReturn(List.of());
        WorkOrder first = order(1, 1, "MANUAL", "CLOSED", "2026-10-23T23:50:00");
        first.setAcceptTime(LocalDateTime.parse("2026-10-25T00:00:00")); // mutable field is not first acceptance
        first.setServiceStartTime(LocalDateTime.parse("2026-10-26T09:00:00"));
        first.setServiceFinishTime(LocalDateTime.parse("2026-10-26T13:00:00"));
        first.setReviewedAt(LocalDateTime.parse("2026-10-26T13:00:00"));
        when(workOrderMapper.selectList(any())).thenReturn(List.of(
                first, order(2, 1, "RESERVATION", "DISPATCHED", "2026-10-23T00:00:00"),
                order(3, 1, "SOS", "ACCEPTED", "2026-10-23T12:00:00"),
                order(4, 1, "MANUAL", "PENDING_DISPATCH", "2026-10-24T00:00:00"),
                order(5, 1, "RESERVATION", "DISPATCHED", "2026-10-24T12:00:00"),
                order(6, 1, "MANUAL", "VOIDED", "2026-10-24T12:00:00"),
                order(7, 1, "MANUAL", "TERMINATED_PRE_ACCEPTANCE", "2026-10-24T12:00:00"),
                order(8, 1, "MANUAL", "DISPATCHED", "2026-10-24T12:00:00")));
        when(actionHistoryMapper.selectList(any())).thenReturn(List.of(
                action(1, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-24T01:50:00"),
                action(1, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-24T00:10:00"),
                action(2, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-23T00:10:00"),
                action(3, "PENDING_DISPATCH", "ACCEPTED", "TRIGGER_SOS", "2026-10-23T12:00:00"),
                action(8, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-24T11:59:59"),
                action(8, "PROCESSING", "ACCEPTED", "HANDOFF_ACCEPTED", "2026-10-24T12:01:00"),
                action(8, "DISPATCHED", "PROCESSING", "ACCEPT", "2026-10-24T12:02:00")));

        var charts = dashboardService.getCharts(1L, "7D");
        var trend = charts.getWorkOrderTrend();
        assertEquals(List.of("10-22", "10-23", "10-24", "10-25", "10-26", "10-27", "10-28"), trend.getDateList());
        assertEquals(10.0, trend.getAvgResponseMinutes().get(1)); // (20 + 10 + SOS 0) / 3
        assertNull(trend.getAvgResponseMinutes().get(4), "审核日不得决定响应样本归属");
        assertEquals(1L, trend.getClosedCounts().get(4));
        var json = new ObjectMapper().valueToTree(charts);
        assertEquals(3, json.at("/workOrderTrend/acceptedCounts/1").asInt());
        assertEquals(3, json.at("/workOrderTrend/unacceptedCounts/2").asInt());
        assertEquals(15.0, json.at("/workOrderTrendByCategory/ORDINARY/avgResponseMinutes/1").asDouble());
        assertEquals(2, json.at("/workOrderTrendByCategory/ORDINARY/acceptedCounts/1").asInt());
        assertEquals(0.0, json.at("/workOrderTrendByCategory/SOS/avgResponseMinutes/1").asDouble());
        assertEquals(1, json.at("/workOrderTrendByCategory/SOS/acceptedCounts/1").asInt());
        verify(actionHistoryMapper, times(1)).selectList(any()); // a single bounded batch, not per-order lookups
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldUseShanghaiHalfOpenSevenDayWindowAndCommunityScopedQueries() {
        when(elderProfileMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(
                order(10, 1, "MANUAL", "ACCEPTED", "2026-10-22T00:00:00"),
                order(11, 1, "MANUAL", "ACCEPTED", "2026-10-21T23:59:59"),
                order(12, 1, "MANUAL", "ACCEPTED", "2026-10-29T00:00:00")),
                List.of(order(20, 2, "MANUAL", "ACCEPTED", "2026-10-22T00:00:00")));
        when(actionHistoryMapper.selectList(any())).thenReturn(List.of(
                action(10, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-22T00:12:30"),
                action(11, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-22T00:00:00"),
                action(12, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-29T00:01:00")),
                List.of(action(20, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-22T01:00:00")));

        var first = dashboardService.getCharts(1L, "7D").getWorkOrderTrend();
        var second = dashboardService.getCharts(2L, "7D").getWorkOrderTrend();
        assertEquals(12.5, first.getAvgResponseMinutes().get(0));
        assertEquals(60.0, second.getAvgResponseMinutes().get(0));
        assertEquals(1L, first.getDispatchedCounts().get(0));
        assertEquals(0L, first.getDispatchedCounts().get(6));
        ArgumentCaptor<LambdaQueryWrapper<WorkOrder>> queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(workOrderMapper, times(2)).selectList(queries.capture());
        for (int i = 0; i < 2; i++) {
            var query = queries.getAllValues().get(i);
            assertTrue(query.getSqlSegment().contains("community_id ="));
            assertTrue(query.getParamNameValuePairs().containsValue((long) i + 1));
        }
    }

    @Test
    void shouldRetainFirstAcceptanceAfterVoidingAndExcludeInvalidOrFutureEventsBeforeRounding() {
        when(elderProfileMapper.selectList(any())).thenReturn(List.of());
        when(workOrderMapper.selectList(any())).thenReturn(List.of(
                order(30, 1, "MANUAL", "VOIDED", "2026-10-27T23:59:00"),
                order(31, 1, "MANUAL", "SERVICE_STOPPED", "2026-10-27T10:00:00"),
                order(32, 1, "MANUAL", "DISPATCHED", "2026-10-27T12:00:00"),
                order(33, 1, "SOS", "PENDING_DISPATCH", "2026-10-27T12:00:00")));
        when(actionHistoryMapper.selectList(any())).thenReturn(List.of(
                action(30, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-28T00:00:01"),
                action(31, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-27T09:59:59"),
                action(31, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-27T10:02:02"),
                action(32, "PENDING_DISPATCH", "ACCEPTED", "TRIGGER_SOS", "2026-10-27T12:00:00"),
                action(32, "PROCESSING", "ACCEPTED", "ACCEPT", "2026-10-27T12:00:00"),
                action(32, "DISPATCHED", "ACCEPTED", "ACCEPT", "2026-10-28T00:05:01"),
                action(33, "PENDING_DISPATCH", "PENDING_DISPATCH", "TRIGGER_SOS", "2026-10-27T12:00:00")));

        var charts = dashboardService.getCharts(1L, "7D");
        var trend = charts.getWorkOrderTrend();

        // (61 seconds + 122 seconds) / 2 / 60 = 1.525 minutes, rounded once to 1.5.
        assertEquals(1.5, trend.getAvgResponseMinutes().get(5));
        assertEquals(2L, trend.getAcceptedCounts().get(5));
        assertEquals(2L, trend.getUnacceptedCounts().get(5));
        var json = new ObjectMapper().valueToTree(charts);
        assertTrue(json.at("/workOrderTrendByCategory/SOS/avgResponseMinutes/5").isNull());
        assertEquals(1L, charts.getWorkOrderTrendByCategory().get("SOS").getUnacceptedCounts().get(5));
    }

    private static WorkOrder order(long id, long communityId, String source, String status, String created) {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderId(id);
        order.setCommunityId(communityId);
        order.setSourceType(source);
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.parse(created));
        return order;
    }

    private static WorkOrderActionHistory action(long orderId, String from, String to, String name, String occurred) {
        WorkOrderActionHistory action = new WorkOrderActionHistory();
        action.setWorkOrderId(orderId);
        action.setFromStatus(from);
        action.setToStatus(to);
        action.setActionName(name);
        action.setOccurredAt(LocalDateTime.parse(occurred));
        return action;
    }
}
