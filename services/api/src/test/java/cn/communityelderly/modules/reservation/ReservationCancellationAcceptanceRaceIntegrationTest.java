package cn.communityelderly.modules.reservation;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.coordination.ReservationWorkOrderCoordinator;
import cn.communityelderly.modules.audit.entity.AuditLog;
import cn.communityelderly.modules.audit.mapper.AuditLogMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.notification.entity.NotificationEvent;
import cn.communityelderly.modules.notification.mapper.NotificationEventMapper;
import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
import cn.communityelderly.modules.reservation.entity.ReservationCancelRequest;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class ReservationCancellationAcceptanceRaceIntegrationTest {

    private static final Long CHILD_ID = 4L;
    private static final Long ADMIN_ID = 1L;
    private static final Long WORKER_ID = 2L;
    private static final String TERMINATION_NOTIFICATION_KEY_PREFIX = "WORK_ORDER_TERMINATED_PRE_ACCEPTANCE:";
    private static final String RESERVATION_REVIEW_AUDIT_ACTION = "REVIEW_RESERVATION_CANCELLATION";

    @Autowired
    private ServiceReservationService reservationService;

    @Autowired
    private ReservationWorkOrderCoordinator coordinator;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private ServiceReservationMapper reservationMapper;

    @Autowired
    private ReservationCancelRequestMapper cancelRequestMapper;

    @Autowired
    private ReservationExecutionLinkMapper executionLinkMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderActionHistoryMapper actionHistoryMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private NotificationEventMapper notificationEventMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("并发接单与取消审批只提交一方，失败方不留下预约、工单、通知或审计副作用")
    void acceptanceAndCancellationApprovalHaveOneTransactionalWinner() throws Exception {
        Method reviewMethod = findCancellationReviewMethod();
        assertNotNull(reviewMethod, "预约服务需要提供取消申请审批操作");

        Long reservationId = createDispatchedReservationWithPendingCancellation();
        ReservationCancelRequest cancelRequest = cancelRequestMapper.selectOne(
                new LambdaQueryWrapper<ReservationCancelRequest>()
                        .eq(ReservationCancelRequest::getReservationId, reservationId)
                        .eq(ReservationCancelRequest::getStatus, "PENDING_REVIEW"));
        assertNotNull(cancelRequest);

        Long workOrderId = findWorkOrderId(reservationId);
        String terminationDedupKey = TERMINATION_NOTIFICATION_KEY_PREFIX + workOrderId;

        long actionCountBefore = countOrderActions(workOrderId);
        long reservationAuditCountBefore = countAudits("SERVICE_RESERVATION", reservationId.toString(),
                RESERVATION_REVIEW_AUDIT_ACTION);
        long workOrderAuditCountBefore = countAudits("WORK_ORDER", workOrderId.toString(),
                "WORK_ORDER_TERMINATE_PRE_ACCEPTANCE");
        long notificationCountBefore = countNotifications(terminationDedupKey);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> acceptance = executor.submit(() -> runTogether(
                    workerUser(), ready, start,
                    () -> capture(() -> workOrderService.acceptOrder(workOrderId, WORKER_ID))));
            Future<Throwable> cancellationApproval = executor.submit(() -> runTogether(
                    adminUser(), ready, start,
                    () -> capture(() -> invokeCancellationApproval(reviewMethod,
                            cancelRequest.getCancelId(), ADMIN_ID))));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "两个事务线程应到达竞争起点");
            start.countDown();

            Throwable acceptanceFailure = acceptance.get(20, TimeUnit.SECONDS);
            Throwable cancellationFailure = cancellationApproval.get(20, TimeUnit.SECONDS);
            boolean acceptanceWon = acceptanceFailure == null;
            boolean cancellationWon = cancellationFailure == null;
            assertNotEquals(acceptanceWon, cancellationWon, "接单与取消审批必须恰好一方成功");

            Throwable losingFailure = acceptanceWon ? cancellationFailure : acceptanceFailure;
            assertTrue(losingFailure instanceof BusinessException,
                    "失败方应返回业务冲突，实际为: " + losingFailure);
            ErrorCode conflict = ((BusinessException) losingFailure).getErrorCode();
            assertTrue(conflict == ErrorCode.OPTIMISTIC_LOCK_CONFLICT
                            || conflict == ErrorCode.ORDER_STATUS_INVALID,
                    "失败方应看到状态/乐观锁冲突，实际为: " + conflict);

            ServiceReservation finalReservation = reservationMapper.selectById(reservationId);
            ReservationCancelRequest finalRequest = cancelRequestMapper.selectById(cancelRequest.getCancelId());
            WorkOrder finalOrder = workOrderMapper.selectById(workOrderId);
            assertNotNull(finalReservation);
            assertNotNull(finalRequest);
            assertNotNull(finalOrder);

            assertEquals(actionCountBefore + 1, countOrderActions(workOrderId),
                    "只应写入成功路径对应的一条工单流转历史");
            long expectedAuditDelta = cancellationWon ? 1 : 0;
            assertEquals(reservationAuditCountBefore + expectedAuditDelta,
                    countAudits("SERVICE_RESERVATION", reservationId.toString(),
                            RESERVATION_REVIEW_AUDIT_ACTION),
                    "失败方不得留下预约取消审批审计");
            assertEquals(workOrderAuditCountBefore + expectedAuditDelta,
                    countAudits("WORK_ORDER", workOrderId.toString(),
                            "WORK_ORDER_TERMINATE_PRE_ACCEPTANCE"),
                    "失败方不得留下工单终止审计");
            assertEquals(notificationCountBefore + expectedAuditDelta,
                    countNotifications(terminationDedupKey),
                    "审批重试或失败不得重复产生工单终止逻辑通知");

            if (acceptanceWon) {
                assertEquals("ACCEPTED", finalOrder.getStatus());
                assertEquals("CONFIRMED", finalReservation.getStatus());
                assertEquals("PENDING_REVIEW", finalRequest.getStatus());
                assertEquals(1L, countOrderActions(workOrderId,
                        "ACCEPT", "DISPATCHED", "ACCEPTED"));
                assertEquals(0L, countOrderActions(workOrderId,
                        "TERMINATE_PRE_ACCEPTANCE", "DISPATCHED", "TERMINATED_PRE_ACCEPTANCE"));
                assertNull(finalRequest.getReviewerUserId());
                assertNull(finalRequest.getReviewedAt());
                assertNull(finalRequest.getReviewReason());
            } else {
                assertEquals("TERMINATED_PRE_ACCEPTANCE", finalOrder.getStatus());
                assertEquals("CANCELLED", finalReservation.getStatus());
                assertEquals("APPROVED", finalRequest.getStatus());
                assertEquals(ADMIN_ID, finalRequest.getReviewerUserId());
                assertNotNull(finalRequest.getReviewedAt());
                assertEquals("并发测试批准原因", finalRequest.getReviewReason());
                assertEquals(0L, countOrderActions(workOrderId, "ACCEPT", "DISPATCHED", "ACCEPTED"));
                assertEquals(1L, countOrderActions(workOrderId,
                        "TERMINATE_PRE_ACCEPTANCE", "DISPATCHED", "TERMINATED_PRE_ACCEPTANCE"));
            }

            // Replaying the same decision may be rejected or served idempotently, but must not
            // add another logical termination notification, audit row, or status transition.
            capture(() -> invokeCancellationApproval(reviewMethod,
                    cancelRequest.getCancelId(), ADMIN_ID));
            assertEquals(notificationCountBefore + expectedAuditDelta,
                    countNotifications(terminationDedupKey));
            assertEquals(actionCountBefore + 1, countOrderActions(workOrderId));
            assertEquals(reservationAuditCountBefore + expectedAuditDelta,
                    countAudits("SERVICE_RESERVATION", reservationId.toString(),
                            RESERVATION_REVIEW_AUDIT_ACTION));
            assertEquals(workOrderAuditCountBefore + expectedAuditDelta,
                    countAudits("WORK_ORDER", workOrderId.toString(),
                            "WORK_ORDER_TERMINATE_PRE_ACCEPTANCE"));
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("工单已接单后取消申请返回409且不保留申请、通知或申请审计")
    void cancellationApplicationAfterCommittedAcceptanceIsRejectedWithoutSideEffects() {
        Long reservationId = createDispatchedReservationWithoutPendingCancellation();
        Long workOrderId = findWorkOrderId(reservationId);

        UserContext.set(workerUser());
        workOrderService.acceptOrder(workOrderId, WORKER_ID);
        UserContext.set(childUser());

        long requestAuditBefore = countAudits("SERVICE_RESERVATION", reservationId.toString(),
                "REQUEST_RESERVATION_CANCELLATION");
        long reservationNotificationBefore = countReservationNotifications(reservationId);
        long pendingRequestBefore = countPendingCancellationRequests(reservationId);

        BusinessException error = assertThrows(BusinessException.class,
                () -> reservationService.cancelReservation(reservationId, "接单后临时取消", CHILD_ID));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        assertEquals("CONFIRMED", reservationMapper.selectById(reservationId).getStatus());
        assertEquals("ACCEPTED", workOrderMapper.selectById(workOrderId).getStatus());
        assertEquals(pendingRequestBefore, countPendingCancellationRequests(reservationId));
        assertEquals(requestAuditBefore, countAudits("SERVICE_RESERVATION", reservationId.toString(),
                "REQUEST_RESERVATION_CANCELLATION"));
        assertEquals(reservationNotificationBefore, countReservationNotifications(reservationId));
    }

    private Long createDispatchedReservationWithPendingCancellation() {
        Long reservationId = createDispatchedReservationWithoutPendingCancellation();
        UserContext.set(childUser());
        reservationService.cancelReservation(reservationId, "竞争测试取消申请", CHILD_ID);
        UserContext.clear();
        return reservationId;
    }

    private Long createDispatchedReservationWithoutPendingCancellation() {
        UserContext.set(childUser());
        ReservationCreateDTO create = new ReservationCreateDTO();
        create.setElderId(1L);
        create.setServiceType("DAILY_CARE");
        create.setServiceDate(LocalDate.now().plusDays(ThreadLocalRandom.current().nextInt(50, 20_050)));
        create.setSlotCode("MORNING");
        create.setServiceAddress("预约取消竞争集成测试地址");
        create.setSpecialNotes("竞争测试专用预约");
        Long reservationId = reservationService.createReservation(create, CHILD_ID);

        UserContext.set(adminUser());
        coordinator.confirmReservationAndCreateWorkOrder(reservationId, ADMIN_ID);
        Long workOrderId = findWorkOrderId(reservationId);

        WorkOrderDispatchDTO dispatch = new WorkOrderDispatchDTO();
        dispatch.setAssigneeUserId(WORKER_ID);
        dispatch.setReason("预约取消竞争测试派单");
        workOrderService.dispatchOrder(workOrderId, dispatch, ADMIN_ID);
        UserContext.clear();
        return reservationId;
    }

    private Long findWorkOrderId(Long reservationId) {
        ReservationExecutionLink link = executionLinkMapper.selectOne(
                new LambdaQueryWrapper<ReservationExecutionLink>()
                        .eq(ReservationExecutionLink::getReservationId, reservationId));
        return link == null ? null : link.getWorkOrderId();
    }

    private Method findCancellationReviewMethod() {
        return java.util.Arrays.stream(ServiceReservationService.class.getMethods())
                .filter(method -> method.getName().equals("reviewCancellationRequest"))
                .filter(method -> method.getParameterCount() == 3)
                .findFirst()
                .orElse(null);
    }

    private void invokeCancellationApproval(Method method, Long cancelId, Long adminId) throws Exception {
        Object request = objectMapper.readValue(
                "{\"decision\":\"APPROVE\",\"reviewReason\":\"并发测试批准原因\"}",
                method.getParameterTypes()[1]);
        try {
            method.invoke(reservationService, cancelId, request, adminId);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private Throwable runTogether(CurrentUser user, CountDownLatch ready,
                                 CountDownLatch start, ThrowingSupplier action) throws Exception {
        UserContext.set(user);
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                return new IllegalStateException("竞争起点未释放");
            }
            return action.get();
        } finally {
            UserContext.clear();
        }
    }

    private Throwable capture(ThrowingAction action) {
        try {
            action.run();
            return null;
        } catch (Throwable exception) {
            return exception;
        }
    }

    private long countOrderActions(Long workOrderId) {
        return actionHistoryMapper.selectCount(new LambdaQueryWrapper<WorkOrderActionHistory>()
                .eq(WorkOrderActionHistory::getWorkOrderId, workOrderId));
    }

    private long countOrderActions(Long workOrderId, String actionName, String fromStatus, String toStatus) {
        return actionHistoryMapper.selectCount(new LambdaQueryWrapper<WorkOrderActionHistory>()
                .eq(WorkOrderActionHistory::getWorkOrderId, workOrderId)
                .eq(WorkOrderActionHistory::getActionName, actionName)
                .eq(WorkOrderActionHistory::getFromStatus, fromStatus)
                .eq(WorkOrderActionHistory::getToStatus, toStatus));
    }

    private long countAudits(String targetType, String targetId, String action) {
        return auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getTargetType, targetType)
                .eq(AuditLog::getTargetId, targetId)
                .eq(AuditLog::getAction, action));
    }

    private long countNotifications(String dedupKey) {
        return notificationEventMapper.selectCount(new LambdaQueryWrapper<NotificationEvent>()
                .eq(NotificationEvent::getDedupKey, dedupKey));
    }

    private long countPendingCancellationRequests(Long reservationId) {
        return cancelRequestMapper.selectCount(new LambdaQueryWrapper<ReservationCancelRequest>()
                .eq(ReservationCancelRequest::getReservationId, reservationId)
                .eq(ReservationCancelRequest::getStatus, "PENDING_REVIEW"));
    }

    private long countReservationNotifications(Long reservationId) {
        return notificationEventMapper.selectCount(new LambdaQueryWrapper<NotificationEvent>()
                .eq(NotificationEvent::getSourceEntityType, "SERVICE_RESERVATION")
                .eq(NotificationEvent::getSourceEntityId, reservationId.toString()));
    }

    private CurrentUser childUser() {
        return new CurrentUser(String.valueOf(CHILD_ID), "13912345678", "FAMILY",
                List.of("CHILD"), "CHILD", "1");
    }

    private CurrentUser adminUser() {
        return new CurrentUser(String.valueOf(ADMIN_ID), "13800138000", "STAFF",
                List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
    }

    private CurrentUser workerUser() {
        return new CurrentUser(String.valueOf(WORKER_ID), "13800138002", "STAFF",
                List.of("GRID_WORKER"), "GRID_WORKER", "1");
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Throwable get() throws Exception;
    }
}
