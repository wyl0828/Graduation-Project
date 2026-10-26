package cn.communityelderly.modules.dashboard.dto;

public class ServiceStructureVO {
    private String serviceType;
    private String serviceName;
    private Long orderCount;
    private Double percentage;

    public ServiceStructureVO() {
    }

    public ServiceStructureVO(String serviceType, String serviceName, Long orderCount, Double percentage) {
        this.serviceType = serviceType;
        this.serviceName = serviceName;
        this.orderCount = orderCount;
        this.percentage = percentage;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }
}
