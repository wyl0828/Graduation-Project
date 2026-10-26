package cn.communityelderly.modules.medication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class MedicationPlanUpdateDTO {

    private String dosage;

    private String usageMethod;

    @NotEmpty(message = "至少设置一个每日提醒时段")
    private List<MedicationPlanCreateDTO.ReminderTimeItem> reminderTimes;

    @NotBlank(message = "调整用药方案必须如实记录变更原因")
    private String changeReason;

    private Boolean refreshTodayPending = false; // 是否立即刷新今日未开始时段

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getUsageMethod() {
        return usageMethod;
    }

    public void setUsageMethod(String usageMethod) {
        this.usageMethod = usageMethod;
    }

    public List<MedicationPlanCreateDTO.ReminderTimeItem> getReminderTimes() {
        return reminderTimes;
    }

    public void setReminderTimes(List<MedicationPlanCreateDTO.ReminderTimeItem> reminderTimes) {
        this.reminderTimes = reminderTimes;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public Boolean getRefreshTodayPending() {
        return refreshTodayPending;
    }

    public void setRefreshTodayPending(Boolean refreshTodayPending) {
        this.refreshTodayPending = refreshTodayPending;
    }
}
