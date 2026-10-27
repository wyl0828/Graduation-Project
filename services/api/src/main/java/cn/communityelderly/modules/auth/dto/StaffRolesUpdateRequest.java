package cn.communityelderly.modules.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class StaffRolesUpdateRequest {

    @NotNull(message = "角色集合不能为空；空集合表示撤销本社区角色")
    private List<@NotBlank(message = "角色代码不能为空") String> roles;

    @Size(max = 255, message = "调整原因不能超过255个字符")
    private String reason;

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
