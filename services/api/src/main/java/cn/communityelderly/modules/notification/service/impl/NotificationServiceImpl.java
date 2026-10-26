package cn.communityelderly.modules.notification.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.notification.dto.NotificationCreateEventDTO;
import cn.communityelderly.modules.notification.dto.NotificationItemVO;
import cn.communityelderly.modules.notification.dto.NotificationQueryDTO;
import cn.communityelderly.modules.notification.entity.NotificationDelivery;
import cn.communityelderly.modules.notification.entity.NotificationEvent;
import cn.communityelderly.modules.notification.mapper.NotificationDeliveryMapper;
import cn.communityelderly.modules.notification.mapper.NotificationEventMapper;
import cn.communityelderly.modules.notification.service.NotificationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationEventMapper eventMapper;
    private final NotificationDeliveryMapper deliveryMapper;

    public NotificationServiceImpl(NotificationEventMapper eventMapper,
                                   NotificationDeliveryMapper deliveryMapper) {
        this.eventMapper = eventMapper;
        this.deliveryMapper = deliveryMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishEvent(NotificationCreateEventDTO dto) {
        if (!StringUtils.hasText(dto.getDedupKey())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "通知业务去重键不能为空");
        }

        // 1. 业务去重检查 (BR-14 / 防骚扰)
        NotificationEvent existing = eventMapper.selectOne(
                new LambdaQueryWrapper<NotificationEvent>()
                        .eq(NotificationEvent::getDedupKey, dto.getDedupKey().trim())
        );

        NotificationEvent event;
        if (existing != null) {
            event = existing;
        } else {
            event = new NotificationEvent();
            event.setPolicyScope(StringUtils.hasText(dto.getPolicyScope()) ? dto.getPolicyScope() : "N01_BUSINESS");
            event.setEventType(dto.getEventType());
            event.setSourceEntityType(dto.getSourceEntityType());
            event.setSourceEntityId(dto.getSourceEntityId());
            event.setDedupKey(dto.getDedupKey().trim());
            event.setTitle(dto.getTitle());
            event.setContent(dto.getContent());
            event.setPayloadJson(dto.getPayloadJson());
            event.setCreatedAt(LocalDateTime.now());
            eventMapper.insert(event);
        }

        // 2. 逐人生成投递记录
        if (dto.getRecipientUserIds() != null && !dto.getRecipientUserIds().isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Long recipientId : dto.getRecipientUserIds()) {
                NotificationDelivery existingDelivery = deliveryMapper.selectOne(
                        new LambdaQueryWrapper<NotificationDelivery>()
                                .eq(NotificationDelivery::getEventId, event.getEventId())
                                .eq(NotificationDelivery::getRecipientUserId, recipientId)
                                .eq(NotificationDelivery::getChannel, "IN_APP")
                );
                if (existingDelivery == null) {
                    NotificationDelivery delivery = new NotificationDelivery();
                    delivery.setEventId(event.getEventId());
                    delivery.setRecipientUserId(recipientId);
                    delivery.setChannel("IN_APP");
                    delivery.setStatus("SENT");
                    delivery.setRetryCount(0);
                    delivery.setReadStatus(false);
                    delivery.setCreatedAt(now);
                    deliveryMapper.insert(delivery);
                }
            }
        }

        return event.getEventId();
    }

    @Override
    public List<NotificationItemVO> listMyNotifications(NotificationQueryDTO query) {
        CurrentUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "请先登录查看通知消息");
        }
        Long userId = Long.parseLong(user.getUserId());

        LambdaQueryWrapper<NotificationDelivery> wrapper = new LambdaQueryWrapper<NotificationDelivery>()
                .eq(NotificationDelivery::getRecipientUserId, userId)
                .orderByDesc(NotificationDelivery::getCreatedAt);

        if (query != null && query.getReadStatus() != null) {
            wrapper.eq(NotificationDelivery::getReadStatus, query.getReadStatus());
        }

        List<NotificationDelivery> deliveries = deliveryMapper.selectList(wrapper);
        if (deliveries.isEmpty()) {
            return List.of();
        }

        List<Long> eventIds = deliveries.stream().map(NotificationDelivery::getEventId).distinct().toList();
        List<NotificationEvent> events = eventMapper.selectBatchIds(eventIds);
        Map<Long, NotificationEvent> eventMap = events.stream().collect(Collectors.toMap(NotificationEvent::getEventId, e -> e));

        List<NotificationItemVO> result = new ArrayList<>();
        for (NotificationDelivery d : deliveries) {
            NotificationEvent e = eventMap.get(d.getEventId());
            if (e == null) continue;

            if (query != null && StringUtils.hasText(query.getType()) && !"ALL".equalsIgnoreCase(query.getType())) {
                if (!query.getType().equalsIgnoreCase(e.getEventType())) {
                    continue;
                }
            }

            NotificationItemVO vo = new NotificationItemVO();
            vo.setDeliveryId(d.getDeliveryId());
            vo.setEventId(e.getEventId());
            vo.setPolicyScope(e.getPolicyScope());
            vo.setEventType(e.getEventType());
            vo.setTitle(e.getTitle());
            vo.setContent(e.getContent());
            vo.setPayloadJson(e.getPayloadJson());
            vo.setChannel(d.getChannel());
            vo.setReadStatus(d.getReadStatus());
            vo.setReadAt(d.getReadAt());
            vo.setCreatedAt(d.getCreatedAt());
            result.add(vo);
        }

        return result;
    }

    @Override
    public Long countUnread() {
        CurrentUser user = UserContext.get();
        if (user == null) return 0L;
        Long userId = Long.parseLong(user.getUserId());

        return deliveryMapper.selectCount(
                new LambdaQueryWrapper<NotificationDelivery>()
                        .eq(NotificationDelivery::getRecipientUserId, userId)
                        .eq(NotificationDelivery::getReadStatus, false)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long deliveryId) {
        CurrentUser user = UserContext.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "请先登录");
        }
        Long userId = Long.parseLong(user.getUserId());

        NotificationDelivery delivery = deliveryMapper.selectById(deliveryId);
        if (delivery == null || !delivery.getRecipientUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "通知不存在或无权操作");
        }

        if (Boolean.FALSE.equals(delivery.getReadStatus())) {
            delivery.setReadStatus(true);
            delivery.setReadAt(LocalDateTime.now());
            deliveryMapper.updateById(delivery);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead() {
        CurrentUser user = UserContext.get();
        if (user == null) return;
        Long userId = Long.parseLong(user.getUserId());

        List<NotificationDelivery> unreadList = deliveryMapper.selectList(
                new LambdaQueryWrapper<NotificationDelivery>()
                        .eq(NotificationDelivery::getRecipientUserId, userId)
                        .eq(NotificationDelivery::getReadStatus, false)
        );

        LocalDateTime now = LocalDateTime.now();
        for (NotificationDelivery d : unreadList) {
            d.setReadStatus(true);
            d.setReadAt(now);
            deliveryMapper.updateById(d);
        }
    }
}
