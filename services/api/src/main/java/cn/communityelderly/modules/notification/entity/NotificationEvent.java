package cn.communityelderly.modules.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("notification_event")
public class NotificationEvent {

    @TableId(value = "event_id", type = IdType.AUTO)
    private Long eventId;

    private String policyScope; // N01_BUSINESS, FZ02_SECURITY
    private String eventType; // MEDICATION_REMINDER, SOS_ALERT, HEALTH_ALERT
    private String sourceEntityType; // MEDICATION_TASK, SOS_EVENT, HEALTH_ALERT
    private String sourceEntityId;
    private String dedupKey;
    private String title;
    private String content;
    private String payloadJson;
    private LocalDateTime createdAt;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getPolicyScope() {
        return policyScope;
    }

    public void setPolicyScope(String policyScope) {
        this.policyScope = policyScope;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSourceEntityType() {
        return sourceEntityType;
    }

    public void setSourceEntityType(String sourceEntityType) {
        this.sourceEntityType = sourceEntityType;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(String sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
