package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotBlank;

public class WorkOrderActionDTO {

    @NotBlank(message = "操作动作代码不能为空 (ACCEPT/REJECT/START)")
    private String action;

    private String comment;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
