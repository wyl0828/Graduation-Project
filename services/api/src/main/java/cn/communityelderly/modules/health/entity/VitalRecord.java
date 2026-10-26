package cn.communityelderly.modules.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("vital_record")
public class VitalRecord {

    @TableId(value = "vital_id", type = IdType.AUTO)
    private Long vitalId;

    private Long elderId;
    private String vitalType;
    private BigDecimal valuePrimary;
    private BigDecimal valueSecondary;
    private String unit;
    private String sourceType;
    private LocalDateTime recordedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private String remark;
    private String idempotencyKey;

    public Long getVitalId() {
        return vitalId;
    }

    public void setVitalId(Long vitalId) {
        this.vitalId = vitalId;
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
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
