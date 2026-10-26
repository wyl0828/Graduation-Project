package cn.communityelderly.modules.dashboard.dto;

public class DiseaseDistributionVO {
    private String name;
    private Long value;
    private Double percentage;

    public DiseaseDistributionVO() {
    }

    public DiseaseDistributionVO(String name, Long value, Double percentage) {
        this.name = name;
        this.value = value;
        this.percentage = percentage;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    public Double getPercentage() {
        return percentage;
    }

    public void setPercentage(Double percentage) {
        this.percentage = percentage;
    }
}
