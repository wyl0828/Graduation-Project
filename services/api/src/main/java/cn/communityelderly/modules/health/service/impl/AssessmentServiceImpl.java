package cn.communityelderly.modules.health.service.impl;

import cn.communityelderly.modules.health.entity.*;
import cn.communityelderly.modules.health.mapper.*;
import cn.communityelderly.modules.health.service.AssessmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class AssessmentServiceImpl implements AssessmentService {

    private final AlertRuleVersionMapper ruleVersionMapper;
    private final AssessmentTaskMapper assessmentTaskMapper;
    private final HealthAlertMapper healthAlertMapper;
    private final AlertTriggerEvidenceMapper evidenceMapper;

    public AssessmentServiceImpl(AlertRuleVersionMapper ruleVersionMapper,
                                 AssessmentTaskMapper assessmentTaskMapper,
                                 HealthAlertMapper healthAlertMapper,
                                 AlertTriggerEvidenceMapper evidenceMapper) {
        this.ruleVersionMapper = ruleVersionMapper;
        this.assessmentTaskMapper = assessmentTaskMapper;
        this.healthAlertMapper = healthAlertMapper;
        this.evidenceMapper = evidenceMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HealthAlert evaluateVital(VitalRecord vital) {
        LocalDateTime now = LocalDateTime.now();

        // 1. 查找当前生效的规则版本
        AlertRuleVersion rule = ruleVersionMapper.selectOne(
                new LambdaQueryWrapper<AlertRuleVersion>()
                        .eq(AlertRuleVersion::getVitalType, vital.getVitalType())
                        .eq(AlertRuleVersion::getStatus, "ACTIVE")
                        .orderByDesc(AlertRuleVersion::getRuleVersionId)
                        .last("LIMIT 1")
        );

        Long ruleVersionId = rule != null ? rule.getRuleVersionId() : 1L;

        // 2. 插入 assessment_task，锁定当时的规则版本
        AssessmentTask task = new AssessmentTask();
        task.setVitalId(vital.getVitalId());
        task.setRuleVersionId(ruleVersionId);
        task.setStatus("RUNNING");
        task.setAttemptCount(1);
        task.setCreatedAt(now);
        assessmentTaskMapper.insert(task);

        // 3. 执行阈值判定
        EvaluationResult eval = evaluateThreshold(vital);

        HealthAlert alert = null;
        if (eval.isAlert()) {
            alert = new HealthAlert();
            alert.setElderId(vital.getElderId());
            alert.setVitalId(vital.getVitalId());
            alert.setAssessmentTaskId(task.getTaskId());
            alert.setAlertCategory(eval.getCategory());
            alert.setAlertLevel(eval.getLevel());
            alert.setStatus("PENDING");
            alert.setCreatedAt(now);
            alert.setUpdatedAt(now);
            healthAlertMapper.insert(alert);

            // 固化证据快照
            AlertTriggerEvidence evidence = new AlertTriggerEvidence();
            evidence.setAlertId(alert.getAlertId());
            evidence.setAssessmentTaskId(task.getTaskId());
            evidence.setMetricName(eval.getMetricName());
            evidence.setActualValue(eval.getActualValue());
            evidence.setThresholdValue(eval.getThresholdValue());
            evidence.setEvidenceJson(String.format("{\"ruleVersionId\":%d,\"vitalType\":\"%s\",\"actualPrimary\":%s,\"actualSecondary\":%s,\"level\":\"%s\"}",
                    ruleVersionId, vital.getVitalType(), vital.getValuePrimary(), vital.getValueSecondary(), eval.getLevel()));
            evidenceMapper.insert(evidence);
        }

        task.setStatus("SUCCEEDED");
        task.setAssessedAt(now);
        assessmentTaskMapper.updateById(task);

        return alert;
    }

    private EvaluationResult evaluateThreshold(VitalRecord vital) {
        String type = vital.getVitalType();
        BigDecimal pVal = vital.getValuePrimary();
        BigDecimal sVal = vital.getValueSecondary();

        if ("BLOOD_PRESSURE".equalsIgnoreCase(type)) {
            // 收缩压 (高压) >= 160 或 舒张压 (低压) >= 100: HIGH
            if ((pVal != null && pVal.compareTo(BigDecimal.valueOf(160)) >= 0) ||
                (sVal != null && sVal.compareTo(BigDecimal.valueOf(100)) >= 0)) {
                return new EvaluationResult(true, "HIGH_BLOOD_PRESSURE", "HIGH", "收缩压/舒张压",
                        pVal != null ? pVal : sVal, BigDecimal.valueOf(160.00));
            }
            // 收缩压 >= 140 或 舒张压 >= 90: MEDIUM
            if ((pVal != null && pVal.compareTo(BigDecimal.valueOf(140)) >= 0) ||
                (sVal != null && sVal.compareTo(BigDecimal.valueOf(90)) >= 0)) {
                return new EvaluationResult(true, "HIGH_BLOOD_PRESSURE", "MEDIUM", "收缩压/舒张压",
                        pVal != null ? pVal : sVal, BigDecimal.valueOf(140.00));
            }
            // 收缩压 < 90: LOW
            if (pVal != null && pVal.compareTo(BigDecimal.valueOf(90)) < 0) {
                return new EvaluationResult(true, "LOW_BLOOD_PRESSURE", "LOW", "收缩压偏低",
                        pVal, BigDecimal.valueOf(90.00));
            }
        } else if ("BLOOD_SUGAR".equalsIgnoreCase(type)) {
            if (pVal != null && pVal.compareTo(BigDecimal.valueOf(11.1)) >= 0) {
                return new EvaluationResult(true, "HIGH_BLOOD_SUGAR", "HIGH", "血糖严重超标",
                        pVal, BigDecimal.valueOf(11.10));
            }
            if (pVal != null && pVal.compareTo(BigDecimal.valueOf(7.8)) >= 0) {
                return new EvaluationResult(true, "HIGH_BLOOD_SUGAR", "MEDIUM", "血糖偏高",
                        pVal, BigDecimal.valueOf(7.80));
            }
            if (pVal != null && pVal.compareTo(BigDecimal.valueOf(3.9)) < 0) {
                return new EvaluationResult(true, "LOW_BLOOD_SUGAR", "HIGH", "低血糖危险",
                        pVal, BigDecimal.valueOf(3.90));
            }
        } else if ("HEART_RATE".equalsIgnoreCase(type)) {
            if (pVal != null && pVal.compareTo(BigDecimal.valueOf(100)) > 0) {
                return new EvaluationResult(true, "ARRHYTHMIA", "MEDIUM", "心率过速",
                        pVal, BigDecimal.valueOf(100.00));
            }
            if (pVal != null && pVal.compareTo(BigDecimal.valueOf(50)) < 0) {
                return new EvaluationResult(true, "ARRHYTHMIA", "MEDIUM", "心率过缓",
                        pVal, BigDecimal.valueOf(50.00));
            }
        }

        return new EvaluationResult(false, null, null, null, null, null);
    }

    private static class EvaluationResult {
        private final boolean isAlert;
        private final String category;
        private final String level;
        private final String metricName;
        private final BigDecimal actualValue;
        private final BigDecimal thresholdValue;

        public EvaluationResult(boolean isAlert, String category, String level, String metricName, BigDecimal actualValue, BigDecimal thresholdValue) {
            this.isAlert = isAlert;
            this.category = category;
            this.level = level;
            this.metricName = metricName;
            this.actualValue = actualValue;
            this.thresholdValue = thresholdValue;
        }

        public boolean isAlert() {
            return isAlert;
        }

        public String getCategory() {
            return category;
        }

        public String getLevel() {
            return level;
        }

        public String getMetricName() {
            return metricName;
        }

        public BigDecimal getActualValue() {
            return actualValue;
        }

        public BigDecimal getThresholdValue() {
            return thresholdValue;
        }
    }
}
