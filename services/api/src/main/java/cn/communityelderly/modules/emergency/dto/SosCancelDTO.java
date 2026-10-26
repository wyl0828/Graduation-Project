package cn.communityelderly.modules.emergency.dto;

public class SosCancelDTO {

    private String cancelReason = "老人确认误触按错";

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
