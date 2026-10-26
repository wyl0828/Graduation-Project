package cn.communityelderly.modules.order;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.coordination.ReservationWorkOrderCoordinator;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.order.dto.WorkOrderDispatchDTO;
import cn.communityelderly.modules.order.dto.WorkOrderExecutionDTO;
import cn.communityelderly.modules.order.dto.WorkOrderReviewDTO;
import cn.communityelderly.modules.order.dto.WorkOrderVO;
import cn.communityelderly.modules.order.service.WorkOrderService;
import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
import cn.communityelderly.modules.reservation.dto.ReservationVO;
import cn.communityelderly.modules.reservation.dto.ServiceCatalogVO;
import cn.communityelderly.modules.reservation.entity.ServiceReservation;
import cn.communityelderly.modules.reservation.mapper.ServiceReservationMapper;
import cn.communityelderly.modules.reservation.service.ServiceCatalogService;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class WorkOrderAndReservationIntegrationTest {

    @Autowired
    private ServiceCatalogService catalogService;

    @Autowired
    private ServiceReservationService reservationService;

    @Autowired
    private ReservationWorkOrderCoordinator coordinator;

    @Autowired
    private WorkOrderService workOrderService;

    @Autowired
    private ServiceReservationMapper reservationMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    private CurrentUser childUser;
    private CurrentUser adminUser;
    private CurrentUser workerUser;

    @BeforeEach
    void setUp() {
        childUser = new CurrentUser("4", "13912345678", "FAMILY", List.of("CHILD"), "CHILD", "1");
        adminUser = new CurrentUser("1", "13800138000", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        workerUser = new CurrentUser("2", "13800138002", "STAFF", List.of("GRID_WORKER"), "GRID_WORKER", "1");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("1. 服务目录查询：应返回预置的助浴、保洁等助老服务")
    void testListActiveCatalogs() {
        List<ServiceCatalogVO> catalogs = catalogService.listActiveCatalogs();
        assertNotNull(catalogs);
        assertFalse(catalogs.isEmpty());
        assertTrue(catalogs.stream().anyMatch(c -> "DAILY_CARE".equals(c.getServiceType())));
        assertTrue(catalogs.stream().anyMatch(c -> "CLEANING".equals(c.getServiceType())));
    }

    @Test
    @DisplayName("2. 预约时段防重：同一老人同服务同时段不可重复预约")
    void testDuplicateReservationConflict() {
        UserContext.set(childUser);

        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setElderId(1L);
        dto.setServiceType("CLEANING");
        dto.setServiceDate(LocalDate.now().plusDays(3));
        dto.setSlotCode("MORNING");
        dto.setServiceAddress("后宰门社区5栋201室");
        dto.setSpecialNotes("老人腰腿不便，请重点清洁地面防跌倒");

        Long rsvId1 = reservationService.createReservation(dto, 4L);
        assertTrue(rsvId1 > 0);

        // 尝试重复提交相同老人、同日期同时段
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            reservationService.createReservation(dto, 4L);
        });
        assertEquals(ErrorCode.RESERVATION_SLOT_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("3. 助老预约到工单全生命周期：预约->审核受理建单->派单->接单->免照结案->自审互斥拦截->审核结案闭环")
    void testFullReservationAndWorkOrderLifecycle() {
        // [阶段 1] 子女代办保洁预约
        UserContext.set(childUser);
        ReservationCreateDTO createDto = new ReservationCreateDTO();
        createDto.setElderId(1L);
        createDto.setServiceType("DAILY_CARE");
        createDto.setServiceDate(LocalDate.now().plusDays(4));
        createDto.setSlotCode("AFTERNOON");
        createDto.setServiceAddress("后宰门街道北安门社区5栋201室");
        createDto.setSpecialNotes("老人需要助浴照料，请安排专业人员");

        Long rsvId = reservationService.createReservation(createDto, 4L);
        assertTrue(rsvId > 0);

        ServiceReservation reservation = reservationMapper.selectById(rsvId);
        assertEquals("PENDING_CONFIRMATION", reservation.getStatus());

        // [阶段 2] 社区管理员审核通过预约，触发协调事务，原子生成待派发工单
        UserContext.set(adminUser);
        Long workOrderId = coordinator.confirmReservationAndCreateWorkOrder(rsvId, 1L);
        assertTrue(workOrderId > 0);

        ServiceReservation rsvAfterConfirm = reservationMapper.selectById(rsvId);
        assertEquals("CONFIRMED", rsvAfterConfirm.getStatus());

        WorkOrder order = workOrderMapper.selectById(workOrderId);
        assertNotNull(order);
        assertEquals("RESERVATION", order.getSourceType());
        assertEquals(rsvId.toString(), order.getSourceReferenceId());
        assertEquals("PENDING_DISPATCH", order.getStatus());

        // [阶段 3] 管理员将工单指派给责任网格员 (ID: 2)
        WorkOrderDispatchDTO dispatchDto = new WorkOrderDispatchDTO();
        dispatchDto.setAssigneeUserId(2L);
        dispatchDto.setReason("指派常驻网格员上门助浴");
        workOrderService.dispatchOrder(workOrderId, dispatchDto, 1L);

        WorkOrder orderDispatched = workOrderMapper.selectById(workOrderId);
        assertEquals("DISPATCHED", orderDispatched.getStatus());
        assertEquals(2L, orderDispatched.getCurrentAssigneeId());

        // [阶段 4] 网格员接单并开始服务
        UserContext.set(workerUser);
        workOrderService.acceptOrder(workOrderId, 2L);
        assertEquals("ACCEPTED", workOrderMapper.selectById(workOrderId).getStatus());

        workOrderService.startService(workOrderId, 2L);
        assertEquals("PROCESSING", workOrderMapper.selectById(workOrderId).getStatus());

        // [阶段 5] 网格员提交结案结果：申请“特殊情况免照豁免”
        WorkOrderExecutionDTO execDto = new WorkOrderExecutionDTO();
        execDto.setIsExemptPhoto(true);
        execDto.setPhotoExemptionReason("老人行动不便且保护浴室隐私，当面征得家属同意后进行免照结案登记");
        execDto.setResultSummary("助浴完成，老人身体体征平稳，浴室地面已防滑擦干。");

        workOrderService.submitExecutionResult(workOrderId, execDto, 2L);

        WorkOrder orderPendingReview = workOrderMapper.selectById(workOrderId);
        assertEquals("PENDING_RESULT_REVIEW", orderPendingReview.getStatus());
        assertFalse(orderPendingReview.getResultPhotoRequired());
        assertNotNull(orderPendingReview.getPhotoExemptionReason());

        // [阶段 6] 网格员无审核角色；双角色责任人切到管理员角色后仍不可自审
        WorkOrderReviewDTO reviewDto = new WorkOrderReviewDTO();
        reviewDto.setAction("APPROVE");
        reviewDto.setReviewComment("同意结案");

        BusinessException workerReviewEx = assertThrows(BusinessException.class, () ->
                workOrderService.reviewWorkOrder(workOrderId, reviewDto, 2L));
        assertEquals(ErrorCode.ACCESS_DENIED, workerReviewEx.getErrorCode());

        UserContext.set(new CurrentUser("2", "13800138002", "STAFF",
                List.of("GRID_WORKER", "COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1"));
        BusinessException selfReviewEx = assertThrows(BusinessException.class, () ->
                workOrderService.reviewWorkOrder(workOrderId, reviewDto, 2L));
        assertEquals(ErrorCode.FORBIDDEN_SELF_REVIEW, selfReviewEx.getErrorCode());

        // [阶段 7] 切换为其他管理员（ID: 1）审核通过，结案归档
        UserContext.set(adminUser);
        workOrderService.reviewWorkOrder(workOrderId, reviewDto, 1L);

        WorkOrder orderClosed = workOrderMapper.selectById(workOrderId);
        assertEquals("CLOSED", orderClosed.getStatus());
        assertEquals(1L, orderClosed.getReviewerUserId());

        // 联动校验：预约状态也应自动闭环置为 COMPLETED
        ServiceReservation rsvCompleted = reservationMapper.selectById(rsvId);
        assertEquals("COMPLETED", rsvCompleted.getStatus());

        // 详情 VO 完整性校验
        ReservationVO detailVO = reservationService.getReservationDetail(rsvId);
        assertEquals("已完成履约", detailVO.getStatusName());
        assertEquals(workOrderId, detailVO.getAssociatedWorkOrderId());
        assertEquals("CLOSED", detailVO.getAssociatedWorkOrderStatus());
    }

    @Test
    @DisplayName("测试预约取消后同一老人可在相同日期时段重新预约 (Flyway V3.2验证)")
    void testReservationCanBeRebookedAfterCancellation() {
        UserContext.set(childUser);

        LocalDate targetDate = LocalDate.now().plusDays(3);

        // 1. 提交预约
        ReservationCreateDTO dto1 = new ReservationCreateDTO();
        dto1.setElderId(1L);
        dto1.setServiceType("DAILY_CARE");
        dto1.setServiceDate(targetDate);
        dto1.setSlotCode("AFTERNOON");
        dto1.setServiceAddress("后宰门街道1号院");
        dto1.setSpecialNotes("老人初次助浴");

        Long rsv1 = reservationService.createReservation(dto1, 4L);
        assertNotNull(rsv1);

        // 2. 取消预约
        reservationService.cancelReservation(rsv1, "家属临时有事调整时间", 4L);
        ServiceReservation rsv1Obj = reservationMapper.selectById(rsv1);
        assertEquals("CANCELLED", rsv1Obj.getStatus());

        // 3. 再次在相同日期时段预约相同服务类型，应正常成功
        ReservationCreateDTO dto2 = new ReservationCreateDTO();
        dto2.setElderId(1L);
        dto2.setServiceType("DAILY_CARE");
        dto2.setServiceDate(targetDate);
        dto2.setSlotCode("AFTERNOON");
        dto2.setServiceAddress("后宰门街道1号院");
        dto2.setSpecialNotes("老人再次申请助浴");

        Long rsv2 = reservationService.createReservation(dto2, 4L);
        assertNotNull(rsv2);
        assertNotEquals(rsv1, rsv2);

        ServiceReservation rsv2Obj = reservationMapper.selectById(rsv2);
        assertEquals("PENDING_CONFIRMATION", rsv2Obj.getStatus());
    }
}
