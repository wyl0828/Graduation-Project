package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class WorkOrderHandoffRequestDTO {

    @NotNull(message = "新承接人不能为空")
    @Positive(message = "新承接人账号无效")
    private Long toUserId;

    @NotBlank(message = "责任交接原因不能为空")
    @Size(max = 255, message = "责任交接原因不能超过255个字符")
    private String reason;

    public Long getToUserId() {
        return toUserId;
    }

    public void setToUserId(Long toUserId) {
        this.toUserId = toUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
