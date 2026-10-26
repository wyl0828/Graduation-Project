package cn.communityelderly.modules.audit.dto;

public class AuditLogQueryDTO {
    private String targetType;
    private String action;
    private String actorRole;
    private String traceId;
    private Integer page = 1;
    private Integer size = 10;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Integer getPage() {
        return page != null && page > 0 ? page : 1;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size != null && size > 0 ? size : 10;
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
