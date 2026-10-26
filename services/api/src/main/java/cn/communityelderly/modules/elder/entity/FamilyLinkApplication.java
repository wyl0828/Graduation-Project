package cn.communityelderly.modules.elder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("family_link_application")
public class FamilyLinkApplication {

    @TableId(value = "application_id", type = IdType.AUTO)
    private Long applicationId;

    private Long elderId;
    private Long childUserId;
    private Long submittedByUserId;
    private String submissionMode; // SELF, STAFF_ASSISTED
    private String relationship;
    private String applyReason;
    private String status; // PENDING, APPROVED, REJECTED
    private String evidenceMetadata;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
    private String decisionReason;
    private Long linkGeneration;
    private String idempotencyKey;
    private String requestHash;
    private LocalDateTime createdAt;

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public Long getChildUserId() {
        return childUserId;
    }

    public void setChildUserId(Long childUserId) {
        this.childUserId = childUserId;
    }

    public Long getSubmittedByUserId() {
        return submittedByUserId;
    }

    public void setSubmittedByUserId(Long submittedByUserId) {
        this.submittedByUserId = submittedByUserId;
    }

    public String getSubmissionMode() {
        return submissionMode;
    }

    public void setSubmissionMode(String submissionMode) {
        this.submissionMode = submissionMode;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getApplyReason() {
        return applyReason;
    }

    public void setApplyReason(String applyReason) {
        this.applyReason = applyReason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEvidenceMetadata() {
        return evidenceMetadata;
    }

    public void setEvidenceMetadata(String evidenceMetadata) {
        this.evidenceMetadata = evidenceMetadata;
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

    public Long getLinkGeneration() {
        return linkGeneration;
    }

    public void setLinkGeneration(Long linkGeneration) {
        this.linkGeneration = linkGeneration;
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
}
