package cn.communityelderly.modules.notification.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.notification.dto.NotificationItemVO;
import cn.communityelderly.modules.notification.dto.NotificationQueryDTO;
import cn.communityelderly.modules.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // 1. 查询我的消息列表 (P-20)
    @GetMapping("/my")
    @RequireRoles({"CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<List<NotificationItemVO>> listMyNotifications(NotificationQueryDTO query) {
        return Result.success(notificationService.listMyNotifications(query));
    }

    // 2. 查询未读消息总数 (红点徽章)
    @GetMapping("/unread-count")
    @RequireRoles({"CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<Long> getUnreadCount() {
        return Result.success(notificationService.countUnread());
    }

    // 3. 标为已读
    @PostMapping("/{deliveryId}/read")
    @RequireRoles({"CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<Void> markAsRead(@PathVariable Long deliveryId) {
        notificationService.markAsRead(deliveryId);
        return Result.success();
    }

    // 4. 一键全部标为已读
    @PostMapping("/read-all")
    @RequireRoles({"CHILD", "ELDER", "COMMUNITY_ADMIN", "GRID_WORKER"})
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return Result.success();
    }
}
