package cn.communityelderly.modules.elder.dto;

import jakarta.validation.constraints.NotNull;

public class CorrectionReviewDTO {

    @NotNull(message = "审核结论不能为空")
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
