package cn.communityelderly.modules.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ReservationCancelDTO {

    @NotBlank(message = "取消原因不能为空")
    @Size(max = 247, message = "取消原因不能超过247个字符")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
