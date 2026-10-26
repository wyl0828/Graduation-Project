package cn.communityelderly.modules.elder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ActivationConsumeDTO {

    @NotBlank(message = "激活票据编号不能为空")
    @Size(max = 36, message = "激活票据编号无效")
    private String ticketId;

    @NotBlank(message = "6位激活码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "激活码必须为6位纯数字")
    private String activationCode;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "已验证手机挑战编号不能为空")
    @Size(max = 36, message = "手机挑战编号无效")
    private String phoneChallengeId;

    @NotBlank(message = "手机验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "手机验证码必须为6位数字")
    private String verificationCode;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 128, message = "初始密码长度须为8至128个字符")
    private String password;

    private String name;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getActivationCode() {
        return activationCode;
    }

    public void setActivationCode(String activationCode) {
        this.activationCode = activationCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhoneChallengeId() {
        return phoneChallengeId;
    }

    public void setPhoneChallengeId(String phoneChallengeId) {
        this.phoneChallengeId = phoneChallengeId;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
