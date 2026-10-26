package cn.communityelderly.modules.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public class AuthTokenResponse {

    private String token;
    private Long userId;
    private String phone;
    private String accountKind;
    private List<String> roles;
    private String activeRole;
    private String communityId;
    private LocalDateTime expiresAt;
    private String clientType;
    private String csrfToken;

    public AuthTokenResponse() {
    }

    public AuthTokenResponse(String token, Long userId, String phone, String accountKind, List<String> roles, String activeRole, String communityId, LocalDateTime expiresAt) {
        this.token = token;
        this.userId = userId;
        this.phone = phone;
        this.accountKind = accountKind;
        this.roles = roles;
        this.activeRole = activeRole;
        this.communityId = communityId;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAccountKind() {
        return accountKind;
    }

    public void setAccountKind(String accountKind) {
        this.accountKind = accountKind;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public String getActiveRole() {
        return activeRole;
    }

    public void setActiveRole(String activeRole) {
        this.activeRole = activeRole;
    }

    public String getCommunityId() {
        return communityId;
    }

    public void setCommunityId(String communityId) {
        this.communityId = communityId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public void setCsrfToken(String csrfToken) {
        this.csrfToken = csrfToken;
    }
}
