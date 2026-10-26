package cn.communityelderly.modules.elder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("elder_activation_ticket")
public class ElderActivationTicket {

    @TableId(value = "ticket_id", type = IdType.INPUT)
    private String ticketId;

    private Long elderId;
    private byte[] codeMac;
    private String status; // ISSUED, USED, LOCKED, EXPIRED, SUPERSEDED
    private Long issuedBy;
    private String issueMode; // IN_PERSON, SPECIAL_PROXY
    private String identityCheckResult;
    private String specialReason;
    private String proxyBasisType;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private Integer failedAttempts;
    private LocalDateTime usedAt;
    private Long usedByUserId;
    private String usedIdempotencyKey;
    private LocalDateTime invalidatedAt;
    private String invalidationReason;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public byte[] getCodeMac() {
        return codeMac;
    }

    public void setCodeMac(byte[] codeMac) {
        this.codeMac = codeMac;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(Long issuedBy) {
        this.issuedBy = issuedBy;
    }

    public String getIssueMode() {
        return issueMode;
    }

    public void setIssueMode(String issueMode) {
        this.issueMode = issueMode;
    }

    public String getIdentityCheckResult() {
        return identityCheckResult;
    }

    public void setIdentityCheckResult(String identityCheckResult) {
        this.identityCheckResult = identityCheckResult;
    }

    public String getSpecialReason() {
        return specialReason;
    }

    public void setSpecialReason(String specialReason) {
        this.specialReason = specialReason;
    }

    public String getProxyBasisType() {
        return proxyBasisType;
    }

    public void setProxyBasisType(String proxyBasisType) {
        this.proxyBasisType = proxyBasisType;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public Long getUsedByUserId() {
        return usedByUserId;
    }

    public void setUsedByUserId(Long usedByUserId) {
        this.usedByUserId = usedByUserId;
    }

    public String getUsedIdempotencyKey() {
        return usedIdempotencyKey;
    }

    public void setUsedIdempotencyKey(String usedIdempotencyKey) {
        this.usedIdempotencyKey = usedIdempotencyKey;
    }

    public LocalDateTime getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(LocalDateTime invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public String getInvalidationReason() {
        return invalidationReason;
    }

    public void setInvalidationReason(String invalidationReason) {
        this.invalidationReason = invalidationReason;
    }
}
