package cn.communityelderly.modules.elder.dto;

import java.time.LocalDateTime;

public class ActivationTicketVO {

    private String ticketId;
    private Long elderId;
    private String elderName;
    private String activationCodePlain; // 仅签发/重签时单次明文返回 (P-04)
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private Integer remainingHours;
    private Integer remainingTries;

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
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

    public String getActivationCodePlain() {
        return activationCodePlain;
    }

    public void setActivationCodePlain(String activationCodePlain) {
        this.activationCodePlain = activationCodePlain;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Integer getRemainingHours() {
        return remainingHours;
    }

    public void setRemainingHours(Integer remainingHours) {
        this.remainingHours = remainingHours;
    }

    public Integer getRemainingTries() {
        return remainingTries;
    }

    public void setRemainingTries(Integer remainingTries) {
        this.remainingTries = remainingTries;
    }
}
