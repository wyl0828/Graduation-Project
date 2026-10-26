package cn.communityelderly.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class PasswordRecoveryTicketDTO {

    @NotBlank(message = "手机号不能为空")
    private String phone;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    @NotBlank(message = "申诉原因不能为空")
    private String appealReason;

    private String contactAlternative;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getAppealReason() {
        return appealReason;
    }

    public void setAppealReason(String appealReason) {
        this.appealReason = appealReason;
    }

    public String getContactAlternative() {
        return contactAlternative;
    }

    public void setContactAlternative(String contactAlternative) {
        this.contactAlternative = contactAlternative;
    }
}
