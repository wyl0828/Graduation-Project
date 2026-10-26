package cn.communityelderly.modules.dashboard.dto;

import java.util.List;

public class WorkOrder7DayTrendVO {
    private List<String> dateList;
    private List<Long> dispatchedCounts;
    private List<Long> closedCounts;
    private List<Double> avgResponseMinutes;

    public WorkOrder7DayTrendVO() {
    }

    public WorkOrder7DayTrendVO(List<String> dateList, List<Long> dispatchedCounts, List<Long> closedCounts, List<Double> avgResponseMinutes) {
        this.dateList = dateList;
        this.dispatchedCounts = dispatchedCounts;
        this.closedCounts = closedCounts;
        this.avgResponseMinutes = avgResponseMinutes;
    }

    public List<String> getDateList() {
        return dateList;
    }

    public void setDateList(List<String> dateList) {
        this.dateList = dateList;
    }

    public List<Long> getDispatchedCounts() {
        return dispatchedCounts;
    }

    public void setDispatchedCounts(List<Long> dispatchedCounts) {
        this.dispatchedCounts = dispatchedCounts;
    }

    public List<Long> getClosedCounts() {
        return closedCounts;
    }

    public void setClosedCounts(List<Long> closedCounts) {
        this.closedCounts = closedCounts;
    }

    public List<Double> getAvgResponseMinutes() {
        return avgResponseMinutes;
    }

    public void setAvgResponseMinutes(List<Double> avgResponseMinutes) {
        this.avgResponseMinutes = avgResponseMinutes;
    }
}
