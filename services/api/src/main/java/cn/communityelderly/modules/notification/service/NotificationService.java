package cn.communityelderly.modules.notification.service;

import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.dto.NotificationItemVO;
import cn.communityelderly.modules.notification.dto.NotificationQueryDTO;

import java.util.List;

public interface NotificationService {

    Long publishEvent(NotificationCreateEventDTO dto);

    List<NotificationItemVO> listMyNotifications(NotificationQueryDTO query);

    Long countUnread();

    void markAsRead(Long deliveryId);

    void markAllAsRead();
}
