package cn.communityelderly.modules.health.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("assessment_task")
public class AssessmentTask {

    @TableId(value = "task_id", type = IdType.AUTO)
    private Long taskId;

    private Long vitalId;
    private Long ruleVersionId;
    private String status;
    private Integer attemptCount;
    private LocalDateTime assessedAt;
    private String errorMessage;
    private LocalDateTime createdAt;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getVitalId() {
        return vitalId;
    }

    public void setVitalId(Long vitalId) {
        this.vitalId = vitalId;
    }

    public Long getRuleVersionId() {
        return ruleVersionId;
    }

    public void setRuleVersionId(Long ruleVersionId) {
        this.ruleVersionId = ruleVersionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(LocalDateTime assessedAt) {
        this.assessedAt = assessedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
