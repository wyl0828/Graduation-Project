package cn.communityelderly.modules.elder.dto;

public class ActivationCodeIssueDTO {

    private String issueMode = "IN_PERSON"; // IN_PERSON, SPECIAL_PROXY
    private String specialReason;
    private String proxyBasisType;

    public String getIssueMode() {
        return issueMode;
    }

    public void setIssueMode(String issueMode) {
        this.issueMode = issueMode;
    }

    public String getSpecialReason() {
        return specialReason;
    }

    public void setSpecialReason(String specialReason) {
        this.specialReason = specialReason;
    }

    public String getProxyBasisType() {
        return proxyBasisType;
    }

    public void setProxyBasisType(String proxyBasisType) {
        this.proxyBasisType = proxyBasisType;
    }
}
