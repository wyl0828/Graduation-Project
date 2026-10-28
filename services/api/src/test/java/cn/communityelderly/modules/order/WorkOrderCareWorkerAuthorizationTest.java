package cn.communityelderly.modules.order;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.entity.UserRole;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.entity.WorkOrderAssignmentHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderHandoffMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderVO;
import cn.communityelderly.modules.order.mapper.ServiceStopRequestMapper;
import cn.communityelderly.modules.order.service.impl.WorkOrderServiceImpl;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderCareWorkerAuthorizationTest {

    private static final long CARE_WORKER_ID = 77L;
    private static final long COMMUNITY_ID = 1L;

    @AfterEach
    void clearCurrentUser() {
        UserContext.clear();
    }

    @Test
    @DisplayName("护工只列出本人本社区工单且响应仅含执行所需字段")
    void careWorkerListsOnlyAssignedOrdersWithExecutionFields() {
        Fixture fixture = fixture(workOrder(201L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED"));
        when(fixture.workOrderMapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(workOrder(201L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED")));
        when(fixture.elderMapper.selectById(22L)).thenReturn(elder(22L, COMMUNITY_ID));

        List<WorkOrderVO> tasks = assertDoesNotThrow(
                () -> fixture.service.getMyTasks(CARE_WORKER_ID, null));

        assertEquals(1, tasks.size());
        WorkOrderVO task = tasks.get(0);
        assertEquals(201L, task.getWorkOrderId());
        assertEquals("WO201", task.getOrderNo());
        assertEquals("DAILY_CARE", task.getServiceType());
        assertEquals("测试老人22", task.getElderName());
        assertEquals("测试地址", task.getElderAddress());
        assertNull(task.getElderPhone());
        assertNull(task.getElderId());
        assertNull(task.getCommunityId());
        assertNull(task.getSourceReferenceId());
        assertNull(task.getAssigneePhone());
        assertNull(task.getReviewerUserId());
        assertNull(task.getReviewComment());
        verify(fixture.elderMapper, never()).selectList(any(Wrapper.class));

        verify(fixture.userRoleMapper).selectOne(any(Wrapper.class));
        verify(fixture.workOrderMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("护工可读取本人当前承接工单详情但不读取老人完整电话")
    void careWorkerReadsAssignedOrderDetailWithoutElderPhone() {
        Fixture fixture = fixture(workOrder(202L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "ACCEPTED"));
        when(fixture.workOrderMapper.selectById(202L)).thenReturn(
                workOrder(202L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "ACCEPTED"));
        when(fixture.elderMapper.selectById(22L)).thenReturn(elder(22L, COMMUNITY_ID));

        WorkOrderVO detail = assertDoesNotThrow(() -> fixture.service.getOrderDetail(202L));

        assertEquals(202L, detail.getWorkOrderId());
        assertEquals("ACCEPTED", detail.getStatus());
        assertEquals("测试地址", detail.getElderAddress());
        assertNull(detail.getElderPhone());
        assertNull(detail.getCommunityId());
        assertNull(detail.getReviewerName());
        verify(fixture.userRoleMapper).selectOne(any(Wrapper.class));
    }

    @Test
    @DisplayName("护工可以接收当前派给本人的工单")
    void careWorkerAcceptsAssignedOrder() {
        WorkOrder order = workOrder(203L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED");
        Fixture fixture = fixture(order);
        when(fixture.workOrderMapper.selectById(203L)).thenReturn(order);
        when(fixture.workOrderMapper.updateById(order)).thenReturn(1);

        assertDoesNotThrow(() -> fixture.service.acceptOrder(203L, CARE_WORKER_ID));

        assertEquals("ACCEPTED", order.getStatus());
        assertTrue(order.getAcceptTime() != null);
        verify(fixture.workOrderMapper).updateById(order);
        verify(fixture.actionHistoryMapper).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("护工可以开始本人已接收的工单")
    void careWorkerStartsAssignedOrder() {
        WorkOrder order = workOrder(204L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "ACCEPTED");
        Fixture fixture = fixture(order);
        when(fixture.workOrderMapper.selectById(204L)).thenReturn(order);
        when(fixture.workOrderMapper.updateById(order)).thenReturn(1);

        assertDoesNotThrow(() -> fixture.service.startService(204L, CARE_WORKER_ID));

        assertEquals("PROCESSING", order.getStatus());
        assertTrue(order.getServiceStartTime() != null);
        verify(fixture.workOrderMapper).updateById(order);
        verify(fixture.actionHistoryMapper).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("护工可以提交本人执行工单的结果与照片凭据")
    void careWorkerSubmitsAssignedOrderExecution() {
        WorkOrder order = workOrder(205L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "PROCESSING");
        Fixture fixture = fixture(order);
        when(fixture.workOrderMapper.selectById(205L)).thenReturn(order);
        when(fixture.workOrderMapper.updateById(order)).thenReturn(1);

        assertDoesNotThrow(() -> fixture.service.submitExecutionResult(205L, execution(), CARE_WORKER_ID));

        assertEquals("PENDING_RESULT_REVIEW", order.getStatus());
        assertEquals("已完成现场照护", order.getServiceResultSummary());
        assertEquals("/api/v1/files/view/evidence.jpg", order.getPhotoUrls());
        verify(fixture.workOrderMapper).updateById(order);
        verify(fixture.actionHistoryMapper).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("乐观锁冲突时护工执行请求失败且不记录成功历史")
    void careWorkerDoesNotRecordActionHistoryWhenOptimisticUpdateIsLost() {
        WorkOrder acceptOrder = workOrder(216L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED");
        Fixture acceptFixture = fixture(acceptOrder);
        when(acceptFixture.workOrderMapper.selectById(216L)).thenReturn(acceptOrder);
        when(acceptFixture.workOrderMapper.updateById(acceptOrder)).thenReturn(0);

        BusinessException acceptFailure = assertThrows(BusinessException.class,
                () -> acceptFixture.service.acceptOrder(216L, CARE_WORKER_ID));

        assertEquals(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, acceptFailure.getErrorCode());
        verify(acceptFixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));

        WorkOrder startOrder = workOrder(217L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "ACCEPTED");
        Fixture startFixture = fixture(startOrder);
        when(startFixture.workOrderMapper.selectById(217L)).thenReturn(startOrder);
        when(startFixture.workOrderMapper.updateById(startOrder)).thenReturn(0);

        BusinessException startFailure = assertThrows(BusinessException.class,
                () -> startFixture.service.startService(217L, CARE_WORKER_ID));

        assertEquals(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, startFailure.getErrorCode());
        verify(startFixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));

        WorkOrder submitOrder = workOrder(218L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "PROCESSING");
        Fixture submitFixture = fixture(submitOrder);
        when(submitFixture.workOrderMapper.selectById(218L)).thenReturn(submitOrder);
        when(submitFixture.workOrderMapper.updateById(submitOrder)).thenReturn(0);

        BusinessException submitFailure = assertThrows(BusinessException.class,
                () -> submitFixture.service.submitExecutionResult(218L, execution(), CARE_WORKER_ID));

        assertEquals(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, submitFailure.getErrorCode());
        verify(submitFixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("未分配工单在详情和各执行写入前均被拒绝")
    void unassignedOrderIsRejectedBeforeExecutionWrites() {
        assertCareWorkerCannotReadOrMutate(workOrder(206L, 22L, COMMUNITY_ID, null, "DISPATCHED"));
    }

    @Test
    @DisplayName("改派到他人后的工单在详情和各执行写入前均被拒绝")
    void reassignedOrderIsRejectedBeforeExecutionWrites() {
        assertCareWorkerCannotReadOrMutate(workOrder(207L, 22L, COMMUNITY_ID, 88L, "DISPATCHED"));
    }

    @Test
    @DisplayName("其他工作人员承接的工单在详情和各执行写入前均被拒绝")
    void anotherWorkersOrderIsRejectedBeforeExecutionWrites() {
        assertCareWorkerCannotReadOrMutate(workOrder(208L, 22L, COMMUNITY_ID, 88L, "ACCEPTED"));
    }

    @Test
    @DisplayName("本社区护工不能读取或修改其他社区工单")
    void differentCommunityOrderIsRejectedBeforeExecutionWrites() {
        assertCareWorkerCannotReadOrMutate(workOrder(209L, 22L, 2L, CARE_WORKER_ID, "PROCESSING"));
    }

    @Test
    @DisplayName("停用的工作人员账号不能列出本人待办")
    void inactiveStaffAccountCannotListTasks() {
        Fixture fixture = fixture(workOrder(210L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED"));
        UserAccount inactive = staffAccount(CARE_WORKER_ID);
        inactive.setStatus("INACTIVE");
        when(fixture.userAccountMapper.selectById(CARE_WORKER_ID)).thenReturn(inactive);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.getMyTasks(CARE_WORKER_ID, null));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(fixture.workOrderMapper, never()).selectList(any(Wrapper.class));
        verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    @DisplayName("已撤销的护工角色不能接单")
    void revokedCareWorkerAssignmentCannotAcceptOrder() {
        Fixture fixture = fixture(workOrder(211L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED"));
        when(fixture.userRoleMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.acceptOrder(211L, CARE_WORKER_ID));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(fixture.workOrderMapper, never()).selectById(211L);
        verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(fixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("家庭账号即使会话角色标为护工也不能读取工单")
    void nonStaffAccountCannotReadCareWorkerOrder() {
        Fixture fixture = fixture(workOrder(212L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "ACCEPTED"));
        when(fixture.workOrderMapper.selectById(212L)).thenReturn(
                workOrder(212L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "ACCEPTED"));
        UserAccount familyAccount = staffAccount(CARE_WORKER_ID);
        familyAccount.setAccountKind("FAMILY");
        when(fixture.userAccountMapper.selectById(CARE_WORKER_ID)).thenReturn(familyAccount);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.getOrderDetail(212L));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(fixture.workOrderMapper, never()).selectById(212L);
    }

    @Test
    @DisplayName("护工不能访问社区派工池或触发全社区老人枚举")
    void careWorkerCannotUseCommunityWideOrderOrElderAccess() {
        Fixture fixture = fixture(workOrder(213L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED"));

        assertThrows(BusinessException.class, () -> fixture.service.getDispatchPool(null, null));

        verify(fixture.elderMapper, never()).selectList(any(Wrapper.class));
        verify(fixture.workOrderMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("护工按终结状态筛选时仍不能列出终结工单")
    void careWorkerCannotListTerminalOrdersRegardlessOfRequestedStatus() {
        List<String> terminalStatuses = List.of(
                "CLOSED", "VOIDED", "TERMINATED_PRE_ACCEPTANCE", "SERVICE_STOPPED");

        for (int index = 0; index < terminalStatuses.size(); index++) {
            String status = terminalStatuses.get(index);
            long orderId = 219L + index;
            WorkOrder order = workOrder(orderId, 22L, COMMUNITY_ID, CARE_WORKER_ID, status);
            Fixture fixture = fixture(order);
            when(fixture.workOrderMapper.selectList(any(Wrapper.class))).thenReturn(List.of(order));

            List<WorkOrderVO> tasks = fixture.service.getMyTasks(CARE_WORKER_ID, status);

            assertTrue(tasks.isEmpty(), "terminal status must stay unavailable: " + status);
            verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
        }
    }

    @Test
    @DisplayName("护工不能读取或上传使用终结工单")
    void careWorkerCannotReadOrUploadTerminalOrders() {
        List<String> terminalStatuses = List.of(
                "CLOSED", "VOIDED", "TERMINATED_PRE_ACCEPTANCE", "SERVICE_STOPPED");

        for (int index = 0; index < terminalStatuses.size(); index++) {
            String status = terminalStatuses.get(index);
            long orderId = 223L + index;
            WorkOrder order = workOrder(orderId, 22L, COMMUNITY_ID, CARE_WORKER_ID, status);
            Fixture fixture = fixture(order);
            when(fixture.workOrderMapper.selectById(orderId)).thenReturn(order);

            assertRejected(() -> fixture.service.getOrderDetail(orderId));
            assertRejected(() -> fixture.service.assertCurrentCareWorkerMayUpload(orderId));

            verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
            verify(fixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
        }
    }

    @Test
    @DisplayName("护工不能通过错误的 workerUserId 参数查询任务")
    void careWorkerCannotListTasksForMismatchedWorkerId() {
        Fixture fixture = fixture(workOrder(227L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "DISPATCHED"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.getMyTasks(88L, null));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(fixture.workOrderMapper, never()).selectList(any(Wrapper.class));
        verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(fixture.assignmentHistoryMapper, never()).insert(any(WorkOrderAssignmentHistory.class));
        verify(fixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("当前承接工单的护工通过现场照片上传授权检查")
    void assignedCareWorkerMayUploadOrderEvidence() {
        WorkOrder order = workOrder(217L, 22L, COMMUNITY_ID, CARE_WORKER_ID, "PROCESSING");
        Fixture fixture = fixture(order);
        when(fixture.workOrderMapper.selectById(217L)).thenReturn(order);

        assertDoesNotThrow(() -> fixture.service.assertCurrentCareWorkerMayUpload(217L));

        verify(fixture.userAccountMapper).selectById(CARE_WORKER_ID);
        verify(fixture.userRoleMapper).selectOne(any(Wrapper.class));
        verify(fixture.workOrderMapper).selectById(217L);
        verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
    }

    @Test
    @DisplayName("未分配给护工的工单不能通过现场照片上传授权检查")
    void unassignedCareWorkerCannotUploadOrderEvidence() {
        WorkOrder order = workOrder(218L, 22L, COMMUNITY_ID, null, "PROCESSING");
        Fixture fixture = fixture(order);
        when(fixture.workOrderMapper.selectById(218L)).thenReturn(order);

        assertRejected(() -> fixture.service.assertCurrentCareWorkerMayUpload(218L));

        verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(fixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("管理员可将工单派给本社区具有有效护工角色的在职员工")
    void dispatchAcceptsActiveSameCommunityCareWorker() {
        Fixture fixture = fixtureForDispatch(214L, activeStaffAccount(88L), activeRole(88L, "CARE_WORKER", COMMUNITY_ID));
        WorkOrder order = workOrder(214L, 22L, COMMUNITY_ID, null, "PENDING_DISPATCH");
        when(fixture.workOrderMapper.selectById(214L)).thenReturn(order);
        when(fixture.elderMapper.selectById(22L)).thenReturn(elder(22L, COMMUNITY_ID));
        when(fixture.workOrderMapper.updateById(order)).thenReturn(1);

        assertDoesNotThrow(() -> fixture.service.dispatchOrder(214L, dispatch(88L), 10L));

        assertEquals(88L, order.getCurrentAssigneeId());
        assertEquals("DISPATCHED", order.getStatus());
        verify(fixture.workOrderMapper).updateById(order);
        verify(fixture.assignmentHistoryMapper).insert(any(WorkOrderAssignmentHistory.class));
        verifyDispatchRoleQuery(fixture.userRoleMapper, 88L, COMMUNITY_ID);
    }

    @Test
    @DisplayName("管理员不能把工单派给已停用的護工账号")
    void dispatchRejectsInactiveCareWorkerAccount() {
        UserAccount inactive = activeStaffAccount(88L);
        inactive.setStatus("INACTIVE");
        assertDispatchTargetRejected(inactive, activeRole(88L, "CARE_WORKER", COMMUNITY_ID));
    }

    @Test
    @DisplayName("管理员不能把工单派给非工作人员账号")
    void dispatchRejectsNonStaffTarget() {
        UserAccount family = activeStaffAccount(88L);
        family.setAccountKind("FAMILY");
        assertDispatchTargetRejected(family, activeRole(88L, "CARE_WORKER", COMMUNITY_ID));
    }

    @Test
    @DisplayName("管理员不能把工单派给只有其他社区护工角色的员工")
    void dispatchRejectsWrongCommunityCareWorkerAssignment() {
        assertDispatchTargetRejected(activeStaffAccount(88L), activeRole(88L, "CARE_WORKER", 2L));
    }

    private void assertCareWorkerCannotReadOrMutate(WorkOrder order) {
        Fixture fixture = fixture(order);
        when(fixture.workOrderMapper.selectById(order.getWorkOrderId())).thenReturn(order);

        assertRejected(() -> fixture.service.getOrderDetail(order.getWorkOrderId()));
        assertRejected(() -> fixture.service.acceptOrder(order.getWorkOrderId(), CARE_WORKER_ID));
        assertRejected(() -> fixture.service.startService(order.getWorkOrderId(), CARE_WORKER_ID));
        assertRejected(() -> fixture.service.submitExecutionResult(order.getWorkOrderId(), execution(), CARE_WORKER_ID));

        verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(fixture.assignmentHistoryMapper, never()).insert(any(WorkOrderAssignmentHistory.class));
        verify(fixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    private void assertRejected(Runnable operation) {
        BusinessException error = assertThrows(BusinessException.class, operation::run);
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
    }

    private void assertDispatchTargetRejected(UserAccount target, UserRole assignment) {
        Fixture fixture = fixtureForDispatch(215L, target, assignment);
        WorkOrder order = workOrder(215L, 22L, COMMUNITY_ID, null, "PENDING_DISPATCH");
        when(fixture.workOrderMapper.selectById(215L)).thenReturn(order);
        when(fixture.elderMapper.selectById(22L)).thenReturn(elder(22L, COMMUNITY_ID));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.dispatchOrder(215L, dispatch(88L), 10L));

        assertEquals(ErrorCode.USER_NOT_FOUND, error.getErrorCode());
        verify(fixture.workOrderMapper, never()).updateById(any(WorkOrder.class));
        verify(fixture.assignmentHistoryMapper, never()).insert(any(WorkOrderAssignmentHistory.class));
        verify(fixture.actionHistoryMapper, never()).insert(any(WorkOrderActionHistory.class));
        verifyDispatchRoleQuery(fixture.userRoleMapper, 88L, COMMUNITY_ID);
    }

    private Fixture fixture(WorkOrder order) {
        Fixture fixture = new Fixture();
        fixture.workOrderMapper = mock(WorkOrderMapper.class);
        fixture.assignmentHistoryMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        fixture.actionHistoryMapper = mock(WorkOrderActionHistoryMapper.class);
        fixture.elderMapper = mock(ElderProfileMapper.class);
        fixture.userAccountMapper = mock(UserAccountMapper.class);
        fixture.userRoleMapper = mock(UserRoleMapper.class);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(fixture.elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class));
        fixture.service = new WorkOrderServiceImpl(fixture.workOrderMapper, fixture.assignmentHistoryMapper,
                fixture.actionHistoryMapper, mock(WorkOrderHandoffMapper.class), mock(ServiceStopRequestMapper.class),
                fixture.elderMapper, fixture.userAccountMapper, fixture.userRoleMapper,
                mock(ReservationExecutionLinkMapper.class), mock(ServiceReservationMapper.class), policy,
                mock(AuditLogService.class), mock(NotificationService.class), new ObjectMapper(),
                Clock.system(ZoneId.of("Asia/Shanghai")));

        UserContext.set(user(String.valueOf(CARE_WORKER_ID), "CARE_WORKER", String.valueOf(COMMUNITY_ID)));
        when(fixture.userAccountMapper.selectById(CARE_WORKER_ID)).thenReturn(staffAccount(CARE_WORKER_ID));
        when(fixture.userRoleMapper.selectOne(any(Wrapper.class)))
                .thenReturn(activeRole(CARE_WORKER_ID, "CARE_WORKER", COMMUNITY_ID));
        when(fixture.elderMapper.selectById(order.getElderId()))
                .thenReturn(elder(order.getElderId(), order.getCommunityId()));
        return fixture;
    }

    private Fixture fixtureForDispatch(Long orderId, UserAccount targetAccount, UserRole targetAssignment) {
        Fixture fixture = new Fixture();
        fixture.workOrderMapper = mock(WorkOrderMapper.class);
        fixture.assignmentHistoryMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        fixture.actionHistoryMapper = mock(WorkOrderActionHistoryMapper.class);
        fixture.elderMapper = mock(ElderProfileMapper.class);
        fixture.userAccountMapper = mock(UserAccountMapper.class);
        fixture.userRoleMapper = mock(UserRoleMapper.class);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(fixture.elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class));
        fixture.service = new WorkOrderServiceImpl(fixture.workOrderMapper, fixture.assignmentHistoryMapper,
                fixture.actionHistoryMapper, mock(WorkOrderHandoffMapper.class), mock(ServiceStopRequestMapper.class),
                fixture.elderMapper, fixture.userAccountMapper, fixture.userRoleMapper,
                mock(ReservationExecutionLinkMapper.class), mock(ServiceReservationMapper.class), policy,
                mock(AuditLogService.class), mock(NotificationService.class), new ObjectMapper(),
                Clock.system(ZoneId.of("Asia/Shanghai")));

        UserContext.set(user("10", "COMMUNITY_ADMIN", String.valueOf(COMMUNITY_ID)));
        when(fixture.userAccountMapper.selectById(88L)).thenReturn(targetAccount);
        when(fixture.userRoleMapper.selectOne(any(Wrapper.class))).thenReturn(null, targetAssignment);
        return fixture;
    }

    private void verifyDispatchRoleQuery(UserRoleMapper mapper, Long userId, Long communityId) {
        verify(mapper, times(2)).selectOne(any(Wrapper.class));
    }

    private static CurrentUser user(String userId, String role, String communityId) {
        return new CurrentUser(userId, "13900000000", "STAFF", List.of(role), role, communityId);
    }

    private static WorkOrder workOrder(Long id, Long elderId, Long communityId, Long assigneeId, String status) {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderId(id);
        order.setOrderNo("WO" + id);
        order.setSourceType("MANUAL");
        order.setSourceReferenceId("sensitive-source-reference");
        order.setElderId(elderId);
        order.setCommunityId(communityId);
        order.setServiceType("DAILY_CARE");
        order.setOrderTitle("上门照护");
        order.setOrderDescription("协助老人完成日常生活照护");
        order.setUrgencyLevel("NORMAL");
        order.setStatus(status);
        order.setCurrentAssigneeId(assigneeId);
        order.setReviewerUserId(10L);
        order.setReviewComment("不应暴露的审核信息");
        return order;
    }

    private static ElderProfile elder(Long elderId, Long communityId) {
        ElderProfile elder = new ElderProfile();
        elder.setElderId(elderId);
        elder.setCommunityId(communityId);
        elder.setName("测试老人" + elderId);
        elder.setContactPhone("13800000000");
        elder.setAddress("测试地址");
        elder.setStatus("ACTIVE");
        return elder;
    }

    private static UserAccount staffAccount(Long userId) {
        return activeStaffAccount(userId);
    }

    private static UserAccount activeStaffAccount(Long userId) {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setStatus("ACTIVE");
        account.setAccountKind("STAFF");
        account.setPhoneNormalized("13900000000");
        return account;
    }

    private static UserRole activeRole(Long userId, String roleCode, Long communityId) {
        UserRole role = new UserRole();
        role.setUserId(userId);
        role.setRoleCode(roleCode);
        role.setCommunityId(communityId);
        role.setStatus("ACTIVE");
        return role;
    }

    private static WorkOrderExecutionDTO execution() {
        WorkOrderExecutionDTO dto = new WorkOrderExecutionDTO();
        dto.setResultSummary("已完成现场照护");
        dto.setPhotoUrls(List.of("/api/v1/files/view/evidence.jpg"));
        dto.setIsExemptPhoto(false);
        return dto;
    }

    private static WorkOrderDispatchDTO dispatch(Long assigneeId) {
        WorkOrderDispatchDTO dto = new WorkOrderDispatchDTO();
        dto.setAssigneeUserId(assigneeId);
        dto.setReason("测试派单");
        return dto;
    }

    private static class Fixture {
        private WorkOrderMapper workOrderMapper;
        private WorkOrderAssignmentHistoryMapper assignmentHistoryMapper;
        private WorkOrderActionHistoryMapper actionHistoryMapper;
        private ElderProfileMapper elderMapper;
        private UserAccountMapper userAccountMapper;
        private UserRoleMapper userRoleMapper;
        private WorkOrderServiceImpl service;
    }
}
