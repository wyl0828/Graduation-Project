package cn.communityelderly.modules.health.dto;

import jakarta.validation.constraints.NotNull;

public class VitalTrendQueryDTO {

    @NotNull(message = "老人档案ID不能为空")
    private Long elderId;

    private String vitalType = "BLOOD_PRESSURE"; // BLOOD_PRESSURE, BLOOD_SUGAR, HEART_RATE

    private Integer days = 7; // 7, 30

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getVitalType() {
        return vitalType;
    }

    public void setVitalType(String vitalType) {
        this.vitalType = vitalType;
    }

    public Integer getDays() {
        return days != null && days > 0 ? days : 7;
    }

    public void setDays(Integer days) {
        this.days = days;
    }
}
