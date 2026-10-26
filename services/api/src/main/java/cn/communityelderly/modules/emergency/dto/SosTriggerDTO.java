package cn.communityelderly.modules.emergency.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class SosTriggerDTO {

    private Long elderId;

    @NotNull(message = "触控时长不能为空")
    @Min(value = 2000, message = "长按触控时长不足2000毫秒（2秒），防误触保护已拦截")
    private Integer pressDurationMs;

    private String locationAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String idempotencyKey;

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
