package cn.communityelderly.modules.dashboard.dto;

import java.util.List;

public class DashboardChartsVO {
    private List<DiseaseDistributionVO> diseaseDistribution;
    private List<ServiceStructureVO> serviceStructure;
    private WorkOrder7DayTrendVO workOrderTrend;

    public DashboardChartsVO() {
    }

    public DashboardChartsVO(List<DiseaseDistributionVO> diseaseDistribution, List<ServiceStructureVO> serviceStructure, WorkOrder7DayTrendVO workOrderTrend) {
        this.diseaseDistribution = diseaseDistribution;
        this.serviceStructure = serviceStructure;
        this.workOrderTrend = workOrderTrend;
    }

    public List<DiseaseDistributionVO> getDiseaseDistribution() {
        return diseaseDistribution;
    }

    public void setDiseaseDistribution(List<DiseaseDistributionVO> diseaseDistribution) {
        this.diseaseDistribution = diseaseDistribution;
    }

    public List<ServiceStructureVO> getServiceStructure() {
        return serviceStructure;
    }

    public void setServiceStructure(List<ServiceStructureVO> serviceStructure) {
        this.serviceStructure = serviceStructure;
    }

    public WorkOrder7DayTrendVO getWorkOrderTrend() {
        return workOrderTrend;
    }

    public void setWorkOrderTrend(WorkOrder7DayTrendVO workOrderTrend) {
        this.workOrderTrend = workOrderTrend;
    }
}
