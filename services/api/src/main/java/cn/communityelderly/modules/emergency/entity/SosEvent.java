package cn.communityelderly.modules.emergency.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("sos_event")
public class SosEvent {

    @TableId(value = "sos_id", type = IdType.AUTO)
    private Long sosId;

    private Long elderId;
    private Long userId;
    private String status; // ACCEPTED, CANCELLED, CLOSED, VOIDED
    private Integer pressDurationMs;
    private String locationAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Long initialWorkOrderId;
    private String idempotencyKey;
    private LocalDateTime occurredAt;

    public Long getSosId() {
        return sosId;
    }

    public void setSosId(Long sosId) {
        this.sosId = sosId;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPressDurationMs() {
        return pressDurationMs;
    }

    public void setPressDurationMs(Integer pressDurationMs) {
        this.pressDurationMs = pressDurationMs;
    }

    public String getLocationAddress() {
        return locationAddress;
    }

    public void setLocationAddress(String locationAddress) {
        this.locationAddress = locationAddress;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public Long getInitialWorkOrderId() {
        return initialWorkOrderId;
    }

    public void setInitialWorkOrderId(Long initialWorkOrderId) {
        this.initialWorkOrderId = initialWorkOrderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
