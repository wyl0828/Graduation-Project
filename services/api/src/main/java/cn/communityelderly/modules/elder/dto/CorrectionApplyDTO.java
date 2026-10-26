package cn.communityelderly.modules.elder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class CorrectionApplyDTO {

    @NotNull(message = "老人档案ID不能为空")
    private Long elderId;

    @NotBlank(message = "更正申请理由不能为空")
    private String reason;

    @NotEmpty(message = "更正项明细不能为空")
    private List<Item> items;

    public static class Item {
        @NotBlank(message = "字段标识不能为空")
        private String fieldCode;

        private String proposedValue;

        public String getFieldCode() {
            return fieldCode;
        }

        public void setFieldCode(String fieldCode) {
            this.fieldCode = fieldCode;
        }

        public String getProposedValue() {
            return proposedValue;
        }

        public void setProposedValue(String proposedValue) {
            this.proposedValue = proposedValue;
        }
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }
}
