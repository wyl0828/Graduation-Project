package cn.communityelderly.modules.simulation.dto;

import java.time.LocalDateTime;

public class SimulationStatusVO {

    private String status; // RUNNING, STOPPED
    private Long elderId;
    private String vitalType;
    private Integer sampleIntervalSec;
    private Long generatedCount;
    private LocalDateTime startedAt;
    private LocalDateTime lastGeneratedAt;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getVitalType() {
        return vitalType;
    }

    public void setVitalType(String vitalType) {
        this.vitalType = vitalType;
    }

    public Integer getSampleIntervalSec() {
        return sampleIntervalSec;
    }

    public void setSampleIntervalSec(Integer sampleIntervalSec) {
        this.sampleIntervalSec = sampleIntervalSec;
    }

    public Long getGeneratedCount() {
        return generatedCount;
    }

    public void setGeneratedCount(Long generatedCount) {
        this.generatedCount = generatedCount;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getLastGeneratedAt() {
        return lastGeneratedAt;
    }

    public void setLastGeneratedAt(LocalDateTime lastGeneratedAt) {
        this.lastGeneratedAt = lastGeneratedAt;
    }
}
