package cn.communityelderly.modules.notification.dto;

public class NotificationQueryDTO {

    private String type = "ALL"; // ALL, MEDICATION_REMINDER, HEALTH_ALERT, SOS_ALERT
    private Boolean readStatus;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Boolean getReadStatus() {
        return readStatus;
    }

    public void setReadStatus(Boolean readStatus) {
        this.readStatus = readStatus;
    }
}
