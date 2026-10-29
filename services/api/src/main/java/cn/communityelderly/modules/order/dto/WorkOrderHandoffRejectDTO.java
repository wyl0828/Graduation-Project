package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WorkOrderHandoffRejectDTO {

    @NotBlank(message = "交接拒绝说明不能为空")
    @Size(max = 255, message = "交接拒绝说明不能超过255个字符")
    private String responseComment;

    public String getResponseComment() {
        return responseComment;
    }

    public void setResponseComment(String responseComment) {
        this.responseComment = responseComment;
    }
}
