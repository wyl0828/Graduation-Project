package cn.communityelderly.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PhoneChallengeRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码用途不能为空")
    @Pattern(regexp = "^(CHILD_REGISTRATION|ACTIVATION|PASSWORD_RESET|PHONE_CHANGE_OLD|PHONE_CHANGE_NEW)$",
            message = "验证码用途无效")
    private String purpose;

    @NotBlank(message = "关联业务编号不能为空")
    @Size(max = 36, message = "关联业务编号无效")
    private String relatedRequestId;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getRelatedRequestId() {
        return relatedRequestId;
    }

    public void setRelatedRequestId(String relatedRequestId) {
        this.relatedRequestId = relatedRequestId;
    }
}
