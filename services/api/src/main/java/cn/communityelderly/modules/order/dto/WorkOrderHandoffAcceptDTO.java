package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.Size;

public class WorkOrderHandoffAcceptDTO {

    @Size(max = 255, message = "交接接收说明不能超过255个字符")
    private String responseComment;

    public String getResponseComment() {
        return responseComment;
    }

    public void setResponseComment(String responseComment) {
        this.responseComment = responseComment;
    }
}
