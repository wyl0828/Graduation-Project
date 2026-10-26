package cn.communityelderly.modules.elder.dto;

import jakarta.validation.constraints.NotNull;

public class KinshipReviewDTO {

    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    private String decisionReason;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }
}
