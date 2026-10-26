package cn.communityelderly.modules.simulation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SimulationConfigDTO {

    @NotNull(message = "目标老人ID不能为空")
    private Long elderId;

    private String vitalType = "BLOOD_PRESSURE";

    @Min(value = 5, message = "采样间隔最低不得小于5秒，防止数据库洪泛")
    private Integer sampleIntervalSec = 10;

    @DecimalMin(value = "50.0", message = "基准值偏离生理范围")
    private BigDecimal baseValuePrimary = BigDecimal.valueOf(130.00);

    private BigDecimal baseValueSecondary = BigDecimal.valueOf(82.00);

    private BigDecimal volatilityRange = BigDecimal.valueOf(5.00);

    private BigDecimal abnormalInjectionRate = BigDecimal.valueOf(0.10);

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

    public Integer getSampleIntervalSec() {
        return sampleIntervalSec;
    }

    public void setSampleIntervalSec(Integer sampleIntervalSec) {
        this.sampleIntervalSec = sampleIntervalSec;
    }

    public BigDecimal getBaseValuePrimary() {
        return baseValuePrimary;
    }

    public void setBaseValuePrimary(BigDecimal baseValuePrimary) {
        this.baseValuePrimary = baseValuePrimary;
    }

    public BigDecimal getBaseValueSecondary() {
        return baseValueSecondary;
    }

    public void setBaseValueSecondary(BigDecimal baseValueSecondary) {
        this.baseValueSecondary = baseValueSecondary;
    }

    public BigDecimal getVolatilityRange() {
        return volatilityRange;
    }

    public void setVolatilityRange(BigDecimal volatilityRange) {
        this.volatilityRange = volatilityRange;
    }

    public BigDecimal getAbnormalInjectionRate() {
        return abnormalInjectionRate;
    }

    public void setAbnormalInjectionRate(BigDecimal abnormalInjectionRate) {
        this.abnormalInjectionRate = abnormalInjectionRate;
    }
}
