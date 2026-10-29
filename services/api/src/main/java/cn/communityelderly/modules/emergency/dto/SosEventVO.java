package cn.communityelderly.modules.emergency.dto;

import java.time.LocalDateTime;

public class SosEventVO {

    private Long sosId;
    private Long elderId;
    private String elderName;
    private Integer elderAge;
    private String elderAddress;
    private String status; // ACCEPTED, CANCELLED, VOIDED, CLOSED
    private String statusDesc;
    private Integer pressDurationMs;
    private Long workOrderId;
    private String workOrderNo;
    private String workOrderStatus;
    private Long assigneeId;
    private String assigneeName;
    private String assigneePhone;
    private String contactName;
    private String contactPhone;
    private LocalDateTime occurredAt;
    private String cancelReason;
    private Boolean elderInactiveAtAcceptance;

    public Long getSosId() {
        return sosId;
    }

    public void setSosId(Long sosId) {
        this.sosId = sosId;
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

    public Integer getElderAge() {
        return elderAge;
    }

    public void setElderAge(Integer elderAge) {
        this.elderAge = elderAge;
    }

    public String getElderAddress() {
        return elderAddress;
    }

    public void setElderAddress(String elderAddress) {
        this.elderAddress = elderAddress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusDesc() {
        return statusDesc;
    }

    public void setStatusDesc(String statusDesc) {
        this.statusDesc = statusDesc;
    }

    public Integer getPressDurationMs() {
        return pressDurationMs;
    }

    public void setPressDurationMs(Integer pressDurationMs) {
        this.pressDurationMs = pressDurationMs;
    }

    public Long getWorkOrderId() {
        return workOrderId;
    }

    public void setWorkOrderId(Long workOrderId) {
        this.workOrderId = workOrderId;
    }

    public String getWorkOrderNo() {
        return workOrderNo;
    }

    public void setWorkOrderNo(String workOrderNo) {
        this.workOrderNo = workOrderNo;
    }

    public String getWorkOrderStatus() {
        return workOrderStatus;
    }

    public void setWorkOrderStatus(String workOrderStatus) {
        this.workOrderStatus = workOrderStatus;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
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

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public Boolean getElderInactiveAtAcceptance() {
        return elderInactiveAtAcceptance;
    }

    public void setElderInactiveAtAcceptance(Boolean elderInactiveAtAcceptance) {
        this.elderInactiveAtAcceptance = elderInactiveAtAcceptance;
    }
}
