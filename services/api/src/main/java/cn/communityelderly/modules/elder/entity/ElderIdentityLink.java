package cn.communityelderly.modules.elder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("elder_identity_link")
public class ElderIdentityLink {

    @TableId(value = "link_id", type = IdType.AUTO)
    private Long linkId;

    private Long elderId;
    private Long userId;
    private String linkedByActivationTicketId;
    private LocalDateTime linkedAt;

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
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

    public String getLinkedByActivationTicketId() {
        return linkedByActivationTicketId;
    }

    public void setLinkedByActivationTicketId(String linkedByActivationTicketId) {
        this.linkedByActivationTicketId = linkedByActivationTicketId;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(LocalDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }
}
