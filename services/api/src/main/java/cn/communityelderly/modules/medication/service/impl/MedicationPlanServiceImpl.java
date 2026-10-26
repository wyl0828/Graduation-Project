package cn.communityelderly.modules.medication.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.medication.dto.*;
import cn.communityelderly.modules.medication.entity.MedicationPlan;
import cn.communityelderly.modules.medication.entity.MedicationPlanReminderTime;
import cn.communityelderly.modules.medication.entity.MedicationPlanVersion;
import cn.communityelderly.modules.medication.entity.MedicationTask;
import cn.communityelderly.modules.medication.mapper.MedicationPlanMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanReminderTimeMapper;
import cn.communityelderly.modules.medication.mapper.MedicationPlanVersionMapper;
import cn.communityelderly.modules.medication.mapper.MedicationTaskMapper;
import cn.communityelderly.modules.medication.service.MedicationPlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MedicationPlanServiceImpl implements MedicationPlanService {

    private final MedicationPlanMapper planMapper;
    private final MedicationPlanVersionMapper versionMapper;
    private final MedicationPlanReminderTimeMapper reminderTimeMapper;
    private final MedicationTaskMapper taskMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final UserAccountMapper userAccountMapper;

    public MedicationPlanServiceImpl(MedicationPlanMapper planMapper,
                                     MedicationPlanVersionMapper versionMapper,
                                     MedicationPlanReminderTimeMapper reminderTimeMapper,
                                     MedicationTaskMapper taskMapper,
                                     ObjectAuthorizationPolicy authorizationPolicy,
                                     UserAccountMapper userAccountMapper) {
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
        this.reminderTimeMapper = reminderTimeMapper;
        this.taskMapper = taskMapper;
        this.authorizationPolicy = authorizationPolicy;
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MedicationPlanVO createPlan(MedicationPlanCreateDTO dto) {
        authorizationPolicy.requireRole("CHILD");
        authorizationPolicy.requireElderAccess(dto.getElderId());
        Long userId = Long.parseLong(authorizationPolicy.requireCurrentUser().getUserId());
        LocalDateTime now = LocalDateTime.now();

        // 1. 插入计划主表
        MedicationPlan plan = new MedicationPlan();
        plan.setElderId(dto.getElderId());
        plan.setMedicineName(dto.getMedicineName().trim());
        plan.setDosage(dto.getDosage().trim());
        plan.setUsageMethod(StringUtils.hasText(dto.getUsageMethod()) ? dto.getUsageMethod().trim() : "口服");
        plan.setStatus("ACTIVE");
        plan.setCurrentVersionNo(1);
        plan.setCreatedByUserId(userId);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planMapper.insert(plan);

        // 2. 插入首个版本表 (v1)
        LocalDate startDate = Boolean.TRUE.equals(dto.getRefreshTodayPending()) ? LocalDate.now() : LocalDate.now().plusDays(1);
        MedicationPlanVersion version = new MedicationPlanVersion();
        version.setPlanId(plan.getPlanId());
        version.setVersionNo(1);
        version.setMedicineName(plan.getMedicineName());
        version.setDosage(plan.getDosage());
        version.setFrequencyType(StringUtils.hasText(dto.getFrequencyType()) ? dto.getFrequencyType() : "DAILY");
        version.setStartDate(startDate);
        version.setChangeReason(StringUtils.hasText(dto.getChangeReason()) ? dto.getChangeReason() : "初始建立用药方案");
        version.setCreatedByUserId(userId);
        version.setCreatedAt(now);
        versionMapper.insert(version);

        // 3. 插入各提醒时段
        saveReminderTimes(version.getVersionId(), dto.getReminderTimes());

        // 4. 若勾选当日生效且有提醒时段，立即生成今日未到期任务快照
        if (Boolean.TRUE.equals(dto.getRefreshTodayPending())) {
            refreshTodayPendingTasks(plan, version, dto.getReminderTimes());
        }

        return getPlanDetail(plan.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MedicationPlanVO updatePlan(Long planId, MedicationPlanUpdateDTO dto) {
        authorizationPolicy.requireRole("CHILD");
        MedicationPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }

        authorizationPolicy.requireElderAccess(plan.getElderId());

        Long userId = Long.parseLong(authorizationPolicy.requireCurrentUser().getUserId());
        LocalDateTime now = LocalDateTime.now();

        int newVersionNo = plan.getCurrentVersionNo() + 1;
        String newDosage = StringUtils.hasText(dto.getDosage()) ? dto.getDosage().trim() : plan.getDosage();
        String newUsageMethod = StringUtils.hasText(dto.getUsageMethod()) ? dto.getUsageMethod().trim() : plan.getUsageMethod();

        LocalDate startDate = Boolean.TRUE.equals(dto.getRefreshTodayPending()) ? LocalDate.now() : LocalDate.now().plusDays(1);

        // 1. 生成新版本记录 (版本不可逆覆盖)
        MedicationPlanVersion newVersion = new MedicationPlanVersion();
        newVersion.setPlanId(plan.getPlanId());
        newVersion.setVersionNo(newVersionNo);
        newVersion.setMedicineName(plan.getMedicineName());
        newVersion.setDosage(newDosage);
        newVersion.setFrequencyType("DAILY");
        newVersion.setStartDate(startDate);
        newVersion.setChangeReason(dto.getChangeReason().trim());
        newVersion.setCreatedByUserId(userId);
        newVersion.setCreatedAt(now);
        versionMapper.insert(newVersion);

        // 2. 插入新版时段
        saveReminderTimes(newVersion.getVersionId(), dto.getReminderTimes());

        // 3. 更新主表状态
        plan.setDosage(newDosage);
        plan.setUsageMethod(newUsageMethod);
        plan.setCurrentVersionNo(newVersionNo);
        plan.setUpdatedAt(now);
        planMapper.updateById(plan);

        // 4. 当日应急刷新逻辑 (用户特别要求)
        if (Boolean.TRUE.equals(dto.getRefreshTodayPending())) {
            refreshTodayPendingTasks(plan, newVersion, dto.getReminderTimes());
        }

        return getPlanDetail(plan.getPlanId());
    }

    @Override
    public List<MedicationPlanVO> listPlans(Long elderId) {
        authorizationPolicy.requireRole("CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER");
        ElderProfile elder = authorizationPolicy.requireElderAccess(elderId);

        List<MedicationPlan> plans = planMapper.selectList(
                new LambdaQueryWrapper<MedicationPlan>()
                        .eq(MedicationPlan::getElderId, elderId)
                        .orderByDesc(MedicationPlan::getCreatedAt)
        );

        String elderName = elder.getName();

        List<MedicationPlanVO> result = new ArrayList<>();
        for (MedicationPlan p : plans) {
            MedicationPlanVO vo = convertToVO(p, elderName);
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<MedicationPlanVersionVO> listPlanVersions(Long planId) {
        authorizationPolicy.requireRole("CHILD", "COMMUNITY_ADMIN", "GRID_WORKER");
        MedicationPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        authorizationPolicy.requireElderAccess(plan.getElderId());

        List<MedicationPlanVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<MedicationPlanVersion>()
                        .eq(MedicationPlanVersion::getPlanId, planId)
                        .orderByDesc(MedicationPlanVersion::getVersionNo)
        );

        List<Long> creatorIds = versions.stream().map(MedicationPlanVersion::getCreatedByUserId).distinct().toList();
        List<UserAccount> users = userAccountMapper.selectBatchIds(creatorIds);
        Map<Long, String> userMap = users.stream().collect(Collectors.toMap(UserAccount::getUserId, u -> u.getPhoneNormalized() != null ? u.getPhoneNormalized() : "工作人员", (k1, k2) -> k1));

        List<MedicationPlanVersionVO> result = new ArrayList<>();
        for (MedicationPlanVersion v : versions) {
            MedicationPlanVersionVO vo = new MedicationPlanVersionVO();
            vo.setVersionId(v.getVersionId());
            vo.setPlanId(v.getPlanId());
            vo.setVersionNo(v.getVersionNo());
            vo.setMedicineName(v.getMedicineName());
            vo.setDosage(v.getDosage());
            vo.setFrequencyType(v.getFrequencyType());
            vo.setStartDate(v.getStartDate());
            vo.setEndDate(v.getEndDate());
            vo.setChangeReason(v.getChangeReason());
            vo.setCreatorName(userMap.getOrDefault(v.getCreatedByUserId(), "工作人员"));
            vo.setCreatedAt(v.getCreatedAt());

            List<MedicationPlanReminderTime> times = reminderTimeMapper.selectList(
                    new LambdaQueryWrapper<MedicationPlanReminderTime>()
                            .eq(MedicationPlanReminderTime::getVersionId, v.getVersionId())
                            .orderByAsc(MedicationPlanReminderTime::getReminderTime)
            );
            vo.setReminderTimes(times.stream().map(t -> {
                MedicationPlanCreateDTO.ReminderTimeItem item = new MedicationPlanCreateDTO.ReminderTimeItem();
                item.setReminderTime(t.getReminderTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                item.setTimeLabel(t.getTimeLabel());
                return item;
            }).toList());

            result.add(vo);
        }
        return result;
    }

    @Override
    public MedicationPlanVO getPlanDetail(Long planId) {
        MedicationPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        ElderProfile elder = authorizationPolicy.requireElderAccess(plan.getElderId());

        return convertToVO(plan, elder.getName());
    }

    private void saveReminderTimes(Long versionId, List<MedicationPlanCreateDTO.ReminderTimeItem> items) {
        if (items == null) return;
        for (MedicationPlanCreateDTO.ReminderTimeItem item : items) {
            MedicationPlanReminderTime t = new MedicationPlanReminderTime();
            t.setVersionId(versionId);
            t.setReminderTime(parseTime(item.getReminderTime()));
            t.setTimeLabel(StringUtils.hasText(item.getTimeLabel()) ? item.getTimeLabel() : "常规服药");
            reminderTimeMapper.insert(t);
        }
    }

    private void refreshTodayPendingTasks(MedicationPlan plan, MedicationPlanVersion version, List<MedicationPlanCreateDTO.ReminderTimeItem> items) {
        LocalDate today = LocalDate.now();

        // 1. 查询今日已存在的任务
        List<MedicationTask> todayTasks = taskMapper.selectList(
                new LambdaQueryWrapper<MedicationTask>()
                        .eq(MedicationTask::getPlanId, plan.getPlanId())
                        .eq(MedicationTask::getTaskDate, today)
        );

        // 2. 区分已打卡与未开始
        // 已打卡 (CONFIRMED) 的任务绝对保留，不可篡改
        // 待服 (PENDING) 的任务物理删除，由新版本重新生成
        for (MedicationTask t : todayTasks) {
            if ("PENDING".equalsIgnoreCase(t.getStatus())) {
                taskMapper.deleteById(t.getTaskId());
            }
        }

        // 3. 重新插入新版未开始任务
        LocalDateTime now = LocalDateTime.now();
        for (MedicationPlanCreateDTO.ReminderTimeItem item : items) {
            LocalTime scheduledTime = parseTime(item.getReminderTime());

            // 检查是否已有已打卡任务占用该时刻
            boolean alreadyConfirmed = todayTasks.stream()
                    .anyMatch(t -> "CONFIRMED".equalsIgnoreCase(t.getStatus()) && t.getScheduledTime().equals(scheduledTime));
            if (alreadyConfirmed) {
                continue;
            }

            MedicationTask newTask = new MedicationTask();
            newTask.setElderId(plan.getElderId());
            newTask.setPlanId(plan.getPlanId());
            newTask.setVersionNo(version.getVersionNo());
            newTask.setTaskDate(today);
            newTask.setScheduledTime(scheduledTime);
            newTask.setMedicineName(plan.getMedicineName());
            newTask.setDosage(version.getDosage());
            newTask.setStatus("PENDING");
            newTask.setCreatedAt(now);
            taskMapper.insert(newTask);
        }
    }

    private MedicationPlanVO convertToVO(MedicationPlan plan, String elderName) {
        MedicationPlanVO vo = new MedicationPlanVO();
        vo.setPlanId(plan.getPlanId());
        vo.setElderId(plan.getElderId());
        vo.setElderName(elderName);
        vo.setMedicineName(plan.getMedicineName());
        vo.setDosage(plan.getDosage());
        vo.setUsageMethod(plan.getUsageMethod());
        vo.setStatus(plan.getStatus());
        vo.setCurrentVersionNo(plan.getCurrentVersionNo());
        vo.setCreatedAt(plan.getCreatedAt());
        vo.setUpdatedAt(plan.getUpdatedAt());

        // 解析通俗标签
        vo.setCategoryTag(inferCategoryTag(plan.getMedicineName()));

        // 查询当前版本的时段
        MedicationPlanVersion currentVer = versionMapper.selectOne(
                new LambdaQueryWrapper<MedicationPlanVersion>()
                        .eq(MedicationPlanVersion::getPlanId, plan.getPlanId())
                        .eq(MedicationPlanVersion::getVersionNo, plan.getCurrentVersionNo())
        );
        if (currentVer != null) {
            List<MedicationPlanReminderTime> times = reminderTimeMapper.selectList(
                    new LambdaQueryWrapper<MedicationPlanReminderTime>()
                            .eq(MedicationPlanReminderTime::getVersionId, currentVer.getVersionId())
                            .orderByAsc(MedicationPlanReminderTime::getReminderTime)
            );
            vo.setReminderTimes(times.stream().map(t -> {
                MedicationPlanCreateDTO.ReminderTimeItem item = new MedicationPlanCreateDTO.ReminderTimeItem();
                item.setReminderTime(t.getReminderTime().format(DateTimeFormatter.ofPattern("HH:mm")));
                item.setTimeLabel(t.getTimeLabel());
                return item;
            }).toList());
        }

        return vo;
    }

    private String inferCategoryTag(String medicineName) {
        if (!StringUtils.hasText(medicineName)) return "常规药";
        if (medicineName.contains("压") || medicineName.contains("地平") || medicineName.contains("沙坦") || medicineName.contains("普利") || medicineName.contains("洛尔")) {
            return "降压药";
        }
        if (medicineName.contains("糖") || medicineName.contains("二甲双胍") || medicineName.contains("格列") || medicineName.contains("阿卡波")) {
            return "降糖药";
        }
        if (medicineName.contains("心") || medicineName.contains("阿司匹林") || medicineName.contains("丹参") || medicineName.contains("硝酸甘油")) {
            return "心脏药";
        }
        if (medicineName.contains("胃") || medicineName.contains("奥美拉唑") || medicineName.contains("铝碳酸镁")) {
            return "胃药";
        }
        return "常规药";
    }

    private LocalTime parseTime(String timeStr) {
        if (!StringUtils.hasText(timeStr)) return LocalTime.of(8, 0);
        String s = timeStr.trim();
        if (s.length() == 5) {
            s += ":00";
        }
        return LocalTime.parse(s);
    }

}
