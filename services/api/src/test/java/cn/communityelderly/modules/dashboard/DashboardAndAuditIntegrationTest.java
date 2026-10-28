package cn.communityelderly.modules.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.dto.AuditDiffVO;
import cn.communityelderly.modules.audit.dto.AuditLogQueryDTO;
import cn.communityelderly.modules.audit.dto.AuditLogVO;
import cn.communityelderly.modules.audit.entity.AuditLog;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.dashboard.dto.DashboardChartsVO;
import cn.communityelderly.modules.dashboard.dto.DashboardSummaryVO;
import cn.communityelderly.modules.dashboard.dto.RealtimeAlertFeedVO;
import cn.communityelderly.modules.dashboard.service.CommunityDashboardService;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class DashboardAndAuditIntegrationTest {

    @Autowired
    private CommunityDashboardService dashboardService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ElderProfileMapper elderProfileMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderActionHistoryMapper workOrderActionHistoryMapper;

    @Autowired
    private Clock applicationClock;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        CurrentUser testAdmin = new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        UserContext.set(testAdmin);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("测试 M09 社区态势感知大屏核心 KPI 与图表复合指标聚合计算")
    void testDashboardSummaryAndCharts() {
        // 1. 测试汇总 KPI (真实数据库聚合结果)
        DashboardSummaryVO summary = dashboardService.getSummary(1L);
        assertNotNull(summary, "Dashboard 概览指标不应为空");
        assertTrue(summary.getTotalElders() >= 1L, "辖区在册长辈基线数应 >= 1");
        assertTrue(summary.getHighAgeAloneCount() >= 0, "高龄独居长辈数量应 >= 0");
        assertTrue(summary.getPendingAlertsCount() >= 0L, "待处置预警数应 >= 0");
        assertTrue(summary.getWorkOrderFulfillmentRate() == null ||
                        (summary.getWorkOrderFulfillmentRate() >= 0.0 && summary.getWorkOrderFulfillmentRate() <= 100.0),
                "工单履约率无分母时应为空，否则应在 0~100 之间");
        assertTrue(summary.getMedicationComplianceRate() == null ||
                        (summary.getMedicationComplianceRate() >= 0.0 && summary.getMedicationComplianceRate() <= 100.0),
                "用药合规率无分母时应为空，否则应在 0~100 之间");
        assertTrue(summary.getMonthlyClosedOrders() >= 0L, "当月办结工单数基线应 >= 0");

        // 2. 测试图表数据结构
        DashboardChartsVO charts = dashboardService.getCharts(1L, "7D");
        assertNotNull(charts, "图表数据对象不应为空");
        assertEquals(5, charts.getDiseaseDistribution().size(), "慢病画像应包含 TOP 5 疾病构成");
        assertEquals(4, charts.getServiceStructure().size(), "助老服务结构应包含 4 类主流服务占比");
        assertEquals(7, charts.getWorkOrderTrend().getDateList().size(), "7天趋势曲线应包含完整的 7 天周期");
        assertEquals(7, charts.getWorkOrderTrend().getDispatchedCounts().size(), "受理派单数应包含 7 天");
        assertEquals(7, charts.getWorkOrderTrend().getClosedCounts().size(), "完工结案数应包含 7 天");
        assertEquals(7, charts.getWorkOrderTrend().getAvgResponseMinutes().size(), "平均响应时长应包含 7 天");

        // 3. 测试实时预警/求助流
        List<RealtimeAlertFeedVO> alerts = dashboardService.getRealtimeAlerts(1L, 10);
        assertNotNull(alerts, "预警流数据不应为空");
        assertTrue(alerts.size() >= 2, "预警流应包含告警事件记录");
        assertNotNull(alerts.get(0).getElderName(), "预警流首条记录必须包含长辈姓名");
        assertNotNull(alerts.get(0).getTagType(), "预警流标签类型必须有效");
    }

    @Test
    @DisplayName("首次接单趋势使用数据库操作历史并按老人所属社区隔离，序列化保留普通与 SOS 分类")
    void firstAcceptanceTrendUsesActionHistoryAndCommunityScope() throws Exception {
        ElderProfile communityOneElder = findElderInCommunity(1L);
        ElderProfile communityTwoElder = findElderInCommunity(2L);
        LocalDate today = LocalDate.now(applicationClock);

        DashboardChartsVO beforeCommunityOne = dashboardService.getCharts(1L, "7D");
        DashboardChartsVO beforeCommunityTwo = dashboardService.getCharts(2L, "7D");
        int ordinaryDayIndex = firstEmptyAcceptedDay(
                beforeCommunityOne.getWorkOrderTrendByCategory().get("ORDINARY"));
        int sosDayIndex = firstEmptyAcceptedDay(
                beforeCommunityTwo.getWorkOrderTrendByCategory().get("SOS"));
        LocalDate ordinaryDay = today.minusDays(6L - ordinaryDayIndex);
        LocalDate sosDay = today.minusDays(6L - sosDayIndex);

        WorkOrder ordinaryAccepted = insertWorkOrder(communityOneElder, "MANUAL", "CLOSED", ordinaryDay.atTime(10, 0));
        insertAcceptanceHistory(ordinaryAccepted, "DISPATCHED", "ACCEPT", ordinaryDay.atTime(10, 8, 30));

        DashboardChartsVO afterCommunityOne = dashboardService.getCharts(1L, "7D");
        DashboardChartsVO afterCommunityTwo = dashboardService.getCharts(2L, "7D");
        assertEquals(beforeCommunityOne.getWorkOrderTrend().getAcceptedCounts().get(ordinaryDayIndex) + 1,
                afterCommunityOne.getWorkOrderTrend().getAcceptedCounts().get(ordinaryDayIndex));
        assertEquals(beforeCommunityOne.getWorkOrderTrendByCategory().get("ORDINARY").getAcceptedCounts().get(ordinaryDayIndex) + 1,
                afterCommunityOne.getWorkOrderTrendByCategory().get("ORDINARY").getAcceptedCounts().get(ordinaryDayIndex));
        assertEquals(beforeCommunityOne.getWorkOrderTrendByCategory().get("SOS").getAcceptedCounts().get(ordinaryDayIndex),
                afterCommunityOne.getWorkOrderTrendByCategory().get("SOS").getAcceptedCounts().get(ordinaryDayIndex));
        assertEquals(beforeCommunityTwo.getWorkOrderTrend().getAcceptedCounts().get(ordinaryDayIndex),
                afterCommunityTwo.getWorkOrderTrend().getAcceptedCounts().get(ordinaryDayIndex),
                "社区 1 新增工单不得流入社区 2 看板");
        assertEquals(8.5, afterCommunityOne.getWorkOrderTrendByCategory().get("ORDINARY").getAvgResponseMinutes().get(ordinaryDayIndex));

        WorkOrder sosAccepted = insertWorkOrder(communityTwoElder, "SOS", "ACCEPTED", sosDay.atTime(11, 0));
        insertAcceptanceHistory(sosAccepted, "PENDING_DISPATCH", "TRIGGER_SOS", sosDay.atTime(11, 0, 20));
        DashboardChartsVO afterCommunityTwoSos = dashboardService.getCharts(2L, "7D");
        DashboardChartsVO afterCommunityOneSos = dashboardService.getCharts(1L, "7D");
        assertEquals(1L, afterCommunityTwoSos.getWorkOrderTrendByCategory().get("SOS").getAcceptedCounts().get(sosDayIndex)
                        - beforeCommunityTwo.getWorkOrderTrendByCategory().get("SOS").getAcceptedCounts().get(sosDayIndex),
                "SOS 自动接单应进入独立分类");
        assertEquals(0.3, afterCommunityTwoSos.getWorkOrderTrendByCategory().get("SOS").getAvgResponseMinutes().get(sosDayIndex), 0.001);
        assertEquals(beforeCommunityOne.getWorkOrderTrend().getAcceptedCounts().get(sosDayIndex)
                        + (ordinaryDayIndex == sosDayIndex ? 1 : 0),
                afterCommunityOneSos.getWorkOrderTrend().getAcceptedCounts().get(sosDayIndex),
                "社区 2 的 SOS 工单不得流入社区 1 看板");

        JsonNode serialized = objectMapper.valueToTree(afterCommunityOne);
        assertTrue(serialized.at("/workOrderTrendByCategory/ORDINARY/acceptedCounts/" + ordinaryDayIndex).isNumber());
        assertTrue(serialized.at("/workOrderTrendByCategory/ORDINARY/avgResponseMinutes/" + ordinaryDayIndex).isNumber());
        assertTrue(serialized.at("/workOrderTrendByCategory/SOS").isObject());
        assertEquals(ordinaryDay.format(DateTimeFormatter.ofPattern("MM-dd")),
                serialized.at("/workOrderTrend/dateList/" + ordinaryDayIndex).asText());
    }

    private int firstEmptyAcceptedDay(cn.communityelderly.modules.dashboard.dto.WorkOrder7DayTrendVO trend) {
        for (int index = 0; index < trend.getAcceptedCounts().size() - 1; index++) {
            if (trend.getAcceptedCounts().get(index) == 0L) {
                return index;
            }
        }
        fail("固定隔离种子至少应有一个历史日无首次接单样本，供集成夹具验证空样本到有效样本的变化");
        return -1;
    }

    private ElderProfile findElderInCommunity(Long communityId) {
        ElderProfile elder = elderProfileMapper.selectOne(new LambdaQueryWrapper<ElderProfile>()
                .eq(ElderProfile::getCommunityId, communityId)
                .last("LIMIT 1"));
        if (elder == null && communityId == 2L) {
            String token = UUID.randomUUID().toString().replace("-", "");
            elder = new ElderProfile();
            elder.setArchiveNo("T5" + token.substring(0, 30));
            elder.setCommunityId(communityId);
            elder.setName("批次五合成老人");
            elder.setAddress("隔离验收测试地址");
            elder.setStatus("ACTIVE");
            elder.setCreatedBy(1L);
            elder.setUpdatedBy(1L);
            assertEquals(1, elderProfileMapper.insert(elder));
        }
        assertNotNull(elder, "隔离种子必须为社区 " + communityId + " 提供老人档案");
        return elder;
    }

    private WorkOrder insertWorkOrder(ElderProfile elder, String sourceType, String status, LocalDateTime createdAt) {
        String token = UUID.randomUUID().toString().replace("-", "");
        WorkOrder order = new WorkOrder();
        order.setOrderNo("T5" + token.substring(0, 30));
        order.setSourceType(sourceType);
        order.setSourceReferenceId("T5-" + token);
        order.setElderId(elder.getElderId());
        order.setCommunityId(elder.getCommunityId());
        order.setServiceType("SOS".equals(sourceType) ? "EMERGENCY_RESCUE" : "CLEANING");
        order.setOrderTitle("批次 5 首次接单统计夹具");
        order.setStatus(status);
        order.setCreatedAt(createdAt);
        assertEquals(1, workOrderMapper.insert(order));
        return order;
    }

    private void insertAcceptanceHistory(WorkOrder order, String fromStatus, String actionName, LocalDateTime occurredAt) {
        WorkOrderActionHistory action = new WorkOrderActionHistory();
        action.setWorkOrderId(order.getWorkOrderId());
        action.setOperatorUserId(1L);
        action.setOperatorRole("CARE_WORKER");
        action.setFromStatus(fromStatus);
        action.setToStatus("ACCEPTED");
        action.setActionName(actionName);
        action.setComment("批次 5 手算夹具");
        action.setTraceId("T5-" + UUID.randomUUID());
        action.setOccurredAt(occurredAt);
        assertEquals(1, workOrderActionHistoryMapper.insert(action));
    }

    @Test
    @DisplayName("测试 M12 全局安全审计日志记录与多维条件精确检索")
    void testAuditLogRecordingAndQuery() {
        // 1. 记录一条合规事件
        String detailsJson = "{\"action\": \"APPROVE_RESERVATION\", \"reservation_no\": \"RSV_TEST_9999\", \"comment\": \"资质合格准予派单\"}";
        AuditLog saved = auditLogService.recordAudit(
                1L,
                "COMMUNITY_ADMIN",
                1L,
                "SERVICE_RESERVATION",
                "9999",
                "APPROVE_RESERVATION",
                "SUCCESS",
                "192.168.1.100",
                detailsJson
        );
        assertNotNull(saved.getAuditId(), "审计记录持久化后主键必须生成");
        assertNotNull(saved.getTraceId(), "全局链路追踪 Trace-Id 必须自动注入");

        // 2. 按 targetType 精确检索
        AuditLogQueryDTO queryDTO = new AuditLogQueryDTO();
        queryDTO.setTargetType("SERVICE_RESERVATION");
        queryDTO.setAction("APPROVE");
        PageResult<AuditLogVO> pageResult = auditLogService.queryAuditLogs(queryDTO);

        assertNotNull(pageResult, "分页查询结果不应为空");
        assertTrue(pageResult.getTotal() >= 1, "应至少能检索到 1 条匹配的审计流水");

        AuditLogVO matched = pageResult.getItems().stream()
                .filter(item -> "9999".equals(item.getTargetId()))
                .findFirst()
                .orElse(null);
        assertNotNull(matched, "检索结果中应包含刚写入的记录");
        assertEquals("COMMUNITY_ADMIN", matched.getActorRole());
        assertEquals("SUCCESS", matched.getResult());
        assertNotNull(matched.getActorName(), "操作责任人姓名应已格式化解析");
    }

    @Test
    @DisplayName("测试 M12 数据快照 Diff 比对与防篡改 SHA-256 签名存证")
    void testAuditDiffViewerAndShaSignature() {
        // 构造具有 before_json 和 after_json 的复杂状态更迭审计
        String snapshotJson = "{\n" +
                "  \"before_json\": {\"status\": \"PENDING\", \"reviewerComment\": \"\", \"urgency\": \"NORMAL\"},\n" +
                "  \"after_json\": {\"status\": \"CLOSED\", \"reviewerComment\": \"免照合规结案通过\", \"urgency\": \"NORMAL\"}\n" +
                "}";

        AuditLog log = auditLogService.recordAudit(
                1L,
                "COMMUNITY_ADMIN",
                1L,
                "WORK_ORDER",
                "WO8888",
                "EXEMPTION_APPROVE",
                "SUCCESS",
                "127.0.0.1",
                snapshotJson
        );

        // 获取 Diff 视图
        AuditDiffVO diffVO = auditLogService.getAuditDiff(log.getAuditId());
        assertNotNull(diffVO, "Diff 对象不应为空");
        assertNotNull(diffVO.getShaSignature(), "防篡改数字签名必须存在");
        assertEquals(64, diffVO.getShaSignature().length(), "HMAC-SHA256 生成 64 位大写十六进制签名");

        // 校验差异比对项
        assertNotNull(diffVO.getDiffItems(), "Diff 比对清单不应为空");
        // 共有 2 个字段发生变更：status (PENDING -> CLOSED), reviewerComment ("" -> "免照合规结案通过")
        // urgency (NORMAL -> NORMAL) 未变更，不应出现在 Diff 变更列表中
        assertEquals(2, diffVO.getDiffItems().size(), "应精确捕获 2 项字段差异变更");

        boolean hasStatusDiff = diffVO.getDiffItems().stream()
                .anyMatch(d -> "status".equals(d.getFieldName()) && "PENDING".equals(d.getBaselineValue()) && "CLOSED".equals(d.getProposedValue()));
        assertTrue(hasStatusDiff, "必须包含 status 字段从 PENDING 到 CLOSED 的状态更迭比对");
    }

    @Test
    @DisplayName("测试 M12 审计日志合规性 CSV 报表导出与防乱码 BOM 校验")
    void testAuditLogCsvExport() {
        AuditLogQueryDTO queryDTO = new AuditLogQueryDTO();
        String csv = auditLogService.exportAuditLogsAsCsv(queryDTO);

        assertNotNull(csv, "导出的 CSV 报表不应为空");
        assertTrue(csv.startsWith("\uFEFF"), "CSV 开头必须包含 UTF-8 BOM 以兼容 Excel 中文显示");
        assertTrue(csv.contains("流水号,发生时间,操作责任人"), "CSV 首行必须包含规范表头");
        assertTrue(csv.split("\n").length >= 2, "报表至少包含表头及 1 行以上数据");
    }
}
