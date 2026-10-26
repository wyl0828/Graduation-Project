package cn.communityelderly.modules.audit.dto;

import java.util.List;

public class AuditDiffVO {
    private Long auditId;
    private String occurredAt;
    private String actorName;
    private String actorRole;
    private String clientIp;
    private String traceId;
    private String shaSignature;
    private List<AuditDiffItemVO> diffItems;
    private Object beforeJson;
    private Object afterJson;

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getShaSignature() {
        return shaSignature;
    }

    public void setShaSignature(String shaSignature) {
        this.shaSignature = shaSignature;
    }

    public List<AuditDiffItemVO> getDiffItems() {
        return diffItems;
    }

    public void setDiffItems(List<AuditDiffItemVO> diffItems) {
        this.diffItems = diffItems;
    }

    public Object getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(Object beforeJson) {
        this.beforeJson = beforeJson;
    }

    public Object getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(Object afterJson) {
        this.afterJson = afterJson;
    }
}
