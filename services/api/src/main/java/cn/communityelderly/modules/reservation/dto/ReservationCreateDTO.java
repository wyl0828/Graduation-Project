package cn.communityelderly.modules.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class ReservationCreateDTO {

    @NotNull(message = "服务老人档案ID不能为空")
    private Long elderId;

    @NotBlank(message = "服务类型不能为空")
    private String serviceType;

    @NotNull(message = "服务预约日期不能为空")
    private LocalDate serviceDate;

    @NotBlank(message = "服务时段代码不能为空 (MORNING/AFTERNOON)")
    private String slotCode;

    @NotBlank(message = "上门服务详细地址不能为空")
    private String serviceAddress;

    private String specialNotes;

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(LocalDate serviceDate) {
        this.serviceDate = serviceDate;
    }

    public String getSlotCode() {
        return slotCode;
    }

    public void setSlotCode(String slotCode) {
        this.slotCode = slotCode;
    }

    public String getServiceAddress() {
        return serviceAddress;
    }

    public void setServiceAddress(String serviceAddress) {
        this.serviceAddress = serviceAddress;
    }

    public String getSpecialNotes() {
        return specialNotes;
    }

    public void setSpecialNotes(String specialNotes) {
        this.specialNotes = specialNotes;
    }
}
