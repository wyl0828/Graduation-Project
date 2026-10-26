package cn.communityelderly.modules.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AlertHandleDTO {

    @NotBlank(message = "处理结论不能为空")
    @Size(min = 5, max = 500, message = "处理结论至少输入5个字符，详细说明处置依据")
    private String handlingResult;

    private Boolean dispatchWorkOrder = false;

    public String getHandlingResult() {
        return handlingResult;
    }

    public void setHandlingResult(String handlingResult) {
        this.handlingResult = handlingResult;
    }

    public Boolean getDispatchWorkOrder() {
        return dispatchWorkOrder;
    }

    public void setDispatchWorkOrder(Boolean dispatchWorkOrder) {
        this.dispatchWorkOrder = dispatchWorkOrder;
    }
}
