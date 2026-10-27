package cn.communityelderly.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class StaffCreateRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号须为规范化的11位手机号")
    private String phone;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 128, message = "初始密码至少为8个字符")
    private String initialPassword;

    @NotEmpty(message = "至少选择一个工作人员角色")
    private List<@NotBlank(message = "角色代码不能为空") String> roles;

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getInitialPassword() {
        return initialPassword;
    }

    public void setInitialPassword(String initialPassword) {
        this.initialPassword = initialPassword;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
