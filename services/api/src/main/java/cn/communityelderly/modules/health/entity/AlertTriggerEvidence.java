package cn.communityelderly.modules.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("alert_trigger_evidence")
public class AlertTriggerEvidence {

    @TableId(value = "evidence_id", type = IdType.AUTO)
    private Long evidenceId;

    private Long alertId;
    private Long assessmentTaskId;
    private String metricName;
    private BigDecimal actualValue;
    private BigDecimal thresholdValue;
    private String evidenceJson;

    public Long getEvidenceId() {
        return evidenceId;
    }

    public void setEvidenceId(Long evidenceId) {
        this.evidenceId = evidenceId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public Long getAssessmentTaskId() {
        return assessmentTaskId;
    }

    public void setAssessmentTaskId(Long assessmentTaskId) {
        this.assessmentTaskId = assessmentTaskId;
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
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

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public void setEvidenceJson(String evidenceJson) {
        this.evidenceJson = evidenceJson;
    }
}
