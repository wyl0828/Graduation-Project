package cn.communityelderly.modules.audit.dto;

public class AuditDiffItemVO {
    private String fieldName;
    private String baselineValue;
    private String proposedValue;

    public AuditDiffItemVO() {
    }

    public AuditDiffItemVO(String fieldName, String baselineValue, String proposedValue) {
        this.fieldName = fieldName;
        this.baselineValue = baselineValue;
        this.proposedValue = proposedValue;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getBaselineValue() {
        return baselineValue;
    }

    public void setBaselineValue(String baselineValue) {
        this.baselineValue = baselineValue;
    }

    public String getProposedValue() {
        return proposedValue;
    }

    public void setProposedValue(String proposedValue) {
        this.proposedValue = proposedValue;
    }
}
