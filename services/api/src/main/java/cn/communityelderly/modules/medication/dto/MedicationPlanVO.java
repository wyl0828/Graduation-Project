package cn.communityelderly.modules.medication.dto;

import java.time.LocalDateTime;
import java.util.List;

public class MedicationPlanVO {

    private Long planId;
    private Long elderId;
    private String elderName;
    private String medicineName;
    private String dosage;
    private String usageMethod;
    private String categoryTag;
    private String status;
    private Integer currentVersionNo;
    private List<MedicationPlanCreateDTO.ReminderTimeItem> reminderTimes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getElderName() {
        return elderName;
    }

    public void setElderName(String elderName) {
        this.elderName = elderName;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getCurrentVersionNo() {
        return currentVersionNo;
    }

    public void setCurrentVersionNo(Integer currentVersionNo) {
        this.currentVersionNo = currentVersionNo;
    }

    public List<MedicationPlanCreateDTO.ReminderTimeItem> getReminderTimes() {
        return reminderTimes;
    }

    public void setReminderTimes(List<MedicationPlanCreateDTO.ReminderTimeItem> reminderTimes) {
        this.reminderTimes = reminderTimes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
