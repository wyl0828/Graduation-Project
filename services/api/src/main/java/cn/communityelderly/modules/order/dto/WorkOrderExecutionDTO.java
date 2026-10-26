package cn.communityelderly.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class WorkOrderExecutionDTO {

    @NotBlank(message = "服务完成总结说明不能为空")
    private String resultSummary;

    private List<String> photoUrls;

    private Boolean isExemptPhoto;

    private String photoExemptionReason;

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public List<String> getPhotoUrls() {
        return photoUrls;
    }

    public void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls;
    }

    public Boolean getIsExemptPhoto() {
        return isExemptPhoto;
    }

    public void setIsExemptPhoto(Boolean isExemptPhoto) {
        this.isExemptPhoto = isExemptPhoto;
    }

    public String getPhotoExemptionReason() {
        return photoExemptionReason;
    }

    public void setPhotoExemptionReason(String photoExemptionReason) {
        this.photoExemptionReason = photoExemptionReason;
    }
}
