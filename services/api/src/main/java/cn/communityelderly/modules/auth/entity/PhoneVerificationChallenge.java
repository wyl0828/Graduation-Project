package cn.communityelderly.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("phone_verification_challenge")
public class PhoneVerificationChallenge {

    @TableId(value = "challenge_id", type = IdType.INPUT)
    private String challengeId;
    private Long userId;
    private String phoneNormalized;
    private String purpose;
    private String relatedRequestId;
    private byte[] codeMac;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Integer attemptCount;
    private LocalDateTime consumedAt;
    private String status;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPhoneNormalized() {
        return phoneNormalized;
    }

    public void setPhoneNormalized(String phoneNormalized) {
        this.phoneNormalized = phoneNormalized;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getRelatedRequestId() {
        return relatedRequestId;
    }

    public void setRelatedRequestId(String relatedRequestId) {
        this.relatedRequestId = relatedRequestId;
    }

    public byte[] getCodeMac() {
        return codeMac;
    }

    public void setCodeMac(byte[] codeMac) {
        this.codeMac = codeMac;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
