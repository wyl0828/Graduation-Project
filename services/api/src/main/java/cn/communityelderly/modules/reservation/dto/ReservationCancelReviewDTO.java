package cn.communityelderly.modules.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ReservationCancelReviewDTO {

    @NotBlank(message = "审批决定不能为空")
    @Pattern(regexp = "(?i)APPROVE|REJECT", message = "审批决定仅支持 APPROVE 或 REJECT")
    private String decision;

    @NotBlank(message = "审批意见不能为空")
    @Size(max = 255, message = "审批意见不能超过255个字符")
    private String reviewReason;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReviewReason() {
        return reviewReason;
    }

    public void setReviewReason(String reviewReason) {
        this.reviewReason = reviewReason;
    }
}
