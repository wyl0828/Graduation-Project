package cn.communityelderly.modules.reservation.dto;

import jakarta.validation.constraints.NotBlank;

public class ReservationCancelDTO {

    @NotBlank(message = "取消原因不能为空")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
