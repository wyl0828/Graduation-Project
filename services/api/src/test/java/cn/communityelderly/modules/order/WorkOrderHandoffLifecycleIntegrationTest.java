package cn.communityelderly.modules.order;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.idempotency.IdempotencyInterceptor;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.dto.StaffCreateRequest;
import cn.communityelderly.modules.auth.dto.StaffRolesUpdateRequest;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import cn.communityelderly.modules.auth.service.StaffManagementService;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class WorkOrderHandoffLifecycleIntegrationTest {

    @Autowired
    private StaffManagementService staffManagementService;

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private ElderProfileMapper elderProfileMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthSessionAuthenticationService sessionAuthenticationService;

    private CurrentUser adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new CurrentUser("1", "13800138000", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        UserContext.set(adminUser);
        when(sessionAuthenticationService.authenticate(eq("admin-session"), anyString())).thenReturn(adminUser);
        when(sessionAuthenticationService.authenticate(eq("old-worker-session"), anyString()))
                .thenReturn(worker(2L));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void revokingWorkerRolePreservesOpenOrderAndCreatesOnlyOnePendingHandoff() {
        WorkOrder order = createOrder("PROCESSING", 2L, 1L);

        StaffRolesUpdateRequest revoke = new StaffRolesUpdateRequest();
        revoke.setRoles(List.of());
        revoke.setReason("工作人员岗位调整");
        staffManagementService.replaceRoles(2L, revoke);

        WorkOrder afterRevocation = workOrderMapper.selectById(order.getWorkOrderId());
        assertEquals("PROCESSING", afterRevocation.getStatus());
        assertEquals(2L, afterRevocation.getCurrentAssigneeId());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_handoff WHERE work_order_id = ?",
                Integer.class, order.getWorkOrderId()));

        Map<String, Object> handoff = jdbcTemplate.queryForMap(
                "SELECT handoff_id, status, from_user_id, to_user_id, handoff_reason " +
                        "FROM work_order_handoff WHERE work_order_id = ?",
                order.getWorkOrderId());
        Long handoffId = ((Number) handoff.get("handoff_id")).longValue();
        assertEquals("REASSIGNMENT_REQUIRED", handoff.get("status"));
        assertEquals(2L, ((Number) handoff.get("from_user_id")).longValue());
        assertNull(handoff.get("to_user_id"));
        assertEquals("工作人员岗位调整", handoff.get("handoff_reason"));

        // A retry after the role is already revoked must not add another handoff or notification.
        staffManagementService.replaceRoles(2L, revoke);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_handoff WHERE work_order_id = ?",
                Integer.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, "WORK_ORDER_HANDOFF_REQUIRED:" + handoffId));

        UserContext.set(new CurrentUser("2", "13800138002", "STAFF", List.of("GRID_WORKER"), "GRID_WORKER", "1"));
        WorkOrderExecutionDTO execution = new WorkOrderExecutionDTO();
        execution.setIsExemptPhoto(true);
        execution.setPhotoExemptionReason("老人身体状况特殊，现场不宜拍照留存");
        execution.setResultSummary("服务处理中止前的有效记录");
        BusinessException denied = assertThrows(BusinessException.class,
                () -> workOrderService.submitExecutionResult(order.getWorkOrderId(), execution, 2L));
        assertEquals(ErrorCode.ACCESS_DENIED, denied.getErrorCode());
    }

    @Test
    void roleRevocationHandoffPreservesEveryAssignedNonTerminalWorkState() throws Exception {
        List<String> transferableStatuses = List.of(
                "DISPATCHED", "ACCEPTED", "PROCESSING", "PENDING_RESULT_REVIEW");
        List<WorkOrder> orders = new java.util.ArrayList<>();
        for (String status : transferableStatuses) {
            String initialStatus = "PENDING_RESULT_REVIEW".equals(status) ? "PROCESSING" : status;
            WorkOrder order = createOrder(initialStatus, 2L, 1L);
            orders.add(order);
            if ("PENDING_RESULT_REVIEW".equals(status)) {
                UserContext.set(new CurrentUser("2", "13800138002", "STAFF",
                        List.of("GRID_WORKER"), "GRID_WORKER", "1"));
                WorkOrderExecutionDTO submittedResult = new WorkOrderExecutionDTO();
                submittedResult.setIsExemptPhoto(true);
                submittedResult.setPhotoExemptionReason("现场条件不适合拍照留存");
                submittedResult.setResultSummary("已接手前的执行进度");
                workOrderService.submitExecutionResult(order.getWorkOrderId(), submittedResult, 2L);
                UserContext.set(adminUser);
            }
        }

        StaffRolesUpdateRequest revoke = new StaffRolesUpdateRequest();
        revoke.setRoles(List.of());
        revoke.setReason("人员离岗，待接单、处理中及待审核责任统一交接");
        staffManagementService.replaceRoles(2L, revoke);

        Long targetUserId = createGridWorker();
        when(sessionAuthenticationService.authenticate(eq("matrix-target-session"), anyString()))
                .thenReturn(worker(targetUserId));
        for (int index = 0; index < orders.size(); index++) {
            WorkOrder order = orders.get(index);
            String originalStatus = transferableStatuses.get(index);
            assertEquals("REASSIGNMENT_REQUIRED", jdbcTemplate.queryForObject(
                    "SELECT status FROM work_order_handoff WHERE work_order_id = ?",
                    String.class, order.getWorkOrderId()));

            mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                            .header("Authorization", "Bearer admin-session")
                            .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER,
                                    "matrix-request-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"toUserId\":" + targetUserId
                                    + ",\"reason\":\"按工单当前状态保留进度完成责任交接\"}"))
                    .andExpect(status().isOk());

            Long handoffId = jdbcTemplate.queryForObject(
                    "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                    Long.class, order.getWorkOrderId());
            mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + handoffId + "/accept")
                            .header("Authorization", "Bearer matrix-target-session")
                            .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER,
                                    "matrix-accept-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());

            WorkOrder transferred = workOrderMapper.selectById(order.getWorkOrderId());
            assertEquals(originalStatus, transferred.getStatus());
            assertEquals(targetUserId, transferred.getCurrentAssigneeId());
            assertEquals("已接手前的执行进度", transferred.getServiceResultSummary());
            assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                    "SELECT status FROM work_order_handoff WHERE handoff_id = ?",
                    String.class, handoffId));
            if ("PENDING_RESULT_REVIEW".equals(originalStatus)) {
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM work_order_action_history WHERE work_order_id = ? "
                                + "AND action_name = 'SUBMIT_RESULT'",
                        Integer.class, order.getWorkOrderId()));
            }
        }
    }

    @Test
    void roleRevocationDoesNotCreateHandoffsForUnassignedOrTerminalOrders() {
        List<WorkOrder> orders = new java.util.ArrayList<>();
        orders.add(createOrder("PENDING_DISPATCH", null, 1L));
        orders.add(createOrder("CLOSED", 2L, 1L));
        orders.add(createOrder("VOIDED", 2L, 1L));
        orders.add(createOrder("TERMINATED_PRE_ACCEPTANCE", 2L, 1L));
        orders.add(createOrder("SERVICE_STOPPED", 2L, 1L));

        StaffRolesUpdateRequest revoke = new StaffRolesUpdateRequest();
        revoke.setRoles(List.of());
        revoke.setReason("人员离岗，不应给无责任或终态工单建立交接");
        staffManagementService.replaceRoles(2L, revoke);

        for (WorkOrder order : orders) {
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM work_order_handoff WHERE work_order_id = ?",
                    Integer.class, order.getWorkOrderId()));
        }
    }

    @Test
    void roleRevocationFailsClosedForUnsupportedAssignedNonTerminalState() {
        WorkOrder unsupportedOrder = createOrder("RETURNED", 2L, 1L);
        StaffRolesUpdateRequest revoke = new StaffRolesUpdateRequest();
        revoke.setRoles(List.of());
        revoke.setReason("人员离岗，存在待专项处理状态");

        BusinessException rejected = assertThrows(BusinessException.class,
                () -> staffManagementService.replaceRoles(2L, revoke));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, rejected.getErrorCode());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id = 2 AND community_id = 1 "
                        + "AND role_code = 'GRID_WORKER' AND status = 'ACTIVE'",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_handoff WHERE work_order_id = ?",
                Integer.class, unsupportedOrder.getWorkOrderId()));
    }

    @Test
    void deactivatingElderIsRejectedUntilEveryOpenOrderHasAnAcceptedHandoff() throws Exception {
        Long elderId = createTestElder();
        WorkOrder order = createOrder("PROCESSING", 2L, elderId);

        StaffRolesUpdateRequest revoke = new StaffRolesUpdateRequest();
        revoke.setRoles(List.of());
        revoke.setReason("人员离岗，工单责任待交接");
        staffManagementService.replaceRoles(2L, revoke);
        WorkOrder afterRevocation = workOrderMapper.selectById(order.getWorkOrderId());
        assertEquals("PROCESSING", afterRevocation.getStatus());
        assertEquals(2L, afterRevocation.getCurrentAssigneeId());
        assertEquals("REASSIGNMENT_REQUIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE work_order_id = ?",
                String.class, order.getWorkOrderId()));

        BusinessException blocked = assertThrows(BusinessException.class,
                () -> elderArchiveService.updateElderStatus(elderId, "INACTIVE", "档案核验停用"));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, blocked.getErrorCode());
        assertEquals("ACTIVE", elderProfileMapper.selectById(elderId).getStatus());

        Long targetUserId = createGridWorker();
        Long otherWorkerId = createGridWorker();
        when(sessionAuthenticationService.authenticate(eq("target-worker-session"), anyString()))
                .thenReturn(worker(targetUserId));
        when(sessionAuthenticationService.authenticate(eq("other-worker-session"), anyString()))
                .thenReturn(worker(otherWorkerId));

        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "invalid-handoff-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":3,\"reason\":\"交接至不具备工作人员资格的账号\"}"))
                .andExpect(status().is4xxClientError());
        assertEquals("REASSIGNMENT_REQUIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE work_order_id = ?",
                String.class, order.getWorkOrderId()));

        mockMvc.perform(post("/api/v1/work-orders/" + order.getWorkOrderId() + "/dispatch")
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "ordinary-reassign-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeUserId\":" + targetUserId
                                + ",\"reason\":\"不允许用普通改派处理处理中工单\"}"))
                .andExpect(status().isConflict());

        String requestKey = "handoff-request-" + UUID.randomUUID();
        String requestBody = "{\"toUserId\":" + targetUserId
                + ",\"reason\":\"原承接人离岗，交由有效工作人员继续服务\"}";
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, requestKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber());
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, requestKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        Long handoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                Long.class, order.getWorkOrderId());
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT from_user_id FROM work_order_handoff WHERE handoff_id = ?", Long.class, handoffId));
        assertEquals(targetUserId, jdbcTemplate.queryForObject(
                "SELECT to_user_id FROM work_order_handoff WHERE handoff_id = ?", Long.class, handoffId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_handoff WHERE work_order_id = ?",
                Integer.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_action_history WHERE work_order_id = ? "
                        + "AND action_name = 'HANDOFF_REQUESTED'",
                Integer.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE target_type = 'WORK_ORDER' AND target_id = ? "
                        + "AND action = 'WORK_ORDER_HANDOFF_REQUESTED'",
                Integer.class, order.getWorkOrderId().toString()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, "WORK_ORDER_HANDOFF_REQUESTED:" + handoffId));

        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + handoffId + "/accept")
                        .header("Authorization", "Bearer old-worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "wrong-recipient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseComment\":\"不是指定接收人\"}"))
                .andExpect(status().is4xxClientError());
        assertEquals("REQUESTED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE handoff_id = ?", String.class, handoffId));
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + handoffId + "/accept")
                        .header("Authorization", "Bearer other-worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "wrong-active-recipient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseComment\":\"不是管理员指定的接收人\"}"))
                .andExpect(status().is4xxClientError());
        assertEquals("REQUESTED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE handoff_id = ?", String.class, handoffId));

        String acceptKey = "handoff-accept-" + UUID.randomUUID();
        String acceptBody = "{\"responseComment\":\"已查看进度并确认接手\"}";
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + handoffId + "/accept")
                        .header("Authorization", "Bearer target-worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, acceptKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody))
                .andExpect(status().isOk());
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + handoffId + "/accept")
                        .header("Authorization", "Bearer target-worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, acceptKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptBody))
                .andExpect(status().isOk());

        assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE handoff_id = ?", String.class, handoffId));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT responded_at FROM work_order_handoff WHERE handoff_id = ?",
                java.time.LocalDateTime.class, handoffId));
        assertEquals("已接手前的执行进度", jdbcTemplate.queryForObject(
                "SELECT service_result_summary FROM work_order WHERE work_order_id = ?",
                String.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_action_history WHERE work_order_id = ? "
                        + "AND action_name = 'HANDOFF_ACCEPTED'",
                Integer.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE target_type = 'WORK_ORDER' AND target_id = ? "
                        + "AND action = 'WORK_ORDER_HANDOFF_ACCEPT'",
                Integer.class, order.getWorkOrderId().toString()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, "WORK_ORDER_HANDOFF_ACCEPTED:" + handoffId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_assignment_history WHERE work_order_id = ? "
                        + "AND action_type = 'HANDOFF' AND assigned_to_user_id = ?",
                Integer.class, order.getWorkOrderId(), targetUserId));

        UserContext.set(adminUser);
        elderArchiveService.updateElderStatus(elderId, "INACTIVE", "档案核验停用");
        assertEquals("INACTIVE", elderProfileMapper.selectById(elderId).getStatus());
        assertEquals(targetUserId, workOrderMapper.selectById(order.getWorkOrderId()).getCurrentAssigneeId());
        assertEquals("PROCESSING", workOrderMapper.selectById(order.getWorkOrderId()).getStatus());
    }

    @Test
    void deactivationRequiresLatestHandoffToBeAcceptedByCurrentAssignee() throws Exception {
        Long elderId = createTestElder();
        WorkOrder order = createOrder("PROCESSING", 2L, elderId);

        StaffRolesUpdateRequest revokeOriginalWorker = new StaffRolesUpdateRequest();
        revokeOriginalWorker.setRoles(List.of());
        revokeOriginalWorker.setReason("原工作人员离岗，责任交接给下一位工作人员");
        staffManagementService.replaceRoles(2L, revokeOriginalWorker);

        Long workerBId = createGridWorker();
        when(sessionAuthenticationService.authenticate(eq("worker-b-session"), anyString()))
                .thenReturn(worker(workerBId));
        Long handoffToB = requestAndAcceptHandoff(order.getWorkOrderId(), workerBId,
                "worker-b-session", "handoff-to-b");

        StaffRolesUpdateRequest revokeWorkerB = new StaffRolesUpdateRequest();
        revokeWorkerB.setRoles(List.of());
        revokeWorkerB.setReason("工作人员 B 离岗，责任需要再次交接");
        UserContext.set(adminUser);
        staffManagementService.replaceRoles(workerBId, revokeWorkerB);

        assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE handoff_id = ?", String.class, handoffToB));
        assertEquals("REASSIGNMENT_REQUIRED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE work_order_id = ? ORDER BY handoff_id DESC LIMIT 1",
                String.class, order.getWorkOrderId()));
        UserContext.set(adminUser);
        BusinessException blocked = assertThrows(BusinessException.class,
                () -> elderArchiveService.updateElderStatus(elderId, "INACTIVE", "档案核验停用"));
        assertEquals(ErrorCode.ORDER_STATUS_INVALID, blocked.getErrorCode());
        assertEquals("ACTIVE", elderProfileMapper.selectById(elderId).getStatus());

        UserContext.set(adminUser);
        Long workerCId = createGridWorker();
        when(sessionAuthenticationService.authenticate(eq("worker-c-session"), anyString()))
                .thenReturn(worker(workerCId));
        Long handoffToC = requestAndAcceptHandoff(order.getWorkOrderId(), workerCId,
                "worker-c-session", "handoff-to-c");

        assertNotEquals(handoffToB, handoffToC);
        assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE handoff_id = ?", String.class, handoffToC));
        assertEquals(workerCId, jdbcTemplate.queryForObject(
                "SELECT to_user_id FROM work_order_handoff WHERE handoff_id = ?", Long.class, handoffToC));
        UserContext.set(adminUser);
        elderArchiveService.updateElderStatus(elderId, "INACTIVE", "交接完成后停用老人档案");
        assertEquals("INACTIVE", elderProfileMapper.selectById(elderId).getStatus());
    }

    @Test
    void designatedRecipientCanRejectThenAdminCanReassignAndNewRecipientCanAccept() throws Exception {
        WorkOrder order = createOrder("PROCESSING", 2L, 1L);
        Long firstTargetId = createGridWorker();
        Long replacementTargetId = createGridWorker();
        when(sessionAuthenticationService.authenticate(eq("first-target-session"), anyString()))
                .thenReturn(worker(firstTargetId));
        when(sessionAuthenticationService.authenticate(eq("replacement-target-session"), anyString()))
                .thenReturn(worker(replacementTargetId));

        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "reject-flow-request-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + firstTargetId + ",\"reason\":\"首轮交接请求\"}"))
                .andExpect(status().isOk());
        Long rejectedHandoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                Long.class, order.getWorkOrderId());

        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + rejectedHandoffId + "/reject")
                        .header("Authorization", "Bearer old-worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "reject-wrong-recipient-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseComment\":\"非指定接收人不得拒绝\"}"))
                .andExpect(status().is4xxClientError());
        assertEquals("REQUESTED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE handoff_id = ?", String.class, rejectedHandoffId));

        String rejectKey = "reject-flow-reject-" + UUID.randomUUID();
        String rejectBody = "{\"responseComment\":\"当前排班冲突，无法承担该工单\"}";
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + rejectedHandoffId + "/reject")
                        .header("Authorization", "Bearer first-target-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, rejectKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectBody))
                .andExpect(status().isOk());
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + rejectedHandoffId + "/reject")
                        .header("Authorization", "Bearer first-target-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, rejectKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectBody))
                .andExpect(status().isOk());

        Map<String, Object> rejected = jdbcTemplate.queryForMap(
                "SELECT status, to_user_id, handoff_reason, responded_at, response_comment "
                        + "FROM work_order_handoff WHERE handoff_id = ?", rejectedHandoffId);
        assertEquals("REJECTED", rejected.get("status"));
        assertEquals(firstTargetId.longValue(), ((Number) rejected.get("to_user_id")).longValue());
        assertEquals("首轮交接请求", rejected.get("handoff_reason"));
        assertNotNull(rejected.get("responded_at"));
        assertEquals("当前排班冲突，无法承担该工单", rejected.get("response_comment"));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_action_history WHERE work_order_id = ? AND action_name = 'HANDOFF_REJECTED'",
                Integer.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE target_type = 'WORK_ORDER' AND target_id = ? "
                        + "AND action = 'WORK_ORDER_HANDOFF_REJECT'",
                Integer.class, order.getWorkOrderId().toString()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, "WORK_ORDER_HANDOFF_REJECTED:" + rejectedHandoffId));

        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "reject-flow-reassign-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + replacementTargetId
                                + ",\"reason\":\"拒绝后重新安排有效接收人\"}"))
                .andExpect(status().isOk());
        Long acceptedHandoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                Long.class, order.getWorkOrderId());
        assertNotEquals(rejectedHandoffId, acceptedHandoffId);
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_handoff WHERE work_order_id = ?",
                Integer.class, order.getWorkOrderId()));
        assertEquals("REJECTED", jdbcTemplate.queryForObject(
                "SELECT status FROM work_order_handoff WHERE handoff_id = ?", String.class, rejectedHandoffId));

        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + acceptedHandoffId + "/accept")
                        .header("Authorization", "Bearer replacement-target-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "reject-flow-accept-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        WorkOrder transferred = workOrderMapper.selectById(order.getWorkOrderId());
        assertEquals("PROCESSING", transferred.getStatus());
        assertEquals(replacementTargetId, transferred.getCurrentAssigneeId());
        assertEquals("已接手前的执行进度", transferred.getServiceResultSummary());
    }

    @Test
    void administratorCanReplacePendingHandoffTargetWithoutErasingPriorTargetOrReason() throws Exception {
        WorkOrder order = createOrder("PROCESSING", 2L, 1L);
        Long firstTargetId = createGridWorker();
        Long replacementTargetId = createGridWorker();

        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "replace-target-first-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + firstTargetId + ",\"reason\":\"最初指定接收人\"}"))
                .andExpect(status().isOk());
        Long previousHandoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                Long.class, order.getWorkOrderId());

        String replacementKey = "replace-target-second-" + UUID.randomUUID();
        String replacementBody = "{\"toUserId\":" + replacementTargetId + ",\"reason\":\"管理员调整为另一有效接收人\"}";
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, replacementKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replacementBody))
                .andExpect(status().isOk());
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, replacementKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(replacementBody))
                .andExpect(status().isOk());

        Map<String, Object> previous = jdbcTemplate.queryForMap(
                "SELECT status, to_user_id, handoff_reason, responded_at, response_comment "
                        + "FROM work_order_handoff WHERE handoff_id = ?", previousHandoffId);
        assertEquals("CANCELLED", previous.get("status"));
        assertEquals(firstTargetId.longValue(), ((Number) previous.get("to_user_id")).longValue());
        assertEquals("最初指定接收人", previous.get("handoff_reason"));
        assertNotNull(previous.get("responded_at"));
        assertTrue(((String) previous.get("response_comment")).contains("管理员调整目标"));

        Long replacementHandoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                Long.class, order.getWorkOrderId());
        assertNotEquals(previousHandoffId, replacementHandoffId);
        Map<String, Object> replacement = jdbcTemplate.queryForMap(
                "SELECT to_user_id, handoff_reason FROM work_order_handoff WHERE handoff_id = ?",
                replacementHandoffId);
        assertEquals(replacementTargetId.longValue(), ((Number) replacement.get("to_user_id")).longValue());
        assertEquals("管理员调整为另一有效接收人", replacement.get("handoff_reason"));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_handoff WHERE work_order_id = ?",
                Integer.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_action_history WHERE work_order_id = ? AND action_name = 'HANDOFF_TARGET_REPLACED'",
                Integer.class, order.getWorkOrderId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, "WORK_ORDER_HANDOFF_TARGET_REPLACED:" + previousHandoffId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, "WORK_ORDER_HANDOFF_REQUESTED:" + replacementHandoffId));
        Long replacementEventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM notification_event WHERE dedup_key = ?",
                Long.class, "WORK_ORDER_HANDOFF_REQUESTED:" + replacementHandoffId);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_delivery WHERE event_id = ? AND recipient_user_id = ?",
                Integer.class, replacementEventId, replacementTargetId));
    }

    @Test
    void repeatedRevocationRoundsCreateIndependentHandoffRequiredNotifications() throws Exception {
        WorkOrder order = createOrder("PROCESSING", 2L, 1L);
        StaffRolesUpdateRequest revoke = new StaffRolesUpdateRequest();
        revoke.setRoles(List.of());
        revoke.setReason("工作人员离岗，待责任交接");
        staffManagementService.replaceRoles(2L, revoke);
        Long firstHandoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND from_user_id = 2",
                Long.class, order.getWorkOrderId());

        Long nextWorkerId = createGridWorker();
        when(sessionAuthenticationService.authenticate(eq("round-two-target-session"), anyString()))
                .thenReturn(worker(nextWorkerId));
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "round-one-request-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + nextWorkerId + ",\"reason\":\"第一轮撤权后的交接\"}"))
                .andExpect(status().isOk());
        Long acceptedHandoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                Long.class, order.getWorkOrderId());
        mockMvc.perform(post(handoffPath(order.getWorkOrderId()) + "/" + acceptedHandoffId + "/accept")
                        .header("Authorization", "Bearer round-two-target-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "round-one-accept-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        StaffRolesUpdateRequest revokeSecondWorker = new StaffRolesUpdateRequest();
        revokeSecondWorker.setRoles(List.of());
        revokeSecondWorker.setReason("第二轮人员离岗，重新安排责任交接");
        UserContext.set(adminUser);
        staffManagementService.replaceRoles(nextWorkerId, revokeSecondWorker);
        Long secondHandoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND from_user_id = ? "
                        + "AND status = 'REASSIGNMENT_REQUIRED'",
                Long.class, order.getWorkOrderId(), nextWorkerId);

        assertNotEquals(firstHandoffId, secondHandoffId);
        String firstDedupKey = "WORK_ORDER_HANDOFF_REQUIRED:" + firstHandoffId;
        String secondDedupKey = "WORK_ORDER_HANDOFF_REQUIRED:" + secondHandoffId;
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, firstDedupKey));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?",
                Integer.class, secondDedupKey));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE event_type = 'WORK_ORDER_HANDOFF_REQUIRED' "
                        + "AND dedup_key IN (?, ?)", Integer.class, firstDedupKey, secondDedupKey));
    }

    private Long createGridWorker() {
        StaffCreateRequest request = new StaffCreateRequest();
        request.setPhone("139" + String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100000000)));
        request.setInitialPassword("ValidPass123");
        request.setRoles(List.of("GRID_WORKER"));
        return staffManagementService.createStaff(request).getUserId();
    }

    private CurrentUser worker(Long userId) {
        return new CurrentUser(String.valueOf(userId), "13900000002", "STAFF", List.of("GRID_WORKER"),
                "GRID_WORKER", "1", userId == 2L ? 2L : null, "worker-session-" + userId, "WEB");
    }

    private String handoffPath(Long workOrderId) {
        return "/api/v1/work-orders/" + workOrderId + "/handoffs";
    }

    private Long requestAndAcceptHandoff(Long workOrderId, Long targetUserId,
                                         String targetSession, String idempotencyPrefix) throws Exception {
        mockMvc.perform(post(handoffPath(workOrderId))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER,
                                idempotencyPrefix + "-request-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toUserId\":" + targetUserId
                                + ",\"reason\":\"工作人员离岗后的责任交接\"}"))
                .andExpect(status().isOk());

        Long handoffId = jdbcTemplate.queryForObject(
                "SELECT handoff_id FROM work_order_handoff WHERE work_order_id = ? AND status = 'REQUESTED'",
                Long.class, workOrderId);
        mockMvc.perform(post(handoffPath(workOrderId) + "/" + handoffId + "/accept")
                        .header("Authorization", "Bearer " + targetSession)
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER,
                                idempotencyPrefix + "-accept-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        return handoffId;
    }

    private Long createTestElder() {
        ElderProfile elder = new ElderProfile();
        elder.setArchiveNo("WOH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        elder.setCommunityId(1L);
        elder.setName("交接停用测试老人");
        elder.setSex("UNKNOWN");
        elder.setAddress("责任交接集成测试地址");
        elder.setStatus("ACTIVE");
        elder.setRowVersion(1L);
        elder.setCreatedBy(1L);
        elder.setUpdatedBy(1L);
        assertEquals(1, elderProfileMapper.insert(elder));
        return elder.getElderId();
    }

    private WorkOrder createOrder(String status, Long assigneeId, Long elderId) {
        WorkOrder order = new WorkOrder();
        order.setOrderNo("WO-TEST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        order.setSourceType("MANUAL");
        order.setSourceReferenceId("handoff-test-" + UUID.randomUUID());
        order.setElderId(elderId);
        order.setCommunityId(1L);
        order.setServiceType("DAILY_CARE");
        order.setOrderTitle("责任交接集成测试");
        order.setOrderDescription("处理中工单");
        order.setUrgencyLevel("NORMAL");
        order.setStatus(status);
        order.setCurrentAssigneeId(assigneeId);
        order.setServiceResultSummary("已接手前的执行进度");
        order.setServiceStartTime(java.time.LocalDateTime.of(2026, 10, 28, 9, 0));
        order.setResultPhotoRequired(false);
        order.setRowVersion(1L);
        assertEquals(1, workOrderMapper.insert(order));
        return order;
    }
}
