package cn.communityelderly.modules.emergency.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("work_order")
public class WorkOrder {

    @TableId(value = "work_order_id", type = IdType.AUTO)
    private Long workOrderId;

    private String orderNo;
    private String sourceType; // SOS, RESERVATION, HEALTH_ALERT, MANUAL
    private String sourceReferenceId;
    private Long elderId;
    private Long communityId;
    private String serviceType; // EMERGENCY_RESCUE, DAILY_CARE, etc.
    private String orderTitle;
    private String orderDescription;
    private String urgencyLevel; // NORMAL, URGENT
    private String status; // PENDING_DISPATCH, PENDING_ACCEPT, ACCEPTED, PROCESSING, CLOSED, VOIDED
    private Long currentAssigneeId;
    private LocalDateTime dispatchTime;
    private LocalDateTime acceptTime;
    private LocalDateTime serviceStartTime;
    private LocalDateTime serviceFinishTime;
    private String serviceResultSummary;
    private Boolean resultPhotoRequired;
    private String photoExemptionReason;
    private String photoUrls;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
    private String reviewComment;

    @Version
    private Long rowVersion;

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

    public Long getCurrentAssigneeId() {
        return currentAssigneeId;
    }

    public void setCurrentAssigneeId(Long currentAssigneeId) {
        this.currentAssigneeId = currentAssigneeId;
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

    public String getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(String photoUrls) {
        this.photoUrls = photoUrls;
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

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
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
