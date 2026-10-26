package cn.communityelderly.modules.order.dto;

import java.time.LocalDateTime;
import java.util.List;

public class WorkOrderVO {
    private Long workOrderId;
    private String orderNo;
    private String sourceType;
    private String sourceReferenceId;
    private Long elderId;
    private String elderName;
    private String elderPhone;
    private String elderAddress;
    private Long communityId;
    private String serviceType;
    private String serviceTypeName;
    private String orderTitle;
    private String orderDescription;
    private String urgencyLevel;
    private String status;
    private String statusName;
    private Long currentAssigneeId;
    private String assigneeName;
    private String assigneePhone;
    private LocalDateTime dispatchTime;
    private LocalDateTime acceptTime;
    private LocalDateTime serviceStartTime;
    private LocalDateTime serviceFinishTime;
    private String serviceResultSummary;
    private Boolean resultPhotoRequired;
    private String photoExemptionReason;
    private List<String> photoUrls;
    private Long reviewerUserId;
    private String reviewerName;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceReferenceId() {
        return sourceReferenceId;
    }

    public void setSourceReferenceId(String sourceReferenceId) {
        this.sourceReferenceId = sourceReferenceId;
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

    public String getElderPhone() {
        return elderPhone;
    }

    public void setElderPhone(String elderPhone) {
        this.elderPhone = elderPhone;
    }

    public String getElderAddress() {
        return elderAddress;
    }

    public void setElderAddress(String elderAddress) {
        this.elderAddress = elderAddress;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public void setCommunityId(Long communityId) {
        this.communityId = communityId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceTypeName() {
        return serviceTypeName;
    }

    public void setServiceTypeName(String serviceTypeName) {
        this.serviceTypeName = serviceTypeName;
    }

    public String getOrderTitle() {
        return orderTitle;
    }

    public void setOrderTitle(String orderTitle) {
        this.orderTitle = orderTitle;
    }

    public String getOrderDescription() {
        return orderDescription;
    }

    public void setOrderDescription(String orderDescription) {
        this.orderDescription = orderDescription;
    }

    public String getUrgencyLevel() {
        return urgencyLevel;
    }

    public void setUrgencyLevel(String urgencyLevel) {
        this.urgencyLevel = urgencyLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusName() {
        return statusName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public Long getCurrentAssigneeId() {
        return currentAssigneeId;
    }

    public void setCurrentAssigneeId(Long currentAssigneeId) {
        this.currentAssigneeId = currentAssigneeId;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public String getAssigneePhone() {
        return assigneePhone;
    }

    public void setAssigneePhone(String assigneePhone) {
        this.assigneePhone = assigneePhone;
    }

    public LocalDateTime getDispatchTime() {
        return dispatchTime;
    }

    public void setDispatchTime(LocalDateTime dispatchTime) {
        this.dispatchTime = dispatchTime;
    }

    public LocalDateTime getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(LocalDateTime acceptTime) {
        this.acceptTime = acceptTime;
    }

    public LocalDateTime getServiceStartTime() {
        return serviceStartTime;
    }

    public void setServiceStartTime(LocalDateTime serviceStartTime) {
        this.serviceStartTime = serviceStartTime;
    }

    public LocalDateTime getServiceFinishTime() {
        return serviceFinishTime;
    }

    public void setServiceFinishTime(LocalDateTime serviceFinishTime) {
        this.serviceFinishTime = serviceFinishTime;
    }

    public String getServiceResultSummary() {
        return serviceResultSummary;
    }

    public void setServiceResultSummary(String serviceResultSummary) {
        this.serviceResultSummary = serviceResultSummary;
    }

    public Boolean getResultPhotoRequired() {
        return resultPhotoRequired;
    }

    public void setResultPhotoRequired(Boolean resultPhotoRequired) {
        this.resultPhotoRequired = resultPhotoRequired;
    }

    public String getPhotoExemptionReason() {
        return photoExemptionReason;
    }

    public void setPhotoExemptionReason(String photoExemptionReason) {
        this.photoExemptionReason = photoExemptionReason;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls;
    }

    public Long getReviewerUserId() {
        return reviewerUserId;
    }

    public void setReviewerUserId(Long reviewerUserId) {
        this.reviewerUserId = reviewerUserId;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
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
