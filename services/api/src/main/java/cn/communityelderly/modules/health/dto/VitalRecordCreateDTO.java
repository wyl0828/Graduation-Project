package cn.communityelderly.modules.health.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class VitalRecordCreateDTO {

    @NotNull(message = "老人档案ID不能为空")
    private Long elderId;

    @NotBlank(message = "体征类型不能为空")
    private String vitalType; // BLOOD_PRESSURE, BLOOD_SUGAR, HEART_RATE, TEMPERATURE

    @NotNull(message = "主测量值不能为空")
    @DecimalMin(value = "0.01", message = "测量值必须大于0")
    private BigDecimal valuePrimary;

    private BigDecimal valueSecondary;

    @NotBlank(message = "单位不能为空")
    private String unit; // mmHg, mmol/L, bpm, ℃

    private String sourceType = "MANUAL"; // MANUAL, SIMULATED

    private LocalDateTime recordedAt;

    private String remark;

    private String idempotencyKey;

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

    public BigDecimal getValuePrimary() {
        return valuePrimary;
    }

    public void setValuePrimary(BigDecimal valuePrimary) {
        this.valuePrimary = valuePrimary;
    }

    public BigDecimal getValueSecondary() {
        return valueSecondary;
    }

    public void setValueSecondary(BigDecimal valueSecondary) {
        this.valueSecondary = valueSecondary;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt != null ? recordedAt : LocalDateTime.now();
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
