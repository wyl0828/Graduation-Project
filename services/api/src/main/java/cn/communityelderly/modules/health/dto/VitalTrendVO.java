package cn.communityelderly.modules.health.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class VitalTrendVO {

    private Long elderId;
    private String elderName;
    private String vitalType;
    private String latestValueText;
    private String latestStatus; // NORMAL, HIGH, LOW
    private LocalDateTime latestRecordedAt;
    private List<DataPoint> dataPoints;

    public static class DataPoint {
        private Long vitalId;
        private LocalDateTime recordedAt;
        private BigDecimal valuePrimary;
        private BigDecimal valueSecondary;
        private String sourceType;
        private Boolean isAbnormal;

        public Long getVitalId() {
            return vitalId;
        }

        public void setVitalId(Long vitalId) {
            this.vitalId = vitalId;
        }

        public LocalDateTime getRecordedAt() {
            return recordedAt;
        }

        public void setRecordedAt(LocalDateTime recordedAt) {
            this.recordedAt = recordedAt;
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

        public String getSourceType() {
            return sourceType;
        }

        public void setSourceType(String sourceType) {
            this.sourceType = sourceType;
        }

        public Boolean getIsAbnormal() {
            return isAbnormal;
        }

        public void setIsAbnormal(Boolean isAbnormal) {
            this.isAbnormal = isAbnormal;
        }
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

    public String getVitalType() {
        return vitalType;
    }

    public void setVitalType(String vitalType) {
        this.vitalType = vitalType;
    }

    public String getLatestValueText() {
        return latestValueText;
    }

    public void setLatestValueText(String latestValueText) {
        this.latestValueText = latestValueText;
    }

    public String getLatestStatus() {
        return latestStatus;
    }

    public void setLatestStatus(String latestStatus) {
        this.latestStatus = latestStatus;
    }

    public LocalDateTime getLatestRecordedAt() {
        return latestRecordedAt;
    }

    public void setLatestRecordedAt(LocalDateTime latestRecordedAt) {
        this.latestRecordedAt = latestRecordedAt;
    }

    public List<DataPoint> getDataPoints() {
        return dataPoints;
    }

    public void setDataPoints(List<DataPoint> dataPoints) {
        this.dataPoints = dataPoints;
    }
}
