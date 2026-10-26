package cn.communityelderly.modules.dashboard.dto;

public class DashboardSummaryVO {
    private Long totalElders;
    private Long highAgeAloneCount;
    private Long pendingAlertsCount;
    private Double workOrderFulfillmentRate;
    private Double medicationComplianceRate;
    private Long monthlyClosedOrders;
    private Long todayConfirmedCheckins;

    public Long getTotalElders() {
        return totalElders;
    }

    public void setTotalElders(Long totalElders) {
        this.totalElders = totalElders;
    }

    public Long getHighAgeAloneCount() {
        return highAgeAloneCount;
    }

    public void setHighAgeAloneCount(Long highAgeAloneCount) {
        this.highAgeAloneCount = highAgeAloneCount;
    }

    public Long getPendingAlertsCount() {
        return pendingAlertsCount;
    }

    public void setPendingAlertsCount(Long pendingAlertsCount) {
        this.pendingAlertsCount = pendingAlertsCount;
    }

    public Double getWorkOrderFulfillmentRate() {
        return workOrderFulfillmentRate;
    }

    public void setWorkOrderFulfillmentRate(Double workOrderFulfillmentRate) {
        this.workOrderFulfillmentRate = workOrderFulfillmentRate;
    }

    public Double getMedicationComplianceRate() {
        return medicationComplianceRate;
    }

    public void setMedicationComplianceRate(Double medicationComplianceRate) {
        this.medicationComplianceRate = medicationComplianceRate;
    }

    public Long getMonthlyClosedOrders() {
        return monthlyClosedOrders;
    }

    public void setMonthlyClosedOrders(Long monthlyClosedOrders) {
        this.monthlyClosedOrders = monthlyClosedOrders;
    }

    public Long getTodayConfirmedCheckins() {
        return todayConfirmedCheckins;
    }

    public void setTodayConfirmedCheckins(Long todayConfirmedCheckins) {
        this.todayConfirmedCheckins = todayConfirmedCheckins;
    }
}
