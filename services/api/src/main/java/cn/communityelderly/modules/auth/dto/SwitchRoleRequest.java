package cn.communityelderly.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class SwitchRoleRequest {

    @NotBlank(message = "目标角色代码不能为空")
    private String targetRoleCode;

    public String getTargetRoleCode() {
        return targetRoleCode;
    }

    public void setTargetRoleCode(String targetRoleCode) {
        this.targetRoleCode = targetRoleCode;
    }
}
