package cn.communityelderly.modules.order;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.coordination.ReservationWorkOrderCoordinator;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.auth.mapper.UserRoleMapper;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.entity.WorkOrderAssignmentHistory;
import cn.communityelderly.modules.emergency.mapper.WorkOrderHandoffMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderAssignmentHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.notification.service.NotificationService;
import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderReviewDTO;
import cn.communityelderly.modules.order.mapper.ServiceStopRequestMapper;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.order.service.impl.WorkOrderServiceImpl;
import cn.communityelderly.modules.reservation.entity.ReservationCancelRequest;
import cn.communityelderly.modules.reservation.entity.ReservationExecutionLink;
import cn.communityelderly.modules.reservation.entity.ServiceCatalog;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ReservationCancelRequestMapper;
import cn.communityelderly.modules.reservation.mapper.ReservationExecutionLinkMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceCatalogMapper;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import cn.communityelderly.modules.reservation.service.impl.ServiceReservationServiceImpl;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.time.Clock;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkOrderReservationAuthorizationTest {

    @AfterEach
    void clearCurrentUser() {
        UserContext.clear();
    }

    @Test
    @DisplayName("子女不能读取无有效亲情关系的他人预约详情")
    void childCannotReadAnotherPersonsReservationDetail() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        ServiceReservation reservation = reservation(101L, 2L, 44L, "PENDING_CONFIRMATION");
        when(reservationMapper.selectById(101L)).thenReturn(reservation);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        when(familyMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        ServiceReservationService service = reservationService(reservationMapper, elderMapper, familyMapper);
        UserContext.set(user("55", "CHILD", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.getReservationDetail(101L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
    }

    @Test
    @DisplayName("即使有有效亲情关系，非原申请人也不能取消待确认预约")
    void differentApplicantCannotCancelPendingReservation() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationCancelRequestMapper cancelMapper = mock(ReservationCancelRequestMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        ServiceReservation reservation = reservation(102L, 2L, 44L, "PENDING_CONFIRMATION");
        when(reservationMapper.selectByIdForUpdate(102L)).thenReturn(reservation);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        ElderFamilyLink activeLink = new ElderFamilyLink();
        activeLink.setElderId(2L);
        activeLink.setChildUserId(55L);
        activeLink.setStatus("ACTIVE");
        when(familyMapper.selectOne(any(Wrapper.class))).thenReturn(activeLink);

        ServiceReservationService service = reservationService(reservationMapper, cancelMapper, elderMapper, familyMapper);
        UserContext.set(user("55", "CHILD", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.cancelReservation(102L, "撤回", 55L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(reservationMapper, never()).updateById(any(ServiceReservation.class));
        verify(cancelMapper, never()).insert(any(ReservationCancelRequest.class));
    }

    @Test
    @DisplayName("老人本人不能绕过预约申请人规则直接创建预约")
    void elderCannotCreateServiceReservation() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ServiceCatalogMapper catalogMapper = mock(ServiceCatalogMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderIdentityLinkMapper identityMapper = mock(ElderIdentityLinkMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(identity(31L, 2L));
        ServiceCatalog catalog = new ServiceCatalog();
        catalog.setServiceType("DAILY_CARE");
        catalog.setStatus("ACTIVE");
        when(catalogMapper.selectOne(any(Wrapper.class))).thenReturn(catalog);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper, identityMapper, familyMapper);
        ServiceReservationServiceImpl service = new ServiceReservationServiceImpl(
                reservationMapper, catalogMapper, mock(ReservationExecutionLinkMapper.class),
                mock(ReservationCancelRequestMapper.class), elderMapper, familyMapper, identityMapper,
                mock(WorkOrderMapper.class), mock(UserAccountMapper.class), policy,
                mock(WorkOrderService.class), mock(AuditLogService.class), mock(NotificationService.class),
                new ObjectMapper(), Clock.system(ZoneId.of("Asia/Shanghai")));
        UserContext.set(user("31", "ELDER", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.createReservation(createReservation(), 31L));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(reservationMapper, never()).insert(any(ServiceReservation.class));
    }

    @Test
    @DisplayName("老人不能取消由子女申请的预约")
    void elderCannotCancelReservationAppliedByChild() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationCancelRequestMapper cancelMapper = mock(ReservationCancelRequestMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ElderIdentityLinkMapper identityMapper = mock(ElderIdentityLinkMapper.class);
        ElderFamilyLinkMapper familyMapper = mock(ElderFamilyLinkMapper.class);
        when(reservationMapper.selectByIdForUpdate(105L)).thenReturn(reservation(105L, 2L, 44L, "PENDING_CONFIRMATION"));
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        when(identityMapper.selectOne(any(Wrapper.class))).thenReturn(identity(31L, 2L));
        ServiceReservationServiceImpl service = new ServiceReservationServiceImpl(
                reservationMapper, null, null, cancelMapper, elderMapper, familyMapper, identityMapper,
                mock(WorkOrderMapper.class), mock(UserAccountMapper.class),
                new ObjectAuthorizationPolicy(elderMapper, identityMapper, familyMapper),
                mock(WorkOrderService.class), mock(AuditLogService.class), mock(NotificationService.class),
                new ObjectMapper(), Clock.system(ZoneId.of("Asia/Shanghai")));
        UserContext.set(user("31", "ELDER", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.cancelReservation(105L, "撤回", 31L));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(reservationMapper, never()).updateById(any(ServiceReservation.class));
        verify(cancelMapper, never()).insert(any(ReservationCancelRequest.class));
    }

    @Test
    @DisplayName("非社区管理员不能查询待审批预约列表")
    void gridWorkerCannotQueryReservationApprovalPool() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationExecutionLinkMapper linkMapper = mock(ReservationExecutionLinkMapper.class);
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderActionHistoryMapper historyMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ServiceReservationService service = mock(ServiceReservationService.class);
        when(elderMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        ReservationWorkOrderCoordinator coordinator = new ReservationWorkOrderCoordinator(
                reservationMapper, linkMapper, orderMapper, historyMapper, elderMapper, service,
                new ObjectAuthorizationPolicy(elderMapper, mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class)),
                mock(cn.communityelderly.modules.audit.service.AuditLogService.class),
                Clock.system(ZoneId.of("Asia/Shanghai")));
        UserContext.set(user("20", "GRID_WORKER", "1"));

        BusinessException error = assertThrows(BusinessException.class, coordinator::getPendingReservations);

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(reservationMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("其他社区管理员不能确认预约或生成工单、关联和审计记录")
    void otherCommunityAdministratorCannotConfirmReservation() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationExecutionLinkMapper linkMapper = mock(ReservationExecutionLinkMapper.class);
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderActionHistoryMapper historyMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ServiceReservationService service = mock(ServiceReservationService.class);
        ServiceReservation reservation = reservation(103L, 22L, 44L, "PENDING_CONFIRMATION");
        when(reservationMapper.selectById(103L)).thenReturn(reservation);
        when(elderMapper.selectById(22L)).thenReturn(elder(22L, 2L));
        ReservationWorkOrderCoordinator coordinator = new ReservationWorkOrderCoordinator(
                reservationMapper, linkMapper, orderMapper, historyMapper, elderMapper, service,
                new ObjectAuthorizationPolicy(elderMapper, mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class)),
                mock(AuditLogService.class), Clock.system(ZoneId.of("Asia/Shanghai")));
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> coordinator.confirmReservationAndCreateWorkOrder(103L, 10L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(reservationMapper, never()).updateById(any(ServiceReservation.class));
        verify(orderMapper, never()).insert(any(WorkOrder.class));
        verify(linkMapper, never()).insert(any(ReservationExecutionLink.class));
        verify(historyMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("其他社区管理员不能拒绝预约，原预约状态和原因保持不变")
    void otherCommunityAdministratorCannotRejectReservation() {
        ServiceReservationMapper reservationMapper = mock(ServiceReservationMapper.class);
        ReservationExecutionLinkMapper linkMapper = mock(ReservationExecutionLinkMapper.class);
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderActionHistoryMapper historyMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        ServiceReservationService service = mock(ServiceReservationService.class);
        ServiceReservation reservation = reservation(104L, 22L, 44L, "PENDING_CONFIRMATION");
        when(reservationMapper.selectById(104L)).thenReturn(reservation);
        when(elderMapper.selectById(22L)).thenReturn(elder(22L, 2L));
        ReservationWorkOrderCoordinator coordinator = new ReservationWorkOrderCoordinator(
                reservationMapper, linkMapper, orderMapper, historyMapper, elderMapper, service,
                new ObjectAuthorizationPolicy(elderMapper, mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class)),
                mock(AuditLogService.class), Clock.system(ZoneId.of("Asia/Shanghai")));
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> coordinator.rejectReservation(104L, "原因", 10L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(reservationMapper, never()).updateById(any(ServiceReservation.class));
    }

    @Test
    @DisplayName("任意非授权角色不能读取、派单或审核工单，也不能留下流转副作用")
    void unrelatedRoleCannotReadDispatchOrReviewWorkOrder() {
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderAssignmentHistoryMapper assignmentMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        WorkOrderActionHistoryMapper actionMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkOrder order = workOrder(201L, 2L, 1L, 77L, "PENDING_DISPATCH");
        when(orderMapper.selectById(201L)).thenReturn(order);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        WorkOrderServiceImpl service = workOrderService(orderMapper, assignmentMapper, actionMapper, elderMapper, userMapper);
        UserContext.set(user("55", "OTHER", "1"));

        assertThrows(BusinessException.class, () -> service.getOrderDetail(201L));
        assertThrows(BusinessException.class, () -> service.dispatchOrder(201L, dispatch(88L), 55L));
        assertThrows(BusinessException.class, () -> service.reviewWorkOrder(201L, review(), 55L));

        verify(orderMapper, never()).updateById(any(WorkOrder.class));
        verify(assignmentMapper, never()).insert(any(WorkOrderAssignmentHistory.class));
        verify(actionMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("本社区管理员不能跨社区派单，且不写工单或分配历史")
    void administratorCannotDispatchOtherCommunityWorkOrder() {
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderAssignmentHistoryMapper assignmentMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        WorkOrderActionHistoryMapper actionMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkOrder order = workOrder(202L, 22L, 2L, null, "PENDING_DISPATCH");
        when(orderMapper.selectById(202L)).thenReturn(order);
        when(elderMapper.selectById(22L)).thenReturn(elder(22L, 2L));
        WorkOrderServiceImpl service = workOrderService(orderMapper, assignmentMapper, actionMapper, elderMapper, userMapper);
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.dispatchOrder(202L, dispatch(88L), 10L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(orderMapper, never()).updateById(any(WorkOrder.class));
        verify(assignmentMapper, never()).insert(any(WorkOrderAssignmentHistory.class));
        verify(actionMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("网格员不能审核其他执行人的工单")
    void workerCannotReviewAnotherWorkersWorkOrder() {
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderAssignmentHistoryMapper assignmentMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        WorkOrderActionHistoryMapper actionMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkOrder order = workOrder(203L, 2L, 1L, 77L, "PENDING_RESULT_REVIEW");
        when(orderMapper.selectById(203L)).thenReturn(order);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        WorkOrderServiceImpl service = workOrderService(orderMapper, assignmentMapper, actionMapper, elderMapper, userMapper);
        UserContext.set(user("66", "GRID_WORKER", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reviewWorkOrder(203L, review(), 66L));

        assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
        verify(orderMapper, never()).updateById(any(WorkOrder.class));
        verify(actionMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("执行人不能审核自己的工单，审核状态和历史均不变")
    void assignedWorkerCannotSelfReview() {
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderAssignmentHistoryMapper assignmentMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        WorkOrderActionHistoryMapper actionMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkOrder order = workOrder(204L, 2L, 1L, 66L, "PENDING_RESULT_REVIEW");
        when(orderMapper.selectById(204L)).thenReturn(order);
        when(orderMapper.selectOne(any(Wrapper.class))).thenReturn(order);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        WorkOrderServiceImpl service = workOrderService(orderMapper, assignmentMapper, actionMapper, elderMapper, userMapper);
        UserContext.set(user("66", "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.reviewWorkOrder(204L, review(), 66L));

        assertEquals(ErrorCode.FORBIDDEN_SELF_REVIEW, error.getErrorCode());
        verify(orderMapper, never()).updateById(any(WorkOrder.class));
        verify(actionMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    @Test
    @DisplayName("工单社区必须与老人档案社区一致，伪造的工单社区不能授权读取")
    void workOrderCommunityMustMatchElderCommunity() {
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderAssignmentHistoryMapper assignmentMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        WorkOrderActionHistoryMapper actionMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        WorkOrder order = workOrder(205L, 2L, 2L, 77L, "PENDING_DISPATCH");
        when(orderMapper.selectById(205L)).thenReturn(order);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        WorkOrderServiceImpl service = workOrderService(orderMapper, assignmentMapper, actionMapper, elderMapper, userMapper);
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.getOrderDetail(205L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
    }

    @Test
    @DisplayName("不能把工单派给没有本社区有效网格员分配的账号")
    void cannotDispatchToAccountWithoutActiveCommunityWorkerAssignment() {
        WorkOrderMapper orderMapper = mock(WorkOrderMapper.class);
        WorkOrderAssignmentHistoryMapper assignmentMapper = mock(WorkOrderAssignmentHistoryMapper.class);
        WorkOrderActionHistoryMapper actionMapper = mock(WorkOrderActionHistoryMapper.class);
        ElderProfileMapper elderMapper = mock(ElderProfileMapper.class);
        UserAccountMapper userMapper = mock(UserAccountMapper.class);
        UserRoleMapper roleMapper = mock(UserRoleMapper.class);
        WorkOrder order = workOrder(206L, 2L, 1L, null, "PENDING_DISPATCH");
        when(orderMapper.selectById(206L)).thenReturn(order);
        when(elderMapper.selectById(2L)).thenReturn(elder(2L, 1L));
        UserAccount inactive = new UserAccount();
        inactive.setUserId(88L);
        inactive.setStatus("ACTIVE");
        inactive.setAccountKind("STAFF");
        when(userMapper.selectById(88L)).thenReturn(inactive);
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        WorkOrderServiceImpl service = workOrderService(orderMapper, assignmentMapper, actionMapper,
                elderMapper, userMapper, roleMapper);
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.dispatchOrder(206L, dispatch(88L), 10L));

        assertEquals(ErrorCode.USER_NOT_FOUND, error.getErrorCode());
        verify(orderMapper, never()).updateById(any(WorkOrder.class));
        verify(assignmentMapper, never()).insert(any(WorkOrderAssignmentHistory.class));
        verify(actionMapper, never()).insert(any(WorkOrderActionHistory.class));
    }

    private ServiceReservationServiceImpl reservationService(ServiceReservationMapper reservationMapper,
                                                             ElderProfileMapper elderMapper,
                                                             ElderFamilyLinkMapper familyMapper) {
        return reservationService(reservationMapper, mock(ReservationCancelRequestMapper.class), elderMapper, familyMapper);
    }

    private ServiceReservationServiceImpl reservationService(ServiceReservationMapper reservationMapper,
                                                             ReservationCancelRequestMapper cancelMapper,
                                                             ElderProfileMapper elderMapper,
                                                             ElderFamilyLinkMapper familyMapper) {
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), familyMapper);
        return new ServiceReservationServiceImpl(reservationMapper, null, null, cancelMapper, elderMapper, familyMapper,
                mock(ElderIdentityLinkMapper.class), mock(WorkOrderMapper.class), mock(UserAccountMapper.class), policy,
                mock(WorkOrderService.class), mock(AuditLogService.class), mock(NotificationService.class),
                new ObjectMapper(), Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    private WorkOrderServiceImpl workOrderService(WorkOrderMapper orderMapper,
                                                  WorkOrderAssignmentHistoryMapper assignmentMapper,
                                                  WorkOrderActionHistoryMapper actionMapper,
                                                  ElderProfileMapper elderMapper,
                                                  UserAccountMapper userMapper) {
        return workOrderService(orderMapper, assignmentMapper, actionMapper, elderMapper,
                userMapper, mock(UserRoleMapper.class));
    }

    private WorkOrderServiceImpl workOrderService(WorkOrderMapper orderMapper,
                                                  WorkOrderAssignmentHistoryMapper assignmentMapper,
                                                  WorkOrderActionHistoryMapper actionMapper,
                                                  ElderProfileMapper elderMapper,
                                                  UserAccountMapper userMapper,
                                                  UserRoleMapper roleMapper) {
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(elderMapper,
                mock(ElderIdentityLinkMapper.class), mock(ElderFamilyLinkMapper.class));
        return new WorkOrderServiceImpl(orderMapper, assignmentMapper, actionMapper,
                mock(WorkOrderHandoffMapper.class), mock(ServiceStopRequestMapper.class),
                elderMapper, userMapper, roleMapper, mock(ReservationExecutionLinkMapper.class),
                mock(ServiceReservationMapper.class), policy, mock(AuditLogService.class),
                mock(NotificationService.class), new ObjectMapper(), mock(cn.communityelderly.modules.order.service.FileEvidenceService.class), Clock.system(ZoneId.of("Asia/Shanghai")));
    }

    private static CurrentUser user(String userId, String role, String communityId) {
        return new CurrentUser(userId, "13900000000", "STAFF", List.of(role), role, communityId);
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

    private static ServiceReservation reservation(Long id, Long elderId, Long applicantId, String status) {
        ServiceReservation reservation = new ServiceReservation();
        reservation.setReservationId(id);
        reservation.setReservationNo("RSV" + id);
        reservation.setElderId(elderId);
        reservation.setApplicantUserId(applicantId);
        reservation.setServiceType("DAILY_CARE");
        reservation.setStatus(status);
        reservation.setServiceAddress("测试地址");
        return reservation;
    }

    private static WorkOrder workOrder(Long id, Long elderId, Long communityId, Long assigneeId, String status) {
        WorkOrder order = new WorkOrder();
        order.setWorkOrderId(id);
        order.setOrderNo("WO" + id);
        order.setElderId(elderId);
        order.setCommunityId(communityId);
        order.setCurrentAssigneeId(assigneeId);
        order.setStatus(status);
        order.setSourceType("MANUAL");
        return order;
    }

    private static WorkOrderDispatchDTO dispatch(Long assigneeId) {
        WorkOrderDispatchDTO dto = new WorkOrderDispatchDTO();
        dto.setAssigneeUserId(assigneeId);
        dto.setReason("测试派单");
        return dto;
    }

    private static WorkOrderReviewDTO review() {
        WorkOrderReviewDTO dto = new WorkOrderReviewDTO();
        dto.setAction("APPROVE");
        dto.setReviewComment("测试审核");
        return dto;
    }

    private static cn.communityelderly.modules.reservation.dto.ReservationCreateDTO createReservation() {
        cn.communityelderly.modules.reservation.dto.ReservationCreateDTO dto =
                new cn.communityelderly.modules.reservation.dto.ReservationCreateDTO();
        dto.setElderId(2L);
        dto.setServiceType("DAILY_CARE");
        dto.setServiceDate(java.time.LocalDate.now().plusDays(2));
        dto.setSlotCode("MORNING");
        dto.setServiceAddress("测试地址");
        return dto;
    }

    private static ElderIdentityLink identity(Long userId, Long elderId) {
        ElderIdentityLink link = new ElderIdentityLink();
        link.setUserId(userId);
        link.setElderId(elderId);
        return link;
    }
}
