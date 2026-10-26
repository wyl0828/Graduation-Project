package cn.communityelderly.modules.elder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class KinshipApplyDTO {

    @NotNull(message = "老人档案ID不能为空")
    private Long elderId;

    @NotBlank(message = "亲情关系称谓不能为空")
    private String relationship;

    @NotBlank(message = "申请事由不能为空")
    private String applyReason;

    private String evidenceMetadata;

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getApplyReason() {
        return applyReason;
    }

    public void setApplyReason(String applyReason) {
        this.applyReason = applyReason;
    }

    public String getEvidenceMetadata() {
        return evidenceMetadata;
    }

    public void setEvidenceMetadata(String evidenceMetadata) {
        this.evidenceMetadata = evidenceMetadata;
    }
}
