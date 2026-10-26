package cn.communityelderly.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterChildRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度须为8至128个字符")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String name;

    @NotBlank(message = "手机号验证挑战编号不能为空")
    @Size(max = 36, message = "手机号验证挑战编号无效")
    private String phoneChallengeId;

    @NotBlank(message = "手机号验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "手机号验证码格式无效")
    private String verificationCode;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public String getPhoneChallengeId() {
        return phoneChallengeId;
    }

    public void setPhoneChallengeId(String phoneChallengeId) {
        this.phoneChallengeId = phoneChallengeId;
    }
}
