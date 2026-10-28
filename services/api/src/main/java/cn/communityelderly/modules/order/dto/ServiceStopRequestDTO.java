package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ServiceStopRequestDTO {

    @NotBlank(message = "服务中止原因不能为空")
    @Size(max = 500, message = "服务中止原因不能超过500个字符")
    private String stopReason;

    @NotBlank(message = "已完成内容不能为空")
    @Size(max = 4000, message = "已完成内容不能超过4000个字符")
    private String completedContent;

    @NotBlank(message = "未完成内容不能为空")
    @Size(max = 4000, message = "未完成内容不能超过4000个字符")
    private String incompleteContent;

    @NotBlank(message = "后续安排不能为空")
    @Size(max = 4000, message = "后续安排不能超过4000个字符")
    private String followUpPlan;

    public String getStopReason() {
        return stopReason;
    }

    public void setStopReason(String stopReason) {
        this.stopReason = stopReason;
    }

    public String getCompletedContent() {
        return completedContent;
    }

    public void setCompletedContent(String completedContent) {
        this.completedContent = completedContent;
    }

    public String getIncompleteContent() {
        return incompleteContent;
    }

    public void setIncompleteContent(String incompleteContent) {
        this.incompleteContent = incompleteContent;
    }

    public String getFollowUpPlan() {
        return followUpPlan;
    }

    public void setFollowUpPlan(String followUpPlan) {
        this.followUpPlan = followUpPlan;
    }
}
