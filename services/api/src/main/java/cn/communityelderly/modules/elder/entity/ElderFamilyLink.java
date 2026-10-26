package cn.communityelderly.modules.elder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

@TableName("elder_family_link")
public class ElderFamilyLink {

    @TableId(value = "link_id", type = IdType.AUTO)
    private Long linkId;

    private Long elderId;
    private Long childUserId;
    private String status; // PENDING, ACTIVE, FROZEN, REJECTED, UNLINKED, REVOKED
    private Long currentApplicationId;
    private Long linkGeneration;
    private String freezeReasonCode;
    private String freezeReasonText;
    private LocalDateTime frozenAt;
    private LocalDateTime reviewDeadline;

    @Version
    private Long rowVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public Long getChildUserId() {
        return childUserId;
    }

    public void setChildUserId(Long childUserId) {
        this.childUserId = childUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentApplicationId() {
        return currentApplicationId;
    }

    public void setCurrentApplicationId(Long currentApplicationId) {
        this.currentApplicationId = currentApplicationId;
    }

    public Long getLinkGeneration() {
        return linkGeneration;
    }

    public void setLinkGeneration(Long linkGeneration) {
        this.linkGeneration = linkGeneration;
    }

    public String getFreezeReasonCode() {
        return freezeReasonCode;
    }

    public void setFreezeReasonCode(String freezeReasonCode) {
        this.freezeReasonCode = freezeReasonCode;
    }

    public String getFreezeReasonText() {
        return freezeReasonText;
    }

    public void setFreezeReasonText(String freezeReasonText) {
        this.freezeReasonText = freezeReasonText;
    }

    public LocalDateTime getFrozenAt() {
        return frozenAt;
    }

    public void setFrozenAt(LocalDateTime frozenAt) {
        this.frozenAt = frozenAt;
    }

    public LocalDateTime getReviewDeadline() {
        return reviewDeadline;
    }

    public void setReviewDeadline(LocalDateTime reviewDeadline) {
        this.reviewDeadline = reviewDeadline;
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
