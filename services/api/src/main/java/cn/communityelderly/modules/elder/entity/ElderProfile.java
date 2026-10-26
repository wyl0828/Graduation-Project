package cn.communityelderly.modules.elder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("elder_profile")
public class ElderProfile {

    @TableId(value = "elder_id", type = IdType.AUTO)
    private Long elderId;

    private String archiveNo;
    private Long communityId;
    private String name;
    private String sex;
    private LocalDate birthDate;
    private String birthDateUnknownReason;
    private String address;
    private String contactPhone;
    private String contactPhoneMissingReason;
    private String emergencyContactName;
    private String emergencyContactRelation;
    private String emergencyContactPhone;
    private String emergencyContactNameMissingReason;
    private String emergencyContactRelationMissingReason;
    private String emergencyContactPhoneMissingReason;
    private byte[] idNumberCiphertext;
    private String idNumberLookupHmac;
    private String status; // ACTIVE, INACTIVE
    private String statusReason;

    @Version
    private Long rowVersion;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getArchiveNo() {
        return archiveNo;
    }

    public void setArchiveNo(String archiveNo) {
        this.archiveNo = archiveNo;
    }

    public Long getCommunityId() {
        return communityId;
    }

    public void setCommunityId(Long communityId) {
        this.communityId = communityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getBirthDateUnknownReason() {
        return birthDateUnknownReason;
    }

    public void setBirthDateUnknownReason(String birthDateUnknownReason) {
        this.birthDateUnknownReason = birthDateUnknownReason;
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

    public String getContactPhoneMissingReason() {
        return contactPhoneMissingReason;
    }

    public void setContactPhoneMissingReason(String contactPhoneMissingReason) {
        this.contactPhoneMissingReason = contactPhoneMissingReason;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactRelation() {
        return emergencyContactRelation;
    }

    public void setEmergencyContactRelation(String emergencyContactRelation) {
        this.emergencyContactRelation = emergencyContactRelation;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public String getEmergencyContactNameMissingReason() {
        return emergencyContactNameMissingReason;
    }

    public void setEmergencyContactNameMissingReason(String emergencyContactNameMissingReason) {
        this.emergencyContactNameMissingReason = emergencyContactNameMissingReason;
    }

    public String getEmergencyContactRelationMissingReason() {
        return emergencyContactRelationMissingReason;
    }

    public void setEmergencyContactRelationMissingReason(String emergencyContactRelationMissingReason) {
        this.emergencyContactRelationMissingReason = emergencyContactRelationMissingReason;
    }

    public String getEmergencyContactPhoneMissingReason() {
        return emergencyContactPhoneMissingReason;
    }

    public void setEmergencyContactPhoneMissingReason(String emergencyContactPhoneMissingReason) {
        this.emergencyContactPhoneMissingReason = emergencyContactPhoneMissingReason;
    }

    public byte[] getIdNumberCiphertext() {
        return idNumberCiphertext;
    }

    public void setIdNumberCiphertext(byte[] idNumberCiphertext) {
        this.idNumberCiphertext = idNumberCiphertext;
    }

    public String getIdNumberLookupHmac() {
        return idNumberLookupHmac;
    }

    public void setIdNumberLookupHmac(String idNumberLookupHmac) {
        this.idNumberLookupHmac = idNumberLookupHmac;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
