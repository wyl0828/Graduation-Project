package cn.communityelderly.modules.emergency;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.elder.dto.ElderArchiveVO;
import cn.communityelderly.modules.elder.dto.ElderCreateDTO;
import cn.communityelderly.modules.elder.entity.ElderIdentityLink;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.emergency.dto.*;
import cn.communityelderly.modules.emergency.entity.SosEvent;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.entity.WorkOrderActionHistory;
import cn.communityelderly.modules.emergency.mapper.SosEventMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderActionHistoryMapper;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.emergency.service.SosEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class SosFullLifecycleIntegrationTest {

    @Autowired
    private SosEventService sosEventService;

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private SosEventMapper sosEventMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private WorkOrderActionHistoryMapper actionHistoryMapper;

    @Autowired
    private ElderIdentityLinkMapper identityLinkMapper;

    private CurrentUser adminUser;
    private CurrentUser elderUser;
    private Long elderId;

    @BeforeEach
    void setUp() {
        adminUser = new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        elderUser = new CurrentUser("3", "13800000003", "ELDER", List.of("ELDER"), "ELDER", "1");

        UserContext.set(adminUser);
        ElderCreateDTO createDTO = new ElderCreateDTO();
        createDTO.setName("陈大爷");
        createDTO.setSex("MALE");
        createDTO.setBirthDate(LocalDate.of(1942, 3, 15));
        createDTO.setAddress("南京市玄武区锁金村15幢204");
        createDTO.setContactPhone("13912345678");
        createDTO.setEmergencyContactName("陈建强");
        createDTO.setEmergencyContactPhone("13800000004");
        createDTO.setEmergencyContactRelation("儿子");

        ElderArchiveVO elder = elderArchiveService.createElder(createDTO);
        elderId = elder.getElderId();

        ElderIdentityLink identity = identityLinkMapper.selectOne(
                new LambdaQueryWrapper<ElderIdentityLink>().eq(ElderIdentityLink::getUserId, 3L));
        if (identity == null) {
            identity = new ElderIdentityLink();
            identity.setUserId(3L);
            identity.setElderId(elderId);
            identity.setLinkedAt(LocalDateTime.now());
            identityLinkMapper.insert(identity);
        } else {
            identity.setElderId(elderId);
            identityLinkMapper.updateById(identity);
        }
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("AT-19: 长按不足2000ms被安全拦截")
    void testPressDurationInsufficient() {
        UserContext.set(elderUser);

        SosTriggerDTO triggerDTO = new SosTriggerDTO();
        triggerDTO.setElderId(elderId);
        triggerDTO.setPressDurationMs(1200); // 不足 2000ms
        triggerDTO.setLatitude(new BigDecimal("32.061234"));
        triggerDTO.setLongitude(new BigDecimal("118.791234"));
        triggerDTO.setLocationAddress("南京市玄武区锁金村15幢204");
        triggerDTO.setIdempotencyKey(UUID.randomUUID().toString());

        BusinessException ex = assertThrows(BusinessException.class, () -> sosEventService.triggerSos(triggerDTO));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("长按触控时长不足2000毫秒"));
    }

    @Test
    @DisplayName("AT-20: 长按>=2000ms原子派生工单、误触二次确认带痕取消转回访、结案全链路")
    void testSosTriggerCancelAndCloseWorkflow() {
        // 1. 老人长按 2200ms 触发 SOS
        UserContext.set(elderUser);
        String reqId = UUID.randomUUID().toString();

        SosTriggerDTO triggerDTO = new SosTriggerDTO();
        triggerDTO.setElderId(elderId);
        triggerDTO.setPressDurationMs(2200); // 满足 >= 2000ms
        triggerDTO.setLatitude(new BigDecimal("32.061234"));
        triggerDTO.setLongitude(new BigDecimal("118.791234"));
        triggerDTO.setLocationAddress("南京市玄武区锁金村15幢204");
        triggerDTO.setIdempotencyKey(reqId);

        SosEventVO sosEvent = sosEventService.triggerSos(triggerDTO);
        assertNotNull(sosEvent);
        assertNotNull(sosEvent.getSosId());
        assertEquals("ACCEPTED", sosEvent.getStatus());
        assertEquals(Boolean.FALSE, readInactiveAtAcceptance(sosEvent),
                "An SOS accepted for an active elder must retain an explicit active-at-acceptance snapshot");
        assertNotNull(sosEvent.getWorkOrderId());
        assertEquals(Boolean.FALSE, readInactiveAtAcceptance(sosEventMapper.selectById(sosEvent.getSosId())));

        // 验证原子生成的工单
        WorkOrder workOrder = workOrderMapper.selectById(sosEvent.getWorkOrderId());
        assertNotNull(workOrder);
        assertTrue(workOrder.getOrderNo().startsWith("WO"));
        assertEquals("ACCEPTED", workOrder.getStatus());
        assertEquals("URGENT", workOrder.getUrgencyLevel());

        // 2. 幂等校验：重传相同 idempotencyKey 返回同一事件
        SosEventVO duplicate = sosEventService.triggerSos(triggerDTO);
        assertEquals(sosEvent.getSosId(), duplicate.getSosId());

        // 3. 老人误触，二次确认取消
        SosCancelDTO cancelDTO = new SosCancelDTO();
        cancelDTO.setCancelReason("不小心碰到了手机屏幕，我身体很好没事");
        sosEventService.cancelSosByElder(sosEvent.getSosId(), cancelDTO);

        SosEvent cancelledEvent = sosEventMapper.selectById(sosEvent.getSosId());
        assertEquals("CANCELLED", cancelledEvent.getStatus());

        // 验证工单流水中记录了“老人主动取消·待电话回访”痕迹
        List<WorkOrderActionHistory> actions = actionHistoryMapper.selectList(
                new LambdaQueryWrapper<WorkOrderActionHistory>()
                        .eq(WorkOrderActionHistory::getWorkOrderId, workOrder.getWorkOrderId())
        );
        assertTrue(actions.stream().anyMatch(a -> "ELDER_CANCEL".equals(a.getActionName()) || a.getComment().contains("误触取消")));

        // 4. 管理员调度改派
        UserContext.set(adminUser);
        SosDispatchDTO dispatchDTO = new SosDispatchDTO();
        dispatchDTO.setAssigneeId(2L);
        dispatchDTO.setDispatchReason("请立即电话回访陈大爷确认安全");
        sosEventService.dispatchSos(sosEvent.getSosId(), dispatchDTO);

        // 5. 调度台结案
        SosCloseDTO closeDTO = new SosCloseDTO();
        closeDTO.setResultSummary("已致电陈大爷本人及家属，确认老人精神状态良好，误触手机，警报解除");
        sosEventService.closeSos(sosEvent.getSosId(), closeDTO);

        WorkOrder closedOrder = workOrderMapper.selectById(sosEvent.getWorkOrderId());
        assertEquals("CLOSED", closedOrder.getStatus());
        assertNotNull(closedOrder.getServiceFinishTime());
    }

    @Test
    @DisplayName("AT-19: 停用档案仍可由有效老人账号触发 SOS 并向管理员保留停档快照")
    void inactiveElderSosPersistsInactiveAtAcceptanceMarkerForAdminReview() {
        UserContext.set(adminUser);
        elderArchiveService.updateElderStatus(elderId, "INACTIVE", "AT-19 停用档案 SOS 快照回归");

        UserContext.set(elderUser);
        SosTriggerDTO triggerDTO = new SosTriggerDTO();
        triggerDTO.setElderId(elderId);
        triggerDTO.setPressDurationMs(2200);
        triggerDTO.setLocationAddress("南京市玄武区锁金村15幢204");
        triggerDTO.setIdempotencyKey(UUID.randomUUID().toString());

        SosEventVO accepted = sosEventService.triggerSos(triggerDTO);
        assertNotNull(accepted.getSosId());
        assertEquals("ACCEPTED", accepted.getStatus());
        assertEquals(Boolean.TRUE, readInactiveAtAcceptance(accepted));

        SosEvent persisted = sosEventMapper.selectById(accepted.getSosId());
        assertEquals(Boolean.TRUE, readInactiveAtAcceptance(persisted),
                "The SOS event must persist the archive state observed while accepting the request");

        UserContext.set(adminUser);
        SosEventVO adminReview = sosEventService.listSosEvents("ACCEPTED").stream()
                .filter(item -> accepted.getSosId().equals(item.getSosId()))
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.TRUE, readInactiveAtAcceptance(adminReview),
                "The community administrator SOS review list must expose the immutable acceptance snapshot");
    }

    private Boolean readInactiveAtAcceptance(Object eventOrView) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(eventOrView);
        assertTrue(wrapper.isReadableProperty("elderInactiveAtAcceptance"),
                "SOS event and review view must expose the archived-at-acceptance snapshot");
        return (Boolean) wrapper.getPropertyValue("elderInactiveAtAcceptance");
    }
}
