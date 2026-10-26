package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotNull;

public class WorkOrderDispatchDTO {

    @NotNull(message = "指派处理人账号ID不能为空")
    private Long assigneeUserId;

    private String reason;

    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
