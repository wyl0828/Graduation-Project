package cn.communityelderly.modules.emergency.dto;

import jakarta.validation.constraints.NotNull;

public class SosDispatchDTO {

    @NotNull(message = "被指派网格员ID不能为空")
    private Long assigneeId;

    private String dispatchReason = "管理员人工调度派单";

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getDispatchReason() {
        return dispatchReason;
    }

    public void setDispatchReason(String dispatchReason) {
        this.dispatchReason = dispatchReason;
    }
}
