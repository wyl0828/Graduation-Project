package cn.communityelderly.modules.elder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("elder_correction_item")
public class ElderCorrectionItem {

    @TableId(value = "item_id", type = IdType.AUTO)
    private Long itemId;

    private Long requestId;
    private String fieldCode;
    private String baselineValueProtected;
    private String proposedValueProtected;
    private String beforeValueProtected;
    private String afterValueProtected;
    private LocalDateTime appliedAt;

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getFieldCode() {
        return fieldCode;
    }

    public void setFieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
    }

    public String getBaselineValueProtected() {
        return baselineValueProtected;
    }

    public void setBaselineValueProtected(String baselineValueProtected) {
        this.baselineValueProtected = baselineValueProtected;
    }

    public String getProposedValueProtected() {
        return proposedValueProtected;
    }

    public void setProposedValueProtected(String proposedValueProtected) {
        this.proposedValueProtected = proposedValueProtected;
    }

    public String getBeforeValueProtected() {
        return beforeValueProtected;
    }

    public void setBeforeValueProtected(String beforeValueProtected) {
        this.beforeValueProtected = beforeValueProtected;
    }

    public String getAfterValueProtected() {
        return afterValueProtected;
    }

    public void setAfterValueProtected(String afterValueProtected) {
        this.afterValueProtected = afterValueProtected;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}
