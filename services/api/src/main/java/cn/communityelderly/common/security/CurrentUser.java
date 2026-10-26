package cn.communityelderly.common.security;

import java.util.Collections;
import java.util.List;

public class CurrentUser {
    private final String userId;
    private final String phone;
    private final String accountKind; // FAMILY | STAFF
    private final List<String> roles;
    private final String currentRole; // ELDER | CHILD | COMMUNITY_ADMIN | GRID_WORKER
    private final String communityId; // 所属辖区社区ID
    private final Long activeAssignmentId;
    private final String sessionId;
    private final String clientType;

    public CurrentUser(String userId, String phone, String accountKind, List<String> roles, String currentRole, String communityId) {
        this(userId, phone, accountKind, roles, currentRole, communityId, null, null, null);
    }

    public CurrentUser(String userId, String phone, String accountKind, List<String> roles,
                       String currentRole, String communityId, Long activeAssignmentId, String sessionId) {
        this(userId, phone, accountKind, roles, currentRole, communityId, activeAssignmentId, sessionId, null);
    }

    public CurrentUser(String userId, String phone, String accountKind, List<String> roles,
                       String currentRole, String communityId, Long activeAssignmentId, String sessionId,
                       String clientType) {
        this.userId = userId;
        this.phone = phone;
        this.accountKind = accountKind;
        this.roles = roles != null ? List.copyOf(roles) : Collections.emptyList();
        this.currentRole = currentRole;
        this.communityId = communityId;
        this.activeAssignmentId = activeAssignmentId;
        this.sessionId = sessionId;
        this.clientType = clientType;
    }

    public String getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public String getAccountKind() {
        return accountKind;
    }

    public List<String> getRoles() {
        return roles;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    public String getCommunityId() {
        return communityId;
    }

    public Long getActiveAssignmentId() {
        return activeAssignmentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getClientType() {
        return clientType;
    }

    public boolean hasRole(String role) {
        return currentRole != null && currentRole.equals(role);
    }
}
