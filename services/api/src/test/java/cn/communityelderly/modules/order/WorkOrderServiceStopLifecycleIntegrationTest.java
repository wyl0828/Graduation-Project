package cn.communityelderly.modules.order;

import cn.communityelderly.common.idempotency.IdempotencyInterceptor;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.modules.auth.dto.StaffCreateRequest;
import cn.communityelderly.modules.auth.service.impl.AuthSessionAuthenticationService;
import cn.communityelderly.modules.auth.service.StaffManagementService;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class WorkOrderServiceStopLifecycleIntegrationTest {

    private static final String REQUEST_BODY = "{\"stopReason\":\"老人身体不适需暂停服务\","
            + "\"completedContent\":\"已完成室内扶手检查\","
            + "\"incompleteContent\":\"尚未完成浴室防滑处理\","
            + "\"followUpPlan\":\"家属陪同就医后重新预约\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private ServiceReservationMapper reservationMapper;

    @Autowired
    private ReservationExecutionLinkMapper executionLinkMapper;

    @Autowired
    private StaffManagementService staffManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthSessionAuthenticationService sessionAuthenticationService;

    private int reservationDateOffset;

    @BeforeEach
    void setUp() {
        reservationDateOffset = 2;
        authenticate("worker-session", worker(2L));
        authenticate("admin-session", admin());
        authenticate("child-session", child(4L));
    }

    @AfterEach
    void tearDown() {
        cn.communityelderly.common.security.UserContext.clear();
    }

    @Test
    void assigneeStopRequestAndApprovalPreserveDetailsAndProduceOneAtomicTerminalEvent() throws Exception {
        ReservationOrder fixture = createReservationOrder("PROCESSING", 2L, 4L);
        String requestKey = "stop-request-" + UUID.randomUUID();

        MvcResult firstRequest = mockMvc.perform(post(stopRequestPath(fixture.workOrderId()))
                        .header("Authorization", "Bearer worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, requestKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber())
                .andReturn();
        long stopId = readDataLong(firstRequest);

        // Same key and payload must replay without adding a second request or side effect.
        mockMvc.perform(post(stopRequestPath(fixture.workOrderId()))
                        .header("Authorization", "Bearer worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, requestKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(stopId));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_stop_request WHERE stop_id = ? AND status = 'PENDING_REVIEW' "
                        + "AND applicant_user_id = 2 AND stop_reason = '老人身体不适需暂停服务' "
                        + "AND completed_content = '已完成室内扶手检查' "
                        + "AND incomplete_content = '尚未完成浴室防滑处理' "
                        + "AND follow_up_plan = '家属陪同就医后重新预约'",
                Integer.class, stopId));
        assertEquals("PROCESSING", workOrderMapper.selectById(fixture.workOrderId()).getStatus());
        assertEquals("CONFIRMED", reservationMapper.selectById(fixture.reservationId()).getStatus());
        assertEquals(1, countAction(fixture.workOrderId(), "SERVICE_STOP_REQUESTED"));
        assertEquals(1, countAudit(fixture.workOrderId(), "WORK_ORDER_SERVICE_STOP_REQUEST"));
        assertEquals(1, countNotification("WORK_ORDER_STOP_REQUESTED:" + stopId));

        mockMvc.perform(get(stopRequestPath(fixture.workOrderId()))
                        .header("Authorization", "Bearer worker-session"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(stopRequestPath(fixture.workOrderId()))
                        .header("Authorization", "Bearer admin-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stopReason").value("老人身体不适需暂停服务"))
                .andExpect(jsonPath("$.data[0].completedContent").value("已完成室内扶手检查"))
                .andExpect(jsonPath("$.data[0].incompleteContent").value("尚未完成浴室防滑处理"))
                .andExpect(jsonPath("$.data[0].followUpPlan").value("家属陪同就医后重新预约"));

        String reviewPath = reviewPath(fixture.workOrderId(), stopId);
        String reviewBody = "{\"action\":\"APPROVE\",\"reviewComment\":\"核验老人就医安排后批准中止\"}";
        String reviewKey = "stop-review-" + UUID.randomUUID();
        mockMvc.perform(post(reviewPath)
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, reviewKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody))
                .andExpect(status().isOk());

        mockMvc.perform(post(reviewPath)
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, reviewKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody))
                .andExpect(status().isOk());

        assertEquals("SERVICE_STOPPED", workOrderMapper.selectById(fixture.workOrderId()).getStatus());
        assertEquals("SERVICE_STOPPED", reservationMapper.selectById(fixture.reservationId()).getStatus());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_stop_request WHERE stop_id = ? AND status = 'APPROVED' "
                        + "AND applicant_user_id = 2 AND reviewer_user_id = 1 AND executor_user_id = 1 "
                        + "AND reviewed_at IS NOT NULL AND executed_at IS NOT NULL "
                        + "AND review_comment = '核验老人就医安排后批准中止'",
                Integer.class, stopId));
        assertEquals(1, countAction(fixture.workOrderId(), "SERVICE_STOPPED"));
        assertEquals(1, countAudit(fixture.workOrderId(), "WORK_ORDER_SERVICE_STOP_APPROVE"));
        assertEquals(1, countNotification("WORK_ORDER_STOPPED:" + stopId));

        // A new request key cannot re-apply a stop after the order reached its terminal state.
        mockMvc.perform(post(stopRequestPath(fixture.workOrderId()))
                        .header("Authorization", "Bearer worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "after-stop-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isConflict());
        assertEquals(1, countAction(fixture.workOrderId(), "SERVICE_STOPPED"));
    }

    @Test
    void originalReservationApplicantMayRequestAndRejectedStopLeavesExecutionActive() throws Exception {
        ReservationOrder fixture = createReservationOrder("ACCEPTED", 2L, 4L);
        String requestKey = "child-stop-" + UUID.randomUUID();
        MvcResult request = mockMvc.perform(post(stopRequestPath(fixture.workOrderId()))
                        .header("Authorization", "Bearer child-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, requestKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andReturn();
        long stopId = readDataLong(request);

        mockMvc.perform(post(reviewPath(fixture.workOrderId(), stopId))
                        .header("Authorization", "Bearer child-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "child-review-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"reviewComment\":\"家属自行批准\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(reviewPath(fixture.workOrderId(), stopId))
                        .header("Authorization", "Bearer admin-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "reject-stop-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REJECT\",\"reviewComment\":\"待与家属确认可执行时间\"}"))
                .andExpect(status().isOk());

        assertEquals("ACCEPTED", workOrderMapper.selectById(fixture.workOrderId()).getStatus());
        assertEquals("CONFIRMED", reservationMapper.selectById(fixture.reservationId()).getStatus());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_stop_request WHERE stop_id = ? AND status = 'REJECTED' "
                        + "AND applicant_user_id = 4 AND reviewer_user_id = 1 "
                        + "AND executed_at IS NULL AND review_comment = '待与家属确认可执行时间'",
                Integer.class, stopId));
        assertEquals(1, countAction(fixture.workOrderId(), "SERVICE_STOP_REJECTED"));
        assertEquals(1, countAudit(fixture.workOrderId(), "WORK_ORDER_SERVICE_STOP_REJECT"));
        assertEquals(1, countNotification("WORK_ORDER_STOP_REJECTED:" + stopId));
    }

    @Test
    void anotherReservationApplicantAndNonAssigneeCannotRequestStop() throws Exception {
        ReservationOrder fixture = createReservationOrder("PROCESSING", 2L, 4L);
        String phone = "139" + String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100000000));
        StaffCreateRequest staff = new StaffCreateRequest();
        staff.setPhone(phone);
        staff.setInitialPassword("ValidPass123");
        staff.setRoles(List.of("GRID_WORKER"));
        cn.communityelderly.common.security.UserContext.set(admin());
        Long otherWorkerId = staffManagementService.createStaff(staff).getUserId();
        authenticate("other-worker-session", worker(otherWorkerId));

        mockMvc.perform(post(stopRequestPath(fixture.workOrderId()))
                        .header("Authorization", "Bearer other-worker-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "other-worker-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400
                        && result.getResponse().getStatus() < 500));

        // The user is an active family member, but this reservation names a different applicant.
        ReservationOrder otherApplicant = createReservationOrder("PROCESSING", 2L, 5L);
        mockMvc.perform(post(stopRequestPath(otherApplicant.workOrderId()))
                        .header("Authorization", "Bearer child-session")
                        .header(IdempotencyInterceptor.IDEMPOTENCY_KEY_HEADER, "other-applicant-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400
                        && result.getResponse().getStatus() < 500));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_stop_request WHERE work_order_id IN (?, ?)",
                Integer.class, fixture.workOrderId(), otherApplicant.workOrderId()));
        assertEquals(0, countNotification("WORK_ORDER_STOP_REQUESTED:" + fixture.workOrderId()));
    }

    private ReservationOrder createReservationOrder(String orderStatus, Long assigneeId, Long applicantId) {
        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationNo("RSV" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        reservation.setElderId(1L);
        reservation.setApplicantUserId(applicantId);
        reservation.setServiceType("DAILY_CARE");
        reservation.setServiceDate(LocalDate.now().plusDays(reservationDateOffset++));
        reservation.setSlotCode("MORNING");
        reservation.setServiceAddress("停用流程测试地址");
        reservation.setSpecialNotes("中止审批集成测试");
        reservation.setStatus("CONFIRMED");
        reservation.setRowVersion(1L);
        assertEquals(1, reservationMapper.insert(reservation));

        WorkOrder order = new WorkOrder();
        order.setOrderNo("WO" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        order.setSourceType("RESERVATION");
        order.setSourceReferenceId(reservation.getReservationId().toString());
        order.setElderId(1L);
        order.setCommunityId(1L);
        order.setServiceType("DAILY_CARE");
        order.setOrderTitle("服务中止集成测试");
        order.setOrderDescription("预约派生工单");
        order.setUrgencyLevel("NORMAL");
        order.setStatus(orderStatus);
        order.setCurrentAssigneeId(assigneeId);
        order.setResultPhotoRequired(false);
        order.setRowVersion(1L);
        assertEquals(1, workOrderMapper.insert(order));

        ReservationExecutionLink link = new ReservationExecutionLink();
        link.setReservationId(reservation.getReservationId());
        link.setWorkOrderId(order.getWorkOrderId());
        assertEquals(1, executionLinkMapper.insert(link));
        return new ReservationOrder(reservation.getReservationId(), order.getWorkOrderId());
    }

    private void authenticate(String token, CurrentUser user) {
        when(sessionAuthenticationService.authenticate(eq(token), anyString())).thenReturn(user);
    }

    private long readDataLong(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.path("data").asLong();
    }

    private String stopRequestPath(Long workOrderId) {
        return "/api/v1/work-orders/" + workOrderId + "/service-stop-requests";
    }

    private String reviewPath(Long workOrderId, long stopId) {
        return stopRequestPath(workOrderId) + "/" + stopId + "/review";
    }

    private int countAction(Long workOrderId, String actionName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_action_history WHERE work_order_id = ? AND action_name = ?",
                Integer.class, workOrderId, actionName);
    }

    private int countAudit(Long workOrderId, String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE target_type = 'WORK_ORDER' AND target_id = ? AND action = ?",
                Integer.class, workOrderId.toString(), action);
    }

    private int countNotification(String dedupKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_event WHERE dedup_key = ?", Integer.class, dedupKey);
    }

    private CurrentUser worker(Long userId) {
        return new CurrentUser(String.valueOf(userId), "13800138002", "STAFF", List.of("GRID_WORKER"),
                "GRID_WORKER", "1", 200L, "session-worker-" + userId, "WEB");
    }

    private CurrentUser admin() {
        return new CurrentUser("1", "13800138000", "STAFF", List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN", "1", 100L, "session-admin", "WEB");
    }

    private CurrentUser child(Long userId) {
        return new CurrentUser(String.valueOf(userId), "13800138004", "FAMILY", List.of("CHILD"),
                "CHILD", "1", 400L, "session-child-" + userId, "MINIAPP");
    }

    private record ReservationOrder(Long reservationId, Long workOrderId) {}
}
