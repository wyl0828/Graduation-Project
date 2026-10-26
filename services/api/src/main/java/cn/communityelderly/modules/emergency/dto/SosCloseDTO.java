package cn.communityelderly.modules.emergency.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SosCloseDTO {

    @NotBlank(message = "结案处置结果不能为空")
    @Size(min = 5, max = 500, message = "结案处置结果至少输入5个字符，详细记录现场处置或电话回访情况")
    private String resultSummary;

    private Boolean isFalseAlarm = false;

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Boolean getIsFalseAlarm() {
        return isFalseAlarm;
    }

    public void setIsFalseAlarm(Boolean isFalseAlarm) {
        this.isFalseAlarm = isFalseAlarm;
    }
}
