package cn.communityelderly.modules.elder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("elder_correction_request")
public class ElderCorrectionRequest {

    @TableId(value = "request_id", type = IdType.AUTO)
    private Long requestId;

    private Long elderId;
    private Long applicantUserId;
    private Long applicantLinkId;
    private Long applicantLinkGeneration;
    private String status; // PENDING_REVIEW, APPROVED_APPLIED, REJECTED
    private String reason;
    private Long submittedArchiveVersion;
    private LocalDateTime submittedAt;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
    private String decisionReason;
    private String blockCode; // FIELD_VALUE_CONFLICT
    private LocalDateTime blockAt;

    @Version
    private Long rowVersion;

    private String idempotencyKey;
    private String requestHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public void setApplicantUserId(Long applicantUserId) {
        this.applicantUserId = applicantUserId;
    }

    public Long getApplicantLinkId() {
        return applicantLinkId;
    }

    public void setApplicantLinkId(Long applicantLinkId) {
        this.applicantLinkId = applicantLinkId;
    }

    public Long getApplicantLinkGeneration() {
        return applicantLinkGeneration;
    }

    public void setApplicantLinkGeneration(Long applicantLinkGeneration) {
        this.applicantLinkGeneration = applicantLinkGeneration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getSubmittedArchiveVersion() {
        return submittedArchiveVersion;
    }

    public void setSubmittedArchiveVersion(Long submittedArchiveVersion) {
        this.submittedArchiveVersion = submittedArchiveVersion;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Long getReviewerUserId() {
        return reviewerUserId;
    }

    public void setReviewerUserId(Long reviewerUserId) {
        this.reviewerUserId = reviewerUserId;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public String getBlockCode() {
        return blockCode;
    }

    public void setBlockCode(String blockCode) {
        this.blockCode = blockCode;
    }

    public LocalDateTime getBlockAt() {
        return blockAt;
    }

    public void setBlockAt(LocalDateTime blockAt) {
        this.blockAt = blockAt;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public void setRequestHash(String requestHash) {
        this.requestHash = requestHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
