package cn.communityelderly.modules.medication.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.medication.dto.MedicationComplianceVO;
import cn.communityelderly.modules.medication.dto.MedicationTaskVO;
import cn.communityelderly.modules.medication.entity.MedicationTask;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import cn.communityelderly.modules.medication.service.MedicationComplianceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MedicationComplianceServiceImpl implements MedicationComplianceService {

    private final MedicationTaskMapper taskMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;

    public MedicationComplianceServiceImpl(MedicationTaskMapper taskMapper,
                                           ElderProfileMapper elderProfileMapper,
                                           ObjectAuthorizationPolicy authorizationPolicy) {
        this.taskMapper = taskMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    public MedicationComplianceVO getCompliance(Long elderId, Integer days) {
        authorizationPolicy.requireRole("CHILD", "COMMUNITY_ADMIN", "GRID_WORKER");
        ElderProfile elder = authorizationPolicy.requireElderAccess(elderId);

        int queryDays = (days != null && days > 0) ? days : 30;
        LocalDate startDate = LocalDate.now().minusDays(queryDays);
        LocalDate endDate = LocalDate.now();

        List<MedicationTask> taskList = taskMapper.selectList(
                new LambdaQueryWrapper<MedicationTask>()
                        .eq(MedicationTask::getElderId, elderId)
                        .ge(MedicationTask::getTaskDate, startDate)
                        .le(MedicationTask::getTaskDate, endDate)
        );

        long total = taskList.size();
        long confirmed = taskList.stream().filter(t -> "CONFIRMED".equalsIgnoreCase(t.getStatus())).count();
        long missed = total - confirmed;

        BigDecimal rate;
        if (total == 0) {
            rate = BigDecimal.valueOf(100.0);
        } else {
            rate = BigDecimal.valueOf(confirmed)
                    .multiply(BigDecimal.valueOf(100.0))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        }

        String ratingText;
        if (rate.compareTo(BigDecimal.valueOf(90)) >= 0) {
            ratingText = "优秀遵从";
        } else if (rate.compareTo(BigDecimal.valueOf(80)) >= 0) {
            ratingText = "良好";
        } else {
            ratingText = "需重点关怀";
        }

        MedicationComplianceVO vo = new MedicationComplianceVO();
        vo.setElderId(elderId);
        vo.setElderName(elder.getName());
        vo.setDays(queryDays);
        vo.setExpectedDosesCount(total);
        vo.setConfirmedDosesCount(confirmed);
        vo.setMissedDosesCount(missed);
        vo.setComplianceRate(rate);
        vo.setRatingText(ratingText);

        return vo;
    }

    @Override
    public List<MedicationTaskVO> listCommunityTimeoutElders() {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN", "GRID_WORKER");
        List<Long> accessibleElderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (accessibleElderIds.isEmpty()) return List.of();
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        // 查询今日状态为 OVER_WINDOW_UNCONFIRMED 或已超计划时段2小时未打卡
        List<MedicationTask> timeoutTasks = taskMapper.selectList(
                new LambdaQueryWrapper<MedicationTask>()
                        .in(MedicationTask::getElderId, accessibleElderIds)
                        .eq(MedicationTask::getTaskDate, today)
                        .and(w -> w.eq(MedicationTask::getStatus, "OVER_WINDOW_UNCONFIRMED")
                                   .or(sub -> sub.eq(MedicationTask::getStatus, "PENDING")
                                                 .le(MedicationTask::getScheduledTime, nowTime.minusHours(2))))
                        .orderByDesc(MedicationTask::getScheduledTime)
        );

        if (timeoutTasks.isEmpty()) {
            return List.of();
        }

        List<Long> elderIds = timeoutTasks.stream().map(MedicationTask::getElderId).distinct().toList();
        List<ElderProfile> elders = elderProfileMapper.selectBatchIds(elderIds);
        Map<Long, String> elderNameMap = elders.stream().collect(Collectors.toMap(ElderProfile::getElderId, ElderProfile::getName));

        List<MedicationTaskVO> result = new ArrayList<>();
        for (MedicationTask t : timeoutTasks) {
            MedicationTaskVO vo = new MedicationTaskVO();
            vo.setTaskId(t.getTaskId());
            vo.setElderId(t.getElderId());
            vo.setElderName(elderNameMap.getOrDefault(t.getElderId(), "长辈"));
            vo.setPlanId(t.getPlanId());
            vo.setVersionNo(t.getVersionNo());
            vo.setTaskDate(t.getTaskDate());
            vo.setScheduledTime(t.getScheduledTime());
            vo.setMedicineName(t.getMedicineName());
            vo.setDosage(t.getDosage());
            vo.setStatus("OVER_WINDOW_UNCONFIRMED");
            vo.setIsOverWindow(true);
            vo.setCategoryTag("慢病常用药");
            vo.setTimeLabel("日常服药");
            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordPhoneCare(Long taskId, String careNotes) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN", "GRID_WORKER");
        MedicationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        authorizationPolicy.requireElderInCurrentCommunity(task.getElderId(), "COMMUNITY_ADMIN", "GRID_WORKER");

        // 更新状态或留痕
        task.setStatus("CONFIRMED"); // 电话关怀确认老人已服药或已知晓
        taskMapper.updateById(task);
    }
}
