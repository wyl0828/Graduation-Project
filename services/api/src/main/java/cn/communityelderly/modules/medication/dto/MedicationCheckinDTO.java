package cn.communityelderly.modules.medication.dto;

import jakarta.validation.constraints.NotNull;

public class MedicationCheckinDTO {

    @NotNull(message = "服药任务ID不能为空")
    private Long taskId;

    private String checkinMode = "SELF_CONFIRMED"; // SELF_CONFIRMED, VOICE_CONFIRMED

    private String idempotencyKey;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getCheckinMode() {
        return checkinMode;
    }

    public void setCheckinMode(String checkinMode) {
        this.checkinMode = checkinMode;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
