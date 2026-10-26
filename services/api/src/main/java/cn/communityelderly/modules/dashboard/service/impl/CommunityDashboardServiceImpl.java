package cn.communityelderly.modules.dashboard.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.communityelderly.modules.dashboard.dto.*;
import cn.communityelderly.modules.dashboard.service.CommunityDashboardService;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.SosEvent;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.health.entity.HealthAlert;
import cn.communityelderly.modules.health.mapper.HealthAlertMapper;
import cn.communityelderly.modules.medication.entity.MedicationPlan;
import cn.communityelderly.modules.medication.entity.MedicationTask;
import cn.communityelderly.modules.medication.mapper.MedicationPlanMapper;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommunityDashboardServiceImpl implements CommunityDashboardService {

    private final ElderProfileMapper elderProfileMapper;
    private final WorkOrderMapper workOrderMapper;
    private final HealthAlertMapper healthAlertMapper;
    private final SosEventMapper sosEventMapper;
    private final MedicationTaskMapper medicationTaskMapper;
    private final MedicationPlanMapper medicationPlanMapper;

    public CommunityDashboardServiceImpl(ElderProfileMapper elderProfileMapper,
                                         WorkOrderMapper workOrderMapper,
                                         HealthAlertMapper healthAlertMapper,
                                         SosEventMapper sosEventMapper,
                                         MedicationTaskMapper medicationTaskMapper,
                                         MedicationPlanMapper medicationPlanMapper) {
        this.elderProfileMapper = elderProfileMapper;
        this.workOrderMapper = workOrderMapper;
        this.healthAlertMapper = healthAlertMapper;
        this.sosEventMapper = sosEventMapper;
        this.medicationTaskMapper = medicationTaskMapper;
        this.medicationPlanMapper = medicationPlanMapper;
    }

    @Override
    public DashboardSummaryVO getSummary(Long communityId) {
        DashboardSummaryVO vo = new DashboardSummaryVO();

        // 1. 在册老人数量与高龄 (>=80岁) 真实统计
        LambdaQueryWrapper<ElderProfile> elderQuery = new LambdaQueryWrapper<ElderProfile>()
                .eq(ElderProfile::getStatus, "ACTIVE");
        if (communityId != null) {
            elderQuery.eq(ElderProfile::getCommunityId, communityId);
        }
        Long totalElders = elderProfileMapper.selectCount(elderQuery);
        vo.setTotalElders(totalElders != null ? totalElders : 0L);

        LocalDate eightyYearsAgo = LocalDate.now().minusYears(80);
        LambdaQueryWrapper<ElderProfile> highAgeQuery = new LambdaQueryWrapper<ElderProfile>()
                .eq(ElderProfile::getStatus, "ACTIVE")
                .le(ElderProfile::getBirthDate, eightyYearsAgo);
        if (communityId != null) {
            highAgeQuery.eq(ElderProfile::getCommunityId, communityId);
        }
        Long highAgeCount = elderProfileMapper.selectCount(highAgeQuery);
        vo.setHighAgeAloneCount(highAgeCount != null ? highAgeCount : 0L);

        // 2. 待处理预警与活跃 SOS (按老人所在社区隔离)
        List<Long> communityElderIds = getElderIdsForCommunity(communityId);

        long pendingAlerts = 0L;
        long pendingSos = 0L;
        if (!communityElderIds.isEmpty()) {
            LambdaQueryWrapper<HealthAlert> alertQuery = new LambdaQueryWrapper<HealthAlert>()
                    .in(HealthAlert::getElderId, communityElderIds)
                    .ne(HealthAlert::getStatus, "CLOSED")
                    .ne(HealthAlert::getStatus, "RESOLVED");
            Long alertsCount = healthAlertMapper.selectCount(alertQuery);
            pendingAlerts = alertsCount != null ? alertsCount : 0L;

            LambdaQueryWrapper<SosEvent> sosQuery = new LambdaQueryWrapper<SosEvent>()
                    .in(SosEvent::getElderId, communityElderIds)
                    .in(SosEvent::getStatus, "ACCEPTED", "TRIGGERED");
            Long sosCount = sosEventMapper.selectCount(sosQuery);
            pendingSos = sosCount != null ? sosCount : 0L;
        }

        vo.setPendingAlertsCount(pendingAlerts + pendingSos);

        // 3. 工单履约率与当月结案真实统计
        LambdaQueryWrapper<WorkOrder> totalOrderQuery = new LambdaQueryWrapper<WorkOrder>()
                .ne(WorkOrder::getStatus, "VOIDED");
        if (communityId != null) {
            totalOrderQuery.eq(WorkOrder::getCommunityId, communityId);
        }
        Long totalOrders = workOrderMapper.selectCount(totalOrderQuery);

        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        LambdaQueryWrapper<WorkOrder> closedOrderQuery = new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getStatus, "CLOSED")
                .ge(WorkOrder::getReviewedAt, monthStart);
        if (communityId != null) {
            closedOrderQuery.eq(WorkOrder::getCommunityId, communityId);
        }
        Long closedOrders = workOrderMapper.selectCount(closedOrderQuery);

        Double fulfillmentRate = null;
        if (totalOrders != null && totalOrders > 0 && closedOrders != null) {
            fulfillmentRate = Math.round((closedOrders * 100.0 / totalOrders) * 10.0) / 10.0;
        }
        vo.setWorkOrderFulfillmentRate(fulfillmentRate);
        vo.setMonthlyClosedOrders(closedOrders != null ? closedOrders : 0L);

        // 4. 用药合规率与今日打卡真实统计 (按老人所在社区隔离)
        LocalDate today = LocalDate.now();
        long todayTasksCount = 0L;
        long confirmedTasksCount = 0L;

        if (!communityElderIds.isEmpty()) {
            LambdaQueryWrapper<MedicationTask> taskQuery = new LambdaQueryWrapper<MedicationTask>()
                    .in(MedicationTask::getElderId, communityElderIds)
                    .eq(MedicationTask::getTaskDate, today);
            Long tasks = medicationTaskMapper.selectCount(taskQuery);
            todayTasksCount = tasks != null ? tasks : 0L;

            LambdaQueryWrapper<MedicationTask> confirmedQuery = new LambdaQueryWrapper<MedicationTask>()
                    .in(MedicationTask::getElderId, communityElderIds)
                    .eq(MedicationTask::getTaskDate, today)
                    .eq(MedicationTask::getStatus, "CONFIRMED");
            Long confirmed = medicationTaskMapper.selectCount(confirmedQuery);
            confirmedTasksCount = confirmed != null ? confirmed : 0L;
        }

        Double medicationRate = null;
        if (todayTasksCount > 0) {
            medicationRate = Math.round((confirmedTasksCount * 100.0 / todayTasksCount) * 10.0) / 10.0;
        }
        vo.setMedicationComplianceRate(medicationRate);
        vo.setTodayConfirmedCheckins(confirmedTasksCount);

        return vo;
    }

    private List<Long> getElderIdsForCommunity(Long communityId) {
        LambdaQueryWrapper<ElderProfile> query = new LambdaQueryWrapper<ElderProfile>()
                .select(ElderProfile::getElderId);
        if (communityId != null) {
            query.eq(ElderProfile::getCommunityId, communityId);
        }
        List<ElderProfile> profiles = elderProfileMapper.selectList(query);
        if (profiles == null || profiles.isEmpty()) {
            return List.of();
        }
        return profiles.stream()
                .map(ElderProfile::getElderId)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public DashboardChartsVO getCharts(Long communityId, String period) {
        DashboardChartsVO chartsVO = new DashboardChartsVO();

        // 1. 慢病画像分布构成 (根据社区老人在服药计划真实统计)
        List<Long> communityElderIds = getElderIdsForCommunity(communityId);
        List<DiseaseDistributionVO> diseases = new ArrayList<>();
        List<MedicationPlan> activePlans = List.of();
        if (!communityElderIds.isEmpty()) {
            activePlans = medicationPlanMapper.selectList(
                    new LambdaQueryWrapper<MedicationPlan>()
                            .in(MedicationPlan::getElderId, communityElderIds)
                            .eq(MedicationPlan::getStatus, "ACTIVE")
            );
        }

        Map<String, Long> diseaseCountMap = new LinkedHashMap<>();
        diseaseCountMap.put("原发性高血压", 0L);
        diseaseCountMap.put("2型糖尿病", 0L);
        diseaseCountMap.put("冠状动脉硬化", 0L);
        diseaseCountMap.put("退行性关节炎", 0L);
        diseaseCountMap.put("慢性阻塞性肺病", 0L);

        for (MedicationPlan plan : activePlans) {
            String med = plan.getMedicineName();
            if (med == null) continue;
            if (med.contains("压") || med.contains("地平") || med.contains("沙坦") || med.contains("洛尔")) {
                diseaseCountMap.put("原发性高血压", diseaseCountMap.get("原发性高血压") + 1);
            } else if (med.contains("糖") || med.contains("二甲双胍") || med.contains("胰岛素") || med.contains("阿卡波")) {
                diseaseCountMap.put("2型糖尿病", diseaseCountMap.get("2型糖尿病") + 1);
            } else if (med.contains("心") || med.contains("冠") || med.contains("阿司匹林") || med.contains("他汀")) {
                diseaseCountMap.put("冠状动脉硬化", diseaseCountMap.get("冠状动脉硬化") + 1);
            } else if (med.contains("钙") || med.contains("氨基葡萄糖") || med.contains("骨")) {
                diseaseCountMap.put("退行性关节炎", diseaseCountMap.get("退行性关节炎") + 1);
            } else {
                diseaseCountMap.put("慢性阻塞性肺病", diseaseCountMap.get("慢性阻塞性肺病") + 1);
            }
        }

        long totalDiseaseCount = diseaseCountMap.values().stream().mapToLong(Long::longValue).sum();
        long denom = totalDiseaseCount > 0 ? totalDiseaseCount : 1L;

        for (Map.Entry<String, Long> entry : diseaseCountMap.entrySet()) {
            double pct = Math.round((entry.getValue() * 100.0 / denom) * 10.0) / 10.0;
            diseases.add(new DiseaseDistributionVO(entry.getKey() + " (" + pct + "%)", entry.getValue(), pct));
        }
        chartsVO.setDiseaseDistribution(diseases);

        // 2. 服务需求结构 (根据实际工单业务类型聚合统计)
        List<ServiceStructureVO> services = new ArrayList<>();
        LambdaQueryWrapper<WorkOrder> woWrapper = new LambdaQueryWrapper<WorkOrder>()
                .ne(WorkOrder::getStatus, "VOIDED");
        if (communityId != null) {
            woWrapper.eq(WorkOrder::getCommunityId, communityId);
        }
        List<WorkOrder> allOrders = workOrderMapper.selectList(woWrapper);
        Map<String, Long> serviceCountMap = allOrders.stream()
                .collect(Collectors.groupingBy(w -> w.getServiceType() != null ? w.getServiceType() : "OTHER", Collectors.counting()));

        // 标准 4 类核心助老服务需求结构
        Map<String, String> serviceNames = new LinkedHashMap<>();
        serviceNames.put("BATH_ASSIST", "上门助浴照料");
        serviceNames.put("CLEANING", "居家适老保洁");
        serviceNames.put("MEAL_DELIVERY", "社区助餐配送");
        serviceNames.put("MEDICAL_ESCORT", "就医陪诊引导");

        long totalOrdersCount = allOrders.size();
        long orderDenom = totalOrdersCount > 0 ? totalOrdersCount : 1L;

        for (Map.Entry<String, String> def : serviceNames.entrySet()) {
            String typeCode = def.getKey();
            Long count = serviceCountMap.getOrDefault(typeCode, 0L);
            double pct = Math.round((count * 100.0 / orderDenom) * 10.0) / 10.0;
            services.add(new ServiceStructureVO(typeCode, def.getValue() + " (" + pct + "%)", count, pct));
        }
        chartsVO.setServiceStructure(services);

        // 3. 近 7 天工单吞吐与平均响应时长复合趋势 (真实按天汇聚)
        LocalDate now = LocalDate.now();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("MM-dd");
        List<String> dates = new ArrayList<>();
        List<Long> dispatched = new ArrayList<>();
        List<Long> closed = new ArrayList<>();
        List<Double> latencies = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate d = now.minusDays(i);
            dates.add(d.format(df));

            LocalDateTime dayStart = d.atStartOfDay();
            LocalDateTime dayEnd = d.plusDays(1).atStartOfDay();

            long dayDispatched = allOrders.stream()
                    .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(dayStart) && o.getCreatedAt().isBefore(dayEnd))
                    .count();
            dispatched.add(dayDispatched);

            List<WorkOrder> dayClosedOrders = allOrders.stream()
                    .filter(o -> "CLOSED".equalsIgnoreCase(o.getStatus()) && o.getReviewedAt() != null
                            && !o.getReviewedAt().isBefore(dayStart) && o.getReviewedAt().isBefore(dayEnd))
                    .toList();
            closed.add((long) dayClosedOrders.size());

            // 履约平均时长 (服务结束时间 - 服务开始时间，单位分钟)
            double avgMinutes = 0.0;
            if (!dayClosedOrders.isEmpty()) {
                long totalMins = 0;
                int countWithDuration = 0;
                for (WorkOrder o : dayClosedOrders) {
                    if (o.getServiceStartTime() != null && o.getServiceFinishTime() != null) {
                        totalMins += Duration.between(o.getServiceStartTime(), o.getServiceFinishTime()).toMinutes();
                        countWithDuration++;
                    }
                }
                if (countWithDuration > 0) {
                    avgMinutes = Math.round((totalMins * 1.0 / countWithDuration) * 10.0) / 10.0;
                }
            }
            latencies.add(avgMinutes);
        }

        chartsVO.setWorkOrderTrend(new WorkOrder7DayTrendVO(dates, dispatched, closed, latencies));

        return chartsVO;
    }

    @Override
    public List<RealtimeAlertFeedVO> getRealtimeAlerts(Long communityId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<RealtimeAlertFeedVO> list = new ArrayList<>();

        List<Long> communityElderIds = getElderIdsForCommunity(communityId);
        if (communityElderIds.isEmpty()) {
            return list;
        }

        // 查询数据库中最近的健康告警 (按社区老人隔离)
        LambdaQueryWrapper<HealthAlert> alertWrapper = new LambdaQueryWrapper<HealthAlert>()
                .in(HealthAlert::getElderId, communityElderIds)
                .orderByDesc(HealthAlert::getCreatedAt)
                .last("LIMIT " + safeLimit);
        List<HealthAlert> alerts = healthAlertMapper.selectList(alertWrapper);

        for (HealthAlert alert : alerts) {
            RealtimeAlertFeedVO item = new RealtimeAlertFeedVO();
            item.setAlertId(alert.getAlertId());
            item.setTime(alert.getCreatedAt() != null ? alert.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "刚刚");
            item.setLevel(alert.getAlertLevel() != null ? alert.getAlertLevel() : "HIGH");
            item.setTagType("HIGH".equalsIgnoreCase(alert.getAlertLevel()) ? "danger" : "warning");
            item.setTagText("体征阈值预警");

            ElderProfile elder = elderProfileMapper.selectById(alert.getElderId());
            if (elder != null) {
                item.setElderName(elder.getName());
                item.setAddress(elder.getAddress());
            } else {
                item.setElderName("长辈");
                item.setAddress("社区辖区内");
            }

            item.setDescription("生命体征检测异常，触发预警规则");
            item.setHandler(alert.getHandledBy() != null ? "工作人员 (ID: " + alert.getHandledBy() + ")" : "待分配处理人");
            item.setStatus(alert.getStatus());
            item.setStatusText("CLOSED".equalsIgnoreCase(alert.getStatus()) ? "已处置办结" : "处置中");
            list.add(item);
        }

        // 查询数据库中最近的 SOS 告警 (真实数据)
        LambdaQueryWrapper<SosEvent> sosWrapper = new LambdaQueryWrapper<SosEvent>()
                .orderByDesc(SosEvent::getOccurredAt)
                .last("LIMIT " + safeLimit);
        List<SosEvent> sosList = sosEventMapper.selectList(sosWrapper);

        for (SosEvent sos : sosList) {
            RealtimeAlertFeedVO item = new RealtimeAlertFeedVO();
            item.setAlertId(10000L + sos.getSosId());
            item.setTime(sos.getOccurredAt() != null ? sos.getOccurredAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "刚刚");
            item.setLevel("HIGH");
            item.setTagType("CANCELLED".equalsIgnoreCase(sos.getStatus()) ? "warning" : "danger");
            item.setTagText("CANCELLED".equalsIgnoreCase(sos.getStatus()) ? "SOS 求助带痕取消" : "紧急 SOS 求助");

            ElderProfile elder = elderProfileMapper.selectById(sos.getElderId());
            if (elder != null) {
                item.setElderName(elder.getName());
                item.setAddress(elder.getAddress());
            } else {
                item.setElderName("长辈");
                item.setAddress(sos.getLocationAddress() != null ? sos.getLocationAddress() : "社区辖区内");
            }

            if ("CANCELLED".equalsIgnoreCase(sos.getStatus())) {
                item.setDescription("长按触发 SOS 后于短时间内取消，系统判定误触保留记录");
                item.setStatus("PENDING_CALLBACK");
                item.setStatusText("待回访电话确认");
            } else {
                item.setDescription("触发紧急 SOS 求助，已联动生成应急抢险工单");
                item.setStatus("PROCESSING");
                item.setStatusText("急救工单处置中");
            }
            item.setHandler("社区网格责任组");
            list.add(item);
        }

        // 按发生时间降序排序并截取 safeLimit
        list.sort((a, b) -> String.valueOf(b.getTime()).compareTo(String.valueOf(a.getTime())));
        if (list.size() > safeLimit) {
            return list.subList(0, safeLimit);
        }
        return list;
    }
}
