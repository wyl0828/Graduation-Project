package cn.communityelderly.modules.elder.dto;

import jakarta.validation.constraints.NotBlank;

public class KinshipFreezeDTO {

    private String freezeReasonCode = "KINSHIP_DISPUTE";

    @NotBlank(message = "冻结原因说明不能为空")
    private String freezeReasonText;

    public String getFreezeReasonCode() {
        return freezeReasonCode;
    }

    public void setFreezeReasonCode(String freezeReasonCode) {
        this.freezeReasonCode = freezeReasonCode;
    }

    public String getFreezeReasonText() {
        return freezeReasonText;
    }

    public void setFreezeReasonText(String freezeReasonText) {
        this.freezeReasonText = freezeReasonText;
    }
}
