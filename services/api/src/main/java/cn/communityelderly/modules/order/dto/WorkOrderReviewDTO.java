package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotBlank;

public class WorkOrderReviewDTO {

    @NotBlank(message = "审核动作代码不能为空 (APPROVE/REJECT)")
    private String action;

    @NotBlank(message = "审核意见说明不能为空")
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
