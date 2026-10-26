package cn.communityelderly.modules.health.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class HealthAlertVO {

    private Long alertId;
    private Long elderId;
    private String elderName;
    private Integer elderAge;
    private String elderAddress;
    private String alertCategory;
    private String alertLevel;
    private String status;
    private BigDecimal actualValue;
    private BigDecimal thresholdValue;
    private String metricName;
    private String sourceType;
    private LocalDateTime createdAt;
    private Long handledBy;
    private String handlerName;
    private LocalDateTime handledAt;
    private String handlingResult;

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
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

    public Integer getElderAge() {
        return elderAge;
    }

    public void setElderAge(Integer elderAge) {
        this.elderAge = elderAge;
    }

    public String getElderAddress() {
        return elderAddress;
    }

    public void setElderAddress(String elderAddress) {
        this.elderAddress = elderAddress;
    }

    public String getAlertCategory() {
        return alertCategory;
    }

    public void setAlertCategory(String alertCategory) {
        this.alertCategory = alertCategory;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getActualValue() {
        return actualValue;
    }

    public void setActualValue(BigDecimal actualValue) {
        this.actualValue = actualValue;
    }

    public BigDecimal getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(BigDecimal thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

    public String getHandlerName() {
        return handlerName;
    }

    public void setHandlerName(String handlerName) {
        this.handlerName = handlerName;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public void setHandledAt(LocalDateTime handledAt) {
        this.handledAt = handledAt;
    }

    public String getHandlingResult() {
        return handlingResult;
    }

    public void setHandlingResult(String handlingResult) {
        this.handlingResult = handlingResult;
    }
}
