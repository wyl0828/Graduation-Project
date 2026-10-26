package cn.communityelderly.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("auth_session")
public class AuthSession {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    private Long userId;
    private byte[] tokenHash;
    private String clientType;
    private Long activeAssignmentId;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime absoluteExpiresAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private byte[] csrfTokenHash;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public byte[] getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(byte[] tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public Long getActiveAssignmentId() {
        return activeAssignmentId;
    }

    public void setActiveAssignmentId(Long activeAssignmentId) {
        this.activeAssignmentId = activeAssignmentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public LocalDateTime getAbsoluteExpiresAt() {
        return absoluteExpiresAt;
    }

    public void setAbsoluteExpiresAt(LocalDateTime absoluteExpiresAt) {
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public void setRevokeReason(String revokeReason) {
        this.revokeReason = revokeReason;
    }

    public byte[] getCsrfTokenHash() {
        return csrfTokenHash;
    }

    public void setCsrfTokenHash(byte[] csrfTokenHash) {
        this.csrfTokenHash = csrfTokenHash;
    }
}
