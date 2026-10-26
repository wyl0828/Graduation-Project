package cn.communityelderly.modules.medication.dto;

import java.math.BigDecimal;

public class MedicationComplianceVO {

    private Long elderId;
    private String elderName;
    private Integer days;
    private Long expectedDosesCount;
    private Long confirmedDosesCount;
    private Long missedDosesCount;
    private BigDecimal complianceRate; // 如 93.3
    private String ratingText; // "优秀遵从", "良好", "需关注"

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

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public Long getExpectedDosesCount() {
        return expectedDosesCount;
    }

    public void setExpectedDosesCount(Long expectedDosesCount) {
        this.expectedDosesCount = expectedDosesCount;
    }

    public Long getConfirmedDosesCount() {
        return confirmedDosesCount;
    }

    public void setConfirmedDosesCount(Long confirmedDosesCount) {
        this.confirmedDosesCount = confirmedDosesCount;
    }

    public Long getMissedDosesCount() {
        return missedDosesCount;
    }

    public void setMissedDosesCount(Long missedDosesCount) {
        this.missedDosesCount = missedDosesCount;
    }

    public BigDecimal getComplianceRate() {
        return complianceRate;
    }

    public void setComplianceRate(BigDecimal complianceRate) {
        this.complianceRate = complianceRate;
    }

    public String getRatingText() {
        return ratingText;
    }

    public void setRatingText(String ratingText) {
        this.ratingText = ratingText;
    }
}
