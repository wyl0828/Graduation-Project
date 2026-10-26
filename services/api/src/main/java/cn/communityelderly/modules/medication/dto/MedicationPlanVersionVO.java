package cn.communityelderly.modules.medication.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class MedicationPlanVersionVO {

    private Long versionId;
    private Long planId;
    private Integer versionNo;
    private String medicineName;
    private String dosage;
    private String frequencyType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String changeReason;
    private String creatorName;
    private LocalDateTime createdAt;
    private List<MedicationPlanCreateDTO.ReminderTimeItem> reminderTimes;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
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

    public String getFrequencyType() {
        return frequencyType;
    }

    public void setFrequencyType(String frequencyType) {
        this.frequencyType = frequencyType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getChangeReason() {
        return changeReason;
    }

    public void setChangeReason(String changeReason) {
        this.changeReason = changeReason;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<MedicationPlanCreateDTO.ReminderTimeItem> getReminderTimes() {
        return reminderTimes;
    }

    public void setReminderTimes(List<MedicationPlanCreateDTO.ReminderTimeItem> reminderTimes) {
        this.reminderTimes = reminderTimes;
    }
}
