package cn.communityelderly.modules.elder;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.ConflictException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.service.impl.PhoneVerificationChallengeService;
import cn.communityelderly.modules.elder.dto.*;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.ArchiveCorrectionService;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.elder.service.KinshipService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class ElderAndKinshipIntegrationTest {

    @Autowired
    private ElderProfileMapper elderProfileMapper;

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private KinshipService kinshipService;

    @Autowired
    private ArchiveCorrectionService correctionService;

    @Autowired
    private PhoneVerificationChallengeService phoneVerificationChallengeService;

    private CurrentUser adminUser;
    private CurrentUser childUser;

    @BeforeEach
    void setUp() {
        adminUser = new CurrentUser("1", "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"), "COMMUNITY_ADMIN", "1");
        childUser = new CurrentUser("4", "13800000004", "FAMILY", List.of("CHILD"), "CHILD", "1");
        UserContext.set(adminUser);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("AT-14: 无可信短信服务时手机号挑战与首次激活失败关闭")
    void activationPhoneChallengeFailsClosedWithoutSmsProvider() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> phoneVerificationChallengeService.issue(
                        "13911223344", "ACTIVATION", "synthetic-ticket", null));
        assertEquals(ErrorCode.PHONE_VERIFICATION_UNAVAILABLE, error.getErrorCode());
    }

    @Test
    @DisplayName("AT-02 & FZ-02: 亲情绑定申请、审批、争议冻结与解冻流转")
    void testKinshipApplyReviewAndDisputeFreezeFlow() {
        UserContext.set(childUser);

        // 1. 子女发起绑定申请 (申请绑定李秀英 elder_id=2)
        KinshipApplyDTO applyDTO = new KinshipApplyDTO();
        applyDTO.setElderId(2L);
        applyDTO.setRelationship("女儿");
        applyDTO.setApplyReason("长辈独居需要照看，申请亲情关联");

        Long appId = kinshipService.applyKinship(applyDTO);
        assertNotNull(appId);

        // 2. 管理员审批通过
        UserContext.set(adminUser);
        KinshipReviewDTO reviewDTO = new KinshipReviewDTO();
        reviewDTO.setApproved(true);
        reviewDTO.setDecisionReason("户口本与亲情证明核对无误");
        kinshipService.reviewApplication(appId, reviewDTO);

        // 3. 验证子女端已绑定
        UserContext.set(childUser);
        List<BoundElderVO> boundList = kinshipService.listBoundElders(4L);
        assertTrue(boundList.stream().anyMatch(e -> e.getElderId().equals(2L) && "ACTIVE".equals(e.getStatus())));
        BoundElderVO boundElder = boundList.stream().filter(e -> e.getElderId().equals(2L)).findFirst().orElseThrow();

        // 4. 触发 FZ-02 争议冻结
        KinshipFreezeDTO freezeDTO = new KinshipFreezeDTO();
        freezeDTO.setFreezeReasonCode("FAMILY_DISPUTE");
        freezeDTO.setFreezeReasonText("亲属间存在监护抚养权争议，社区介入核实期间暂冻结");
        UserContext.set(adminUser);
        kinshipService.freezeKinship(boundElder.getLinkId(), freezeDTO);

        // 5. 验证冻结后状态与5工作日倒计时生效
        UserContext.set(childUser);
        List<BoundElderVO> frozenList = kinshipService.listBoundElders(4L);
        BoundElderVO frozenElder = frozenList.stream().filter(e -> e.getElderId().equals(2L)).findFirst().orElseThrow();
        assertEquals("FROZEN", frozenElder.getStatus());
        assertNotNull(frozenElder.getReviewDeadline());
        assertEquals(freezeDTO.getFreezeReasonText(), frozenElder.getFreezeReason());

        // 6. 社区调解后解冻恢复
        UserContext.set(adminUser);
        kinshipService.unfreezeKinship(boundElder.getLinkId(), "社区居委会调解成功，家属达成一致");
        UserContext.set(childUser);
        List<BoundElderVO> recoveredList = kinshipService.listBoundElders(4L);
        BoundElderVO recoveredElder = recoveredList.stream().filter(e -> e.getElderId().equals(2L)).findFirst().orElseThrow();
        assertEquals("ACTIVE", recoveredElder.getStatus());
    }

    @Test
    @DisplayName("FZ-01 & AT-18: 档案更正并发冲突控制 (Baseline快照比对与409拦截)")
    void testArchiveCorrectionSnapshotConflict() {
        // 子女 4 已与 张建国 (elder_id=1) 处于 ACTIVE 绑定
        UserContext.set(childUser);

        // 1. 子女提交更正申请
        CorrectionApplyDTO applyDTO = new CorrectionApplyDTO();
        applyDTO.setElderId(1L);
        applyDTO.setReason("老人已搬至新居，申请更新现居地址");
        CorrectionApplyDTO.Item item = new CorrectionApplyDTO.Item();
        item.setFieldCode("address");
        item.setProposedValue("南京市玄武区北京东路88号301室");
        applyDTO.setItems(List.of(item));

        Long reqId = correctionService.submitCorrection(applyDTO);
        assertNotNull(reqId);

        // 2. 模拟并发冲突：在管理员审批之前，老人的现居地址被他人修改！
        ElderProfile elder = elderArchiveService.getElderEntity(1L);
        elder.setAddress("南京市玄武区锁金村临时修改地址99号");
        UserContext.set(adminUser);
        ElderProfileUpdateDTO directUpdate = new ElderProfileUpdateDTO();
        directUpdate.setAddress("南京市玄武区锁金村临时修改地址99号");
        elder.setAddress(directUpdate.getAddress());
        elderProfileMapper.updateById(elder);

        // 3. 管理员审批批准 -> 此时触发快照比对失败，必须抛出 409 ConflictException!
        CorrectionReviewDTO reviewDTO = new CorrectionReviewDTO();
        reviewDTO.setApproved(true);
        reviewDTO.setDecisionReason("同意修改");

        ConflictException conflictEx = assertThrows(ConflictException.class, () -> correctionService.reviewCorrection(reqId, reviewDTO));
        assertEquals(ErrorCode.FIELD_VALUE_CONFLICT, conflictEx.getErrorCode());

        // 4. 检查更正详情状态，确认 blockCode 已被记录为 FIELD_VALUE_CONFLICT 且前端可获得冲突对比
        CorrectionDetailVO detail = correctionService.getCorrectionDetail(reqId);
        assertNotNull(detail);
        assertEquals("FIELD_VALUE_CONFLICT", detail.getBlockCode());
        assertTrue(detail.getItems().get(0).getHasConflict());
    }
}
