package cn.communityelderly.modules.elder.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CorrectionDetailVO {

    private Long requestId;
    private Long elderId;
    private String elderName;
    private String archiveNo;
    private Long applicantUserId;
    private String applicantPhone;
    private String status; // PENDING_REVIEW, APPROVED_APPLIED, REJECTED
    private String reason;
    private String blockCode;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private String decisionReason;
    private List<ItemVO> items;

    public static class ItemVO {
        private String fieldCode;
        private String fieldName;
        private String baselineValue;
        private String currentDbValue;
        private String proposedValue;
        private Boolean hasConflict;

        public String getFieldCode() {
            return fieldCode;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getBaselineValue() {
            return baselineValue;
        }

        public void setBaselineValue(String baselineValue) {
            this.baselineValue = baselineValue;
        }

        public String getCurrentDbValue() {
            return currentDbValue;
        }

        public void setCurrentDbValue(String currentDbValue) {
            this.currentDbValue = currentDbValue;
        }

        public String getProposedValue() {
            return proposedValue;
        }

        public void setProposedValue(String proposedValue) {
            this.proposedValue = proposedValue;
        }

        public Boolean getHasConflict() {
            return hasConflict;
        }

        public void setHasConflict(Boolean hasConflict) {
            this.hasConflict = hasConflict;
        }
    }

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

    public String getElderName() {
        return elderName;
    }

    public void setElderName(String elderName) {
        this.elderName = elderName;
    }

    public String getArchiveNo() {
        return archiveNo;
    }

    public void setArchiveNo(String archiveNo) {
        this.archiveNo = archiveNo;
    }

    public Long getApplicantUserId() {
        return applicantUserId;
    }

    public void setApplicantUserId(Long applicantUserId) {
        this.applicantUserId = applicantUserId;
    }

    public String getApplicantPhone() {
        return applicantPhone;
    }

    public void setApplicantPhone(String applicantPhone) {
        this.applicantPhone = applicantPhone;
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

    public String getBlockCode() {
        return blockCode;
    }

    public void setBlockCode(String blockCode) {
        this.blockCode = blockCode;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
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

    public List<ItemVO> getItems() {
        return items;
    }

    public void setItems(List<ItemVO> items) {
        this.items = items;
    }
}
