package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ServiceStopReviewDTO {

    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "(?i)APPROVE|REJECT", message = "审核动作仅支持 APPROVE 或 REJECT")
    private String action;

    @NotBlank(message = "审核意见不能为空")
    @Size(max = 255, message = "审核意见不能超过255个字符")
    private String reviewComment;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }
}
