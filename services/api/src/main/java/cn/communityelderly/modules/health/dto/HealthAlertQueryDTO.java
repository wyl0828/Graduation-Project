package cn.communityelderly.modules.health.dto;

public class HealthAlertQueryDTO {

    private Integer page = 1;
    private Integer size = 10;
    private Long elderId;
    private String alertLevel; // LOW, MEDIUM, HIGH
    private String status;     // PENDING, PROCESSING, CLOSED

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

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
