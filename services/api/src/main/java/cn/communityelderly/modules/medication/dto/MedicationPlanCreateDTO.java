package cn.communityelderly.modules.medication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class MedicationPlanCreateDTO {

    @NotNull(message = "老人档案ID不能为空")
    private Long elderId;

    @NotBlank(message = "药品名称不能为空")
    private String medicineName;

    @NotBlank(message = "单次剂量不能为空")
    private String dosage;

    private String usageMethod = "口服";

    private String categoryTag = "常规药"; // 降压药, 降糖药, 心脏药, 常规药

    private String frequencyType = "DAILY";

    @NotEmpty(message = "至少设置一个每日提醒时段")
    private List<ReminderTimeItem> reminderTimes;

    private String changeReason = "初始建立用药方案";

    private Boolean refreshTodayPending = false; // 是否立即刷新今日未开始时段

    public static class ReminderTimeItem {
        private String reminderTime; // "08:00:00" 或 "08:00"
        private String timeLabel;    // "早餐后"

        public String getReminderTime() {
            return reminderTime;
        }

        public void setReminderTime(String reminderTime) {
            this.reminderTime = reminderTime;
        }

        public String getTimeLabel() {
            return timeLabel;
        }

        public void setTimeLabel(String timeLabel) {
            this.timeLabel = timeLabel;
        }
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

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

    public String getCategoryTag() {
        return categoryTag;
    }

    public void setCategoryTag(String categoryTag) {
        this.categoryTag = categoryTag;
    }

    public String getFrequencyType() {
        return frequencyType;
    }

    public void setFrequencyType(String frequencyType) {
        this.frequencyType = frequencyType;
    }

    public List<ReminderTimeItem> getReminderTimes() {
        return reminderTimes;
    }

    public void setReminderTimes(List<ReminderTimeItem> reminderTimes) {
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
