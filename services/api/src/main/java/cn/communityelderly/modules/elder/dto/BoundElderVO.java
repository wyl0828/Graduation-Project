package cn.communityelderly.modules.elder.dto;

import java.time.LocalDateTime;

public class BoundElderVO {

    private Long linkId;
    private Long elderId;
    private String name;
    private String archiveNo;
    private Integer age;
    private String sex;
    private String relationship;
    private String status; // ACTIVE, PENDING, FROZEN, REJECTED
    private String freezeReason;
    private LocalDateTime reviewDeadline;
    private String address;
    private String contactPhone;

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArchiveNo() {
        return archiveNo;
    }

    public void setArchiveNo(String archiveNo) {
        this.archiveNo = archiveNo;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFreezeReason() {
        return freezeReason;
    }

    public void setFreezeReason(String freezeReason) {
        this.freezeReason = freezeReason;
    }

    public LocalDateTime getReviewDeadline() {
        return reviewDeadline;
    }

    public void setReviewDeadline(LocalDateTime reviewDeadline) {
        this.reviewDeadline = reviewDeadline;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }
}
