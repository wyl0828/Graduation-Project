package cn.communityelderly.modules.order.dto;

import java.time.LocalDateTime;

public record ServiceStopRequestVO(
        Long stopId,
        Long reservationId,
        Long workOrderId,
        Long applicantUserId,
        String stopReason,
        String completedContent,
        String incompleteContent,
        String followUpPlan,
        String status,
        Long reviewerUserId,
        LocalDateTime reviewedAt,
        String reviewComment,
        Long executorUserId,
        LocalDateTime executedAt,
        LocalDateTime createdAt) {
}
