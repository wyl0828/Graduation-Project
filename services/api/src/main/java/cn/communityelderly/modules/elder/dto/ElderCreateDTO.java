package cn.communityelderly.modules.elder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class ElderCreateDTO {

    @NotBlank(message = "老人姓名不能为空")
    private String name;

    private String sex = "UNKNOWN";

    private LocalDate birthDate;
    private String birthDateUnknownReason;

    @NotBlank(message = "现居地址不能为空")
    private String address;

    private String contactPhone;
    private String contactPhoneMissingReason;

    private String emergencyContactName;
    private String emergencyContactRelation;
    private String emergencyContactPhone;
    private String emergencyContactNameMissingReason;
    private String emergencyContactRelationMissingReason;
    private String emergencyContactPhoneMissingReason;

    private String idCard;

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

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }
}
