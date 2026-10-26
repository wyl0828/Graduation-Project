package cn.communityelderly.modules.simulation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SingleInjectDTO {

    @NotNull(message = "目标老人ID不能为空")
    private Long elderId;

    private String vitalType = "BLOOD_PRESSURE";

    @NotNull(message = "主测量值不能为空")
    @DecimalMin(value = "0.01")
    private BigDecimal valuePrimary;

    private BigDecimal valueSecondary;

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
}
