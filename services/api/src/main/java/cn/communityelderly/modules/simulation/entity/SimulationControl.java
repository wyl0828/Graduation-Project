package cn.communityelderly.modules.simulation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("simulation_control")
public class SimulationControl {

    @TableId(value = "control_id", type = IdType.AUTO)
    private Long controlId;

    private Long elderId;
    private String vitalType;
    private String status; // RUNNING, STOPPED
    private Integer sampleIntervalSec;
    private BigDecimal baseValuePrimary;
    private BigDecimal baseValueSecondary;
    private BigDecimal volatilityRange;
    private BigDecimal abnormalInjectionRate;
    private LocalDateTime startedAt;
    private LocalDateTime stoppedAt;

    public Long getControlId() {
        return controlId;
    }

    public void setControlId(Long controlId) {
        this.controlId = controlId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getStoppedAt() {
        return stoppedAt;
    }

    public void setStoppedAt(LocalDateTime stoppedAt) {
        this.stoppedAt = stoppedAt;
    }
}
