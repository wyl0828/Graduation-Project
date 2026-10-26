package cn.communityelderly.modules.medication.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.medication.dto.MedicationCheckinDTO;
import cn.communityelderly.modules.medication.dto.MedicationTaskVO;
import cn.communityelderly.modules.medication.entity.*;
import cn.communityelderly.modules.medication.mapper.*;
import cn.communityelderly.modules.medication.service.MedicationTaskService;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MedicationTaskServiceImpl implements MedicationTaskService {

    private final MedicationTaskMapper taskMapper;
    private final MedicationCheckinMapper checkinMapper;
    private final MedicationPlanMapper planMapper;
    private final MedicationPlanVersionMapper versionMapper;
    private final MedicationPlanReminderTimeMapper reminderTimeMapper;
    private final ElderFamilyLinkMapper familyLinkMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final NotificationService notificationService;

    public MedicationTaskServiceImpl(MedicationTaskMapper taskMapper,
                                     MedicationCheckinMapper checkinMapper,
                                     MedicationPlanMapper planMapper,
                                     MedicationPlanVersionMapper versionMapper,
                                     MedicationPlanReminderTimeMapper reminderTimeMapper,
                                     ElderFamilyLinkMapper familyLinkMapper,
                                     ObjectAuthorizationPolicy authorizationPolicy,
                                     NotificationService notificationService) {
        this.taskMapper = taskMapper;
        this.checkinMapper = checkinMapper;
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
        this.reminderTimeMapper = reminderTimeMapper;
        this.familyLinkMapper = familyLinkMapper;
        this.authorizationPolicy = authorizationPolicy;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MedicationTaskVO> getTodayTasks(Long elderId) {
        Long resolvedElderId = elderId;
        if (resolvedElderId == null) {
            authorizationPolicy.requireRole("ELDER");
            resolvedElderId = authorizationPolicy.requireElderIdentityId();
        }
        ElderProfile elder = authorizationPolicy.requireElderAccess(resolvedElderId);

        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        // 1. 查询今日已存在任务
        List<MedicationTask> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<MedicationTask>()
                        .eq(MedicationTask::getElderId, resolvedElderId)
                        .eq(MedicationTask::getTaskDate, today)
                        .orderByAsc(MedicationTask::getScheduledTime)
        );

        // 2. 如果今日尚未为所有活跃 plan 生成任务快照，就地补充生成
        List<MedicationPlan> activePlans = planMapper.selectList(
                new LambdaQueryWrapper<MedicationPlan>()
                        .eq(MedicationPlan::getElderId, resolvedElderId)
                        .eq(MedicationPlan::getStatus, "ACTIVE")
        );

        for (MedicationPlan plan : activePlans) {
            boolean hasTasks = tasks.stream().anyMatch(t -> t.getPlanId().equals(plan.getPlanId()));
            if (!hasTasks) {
                // 读取当前版本的时段
                MedicationPlanVersion currentVer = versionMapper.selectOne(
                        new LambdaQueryWrapper<MedicationPlanVersion>()
                                .eq(MedicationPlanVersion::getPlanId, plan.getPlanId())
                                .eq(MedicationPlanVersion::getVersionNo, plan.getCurrentVersionNo())
                );
                if (currentVer != null && !currentVer.getStartDate().isAfter(today)) {
                    List<MedicationPlanReminderTime> times = reminderTimeMapper.selectList(
                            new LambdaQueryWrapper<MedicationPlanReminderTime>()
                                    .eq(MedicationPlanReminderTime::getVersionId, currentVer.getVersionId())
                    );
                    for (MedicationPlanReminderTime t : times) {
                        MedicationTask task = new MedicationTask();
                        task.setElderId(resolvedElderId);
                        task.setPlanId(plan.getPlanId());
                        task.setVersionNo(currentVer.getVersionNo());
                        task.setTaskDate(today);
                        task.setScheduledTime(t.getReminderTime());
                        task.setMedicineName(plan.getMedicineName());
                        task.setDosage(currentVer.getDosage());
                        task.setStatus("PENDING");
                        task.setCreatedAt(LocalDateTime.now());
                        taskMapper.insert(task);
                        tasks.add(task);
                    }
                }
            }
        }

        // 3. 超时未服判定与关怀通知联动 (BR-08 / M11)
        List<MedicationTaskVO> result = new ArrayList<>();
        for (MedicationTask t : tasks) {
            boolean isOverWindow = false;
            // 超过计划时间 2 小时未服，且处于 PENDING 态
            if ("PENDING".equalsIgnoreCase(t.getStatus())) {
                if (nowTime.isAfter(t.getScheduledTime().plusHours(2))) {
                    isOverWindow = true;
                    t.setStatus("OVER_WINDOW_UNCONFIRMED");
                    taskMapper.updateById(t);

                    // 触发发布通知给关联的有效子女
                    publishTimeoutCareNotification(t, elder);
                }
            } else if ("OVER_WINDOW_UNCONFIRMED".equalsIgnoreCase(t.getStatus())) {
                isOverWindow = true;
            }

            MedicationTaskVO vo = convertToVO(t, elder.getName(), isOverWindow);
            result.add(vo);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MedicationTaskVO checkin(MedicationCheckinDTO dto) {
        authorizationPolicy.requireRole("ELDER");
        MedicationTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }

        ElderProfile elder = authorizationPolicy.requireElderAccess(task.getElderId());
        String elderName = elder.getName();

        // 1. 幂等检查 (BR-07)
        if ("CONFIRMED".equalsIgnoreCase(task.getStatus())) {
            return convertToVO(task, elderName, false);
        }

        String idempotencyKey = StringUtils.hasText(dto.getIdempotencyKey()) ? dto.getIdempotencyKey().trim() : UUID.randomUUID().toString();
        MedicationCheckin existingCheckin = checkinMapper.selectOne(
                new LambdaQueryWrapper<MedicationCheckin>()
                        .eq(MedicationCheckin::getIdempotencyKey, idempotencyKey)
        );
        if (existingCheckin != null) {
            return convertToVO(task, elderName, false);
        }

        LocalDateTime now = LocalDateTime.now();

        // 2. 插入打卡流水记录
        MedicationCheckin checkin = new MedicationCheckin();
        checkin.setTaskId(task.getTaskId());
        checkin.setElderId(task.getElderId());
        checkin.setCheckinTime(now);
        checkin.setCheckinMode(StringUtils.hasText(dto.getCheckinMode()) ? dto.getCheckinMode() : "SELF_CONFIRMED");
        checkin.setActualStatus("TAKEN");
        checkin.setIdempotencyKey(idempotencyKey);
        checkinMapper.insert(checkin);

        // 3. 更新任务状态为已完成
        task.setStatus("CONFIRMED");
        task.setConfirmedAt(now);
        taskMapper.updateById(task);

        return convertToVO(task, elderName, false);
    }

    private void publishTimeoutCareNotification(MedicationTask task, ElderProfile elder) {
        // 查询该长辈绑定的有效子女
        List<ElderFamilyLink> links = familyLinkMapper.selectList(
                new LambdaQueryWrapper<ElderFamilyLink>()
                        .eq(ElderFamilyLink::getElderId, elder.getElderId())
                        .eq(ElderFamilyLink::getStatus, "ACTIVE")
        );
        if (links.isEmpty()) return;

        List<Long> childUserIds = links.stream().map(ElderFamilyLink::getChildUserId).toList();

        NotificationCreateEventDTO notify = new NotificationCreateEventDTO();
        notify.setPolicyScope("N01_BUSINESS");
        notify.setEventType("MEDICATION_REMINDER");
        notify.setSourceEntityType("MEDICATION_TASK");
        notify.setSourceEntityId(String.valueOf(task.getTaskId()));
        notify.setDedupKey("NOTIFY_MED_TIMEOUT:" + task.getTaskId() + ":" + task.getTaskDate());
        notify.setTitle("【服药超时关怀】" + elder.getName() + "今日" + task.getMedicineName() + "尚未打卡");
        notify.setContent("长辈计划于 " + task.getScheduledTime() + " 服用 " + task.getMedicineName() + " (" + task.getDosage() + ")，目前已超出服药窗口尚未记录打卡，请您及时致电长辈提醒关怀。");
        notify.setRecipientUserIds(childUserIds);

        notificationService.publishEvent(notify);
    }

    private MedicationTaskVO convertToVO(MedicationTask t, String elderName, boolean isOverWindow) {
        MedicationTaskVO vo = new MedicationTaskVO();
        vo.setTaskId(t.getTaskId());
        vo.setElderId(t.getElderId());
        vo.setElderName(elderName);
        vo.setPlanId(t.getPlanId());
        vo.setVersionNo(t.getVersionNo());
        vo.setTaskDate(t.getTaskDate());
        vo.setScheduledTime(t.getScheduledTime());
        vo.setMedicineName(t.getMedicineName());
        vo.setDosage(t.getDosage());
        vo.setStatus(t.getStatus());
        vo.setConfirmedAt(t.getConfirmedAt());
        vo.setIsOverWindow(isOverWindow);

        // 推理标签与用法
        vo.setCategoryTag(inferCategoryTag(t.getMedicineName()));
        vo.setTimeLabel(inferTimeLabel(t.getScheduledTime()));
        vo.setUsageMethod("口服 · 温开水送服");
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

    private String inferTimeLabel(LocalTime time) {
        if (time == null) return "日常服药";
        int hour = time.getHour();
        if (hour < 10) return "早餐后";
        if (hour < 14) return "午餐后";
        if (hour < 19) return "晚餐后";
        return "睡前";
    }

}
