package cn.communityelderly.modules.elder;

import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.UnauthorizedException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.elder.dto.BoundElderVO;
import cn.communityelderly.modules.elder.dto.KinshipReviewDTO;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.entity.FamilyLinkApplication;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.mapper.FamilyLinkApplicationMapper;
import cn.communityelderly.modules.elder.service.impl.KinshipServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

class KinshipServiceAuthorizationTest {

    private ElderFamilyLinkMapper familyLinkMapper;
    private FamilyLinkApplicationMapper applicationMapper;
    private ElderProfileMapper elderProfileMapper;
    private AuditLogService auditLogService;
    private KinshipServiceImpl service;

    @BeforeEach
    void setUp() {
        familyLinkMapper = mock(ElderFamilyLinkMapper.class);
        applicationMapper = mock(FamilyLinkApplicationMapper.class);
        elderProfileMapper = mock(ElderProfileMapper.class);
        auditLogService = mock(AuditLogService.class);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(
                elderProfileMapper, mock(cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper.class), familyLinkMapper);
        service = new KinshipServiceImpl(familyLinkMapper, applicationMapper, elderProfileMapper, auditLogService, policy);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("匿名解绑必须在读取或修改关系前拒绝")
    void anonymousUnlinkIsRejectedBeforeLoadingLink() {
        assertThrows(UnauthorizedException.class, () -> service.unlinkKinship(7L, "test"));
        verifyNoInteractions(familyLinkMapper, applicationMapper, elderProfileMapper, auditLogService);
    }

    @Test
    @DisplayName("子女只能查询自己的绑定列表")
    void childCannotListAnotherUsersLinks() {
        UserContext.set(child(4L));

        assertThrows(BusinessException.class, () -> service.listBoundElders(5L));
        verifyNoInteractions(familyLinkMapper, applicationMapper, elderProfileMapper);
    }

    @Test
    @DisplayName("外社区管理员不能审批绑定申请")
    void communityAdminCannotReviewApplicationFromAnotherCommunity() {
        UserContext.set(admin(1L, 10L));
        FamilyLinkApplication app = new FamilyLinkApplication();
        app.setApplicationId(20L);
        app.setElderId(30L);
        app.setChildUserId(4L);
        app.setStatus("PENDING");
        when(applicationMapper.selectById(20L)).thenReturn(app);
        ElderProfile elder = new ElderProfile();
        elder.setElderId(30L);
        elder.setCommunityId(11L);
        when(elderProfileMapper.selectById(30L)).thenReturn(elder);
        KinshipReviewDTO decision = new KinshipReviewDTO();
        decision.setApproved(true);

        assertThrows(BusinessException.class, () -> service.reviewApplication(20L, decision));
        verify(applicationMapper, never()).updateById(any(FamilyLinkApplication.class));
        verify(familyLinkMapper, never()).updateById(any(ElderFamilyLink.class));
    }

    @Test
    @DisplayName("绑定申请列表必须按当前管理员社区的老人档案限定查询")
    void applicationListQueryIsScopedToCurrentCommunity() {
        UserContext.set(admin(1L, 10L));
        ElderProfile localElder = new ElderProfile();
        localElder.setElderId(30L);
        localElder.setCommunityId(10L);
        when(elderProfileMapper.selectList(any(Wrapper.class))).thenReturn(List.of(localElder));
        when(applicationMapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            assertTrue(query.getSqlSegment().contains("elder_id"));
            return List.of();
        });

        assertTrue(service.listApplications(null).isEmpty());
        verify(applicationMapper).selectList(any(Wrapper.class));
    }

    @Test
    @DisplayName("非 ACTIVE 关系列表不得返回老人个人资料")
    void nonActiveListEntriesDoNotExposeElderProfileDetails() {
        UserContext.set(child(4L));
        ElderFamilyLink link = new ElderFamilyLink();
        link.setLinkId(7L);
        link.setElderId(30L);
        link.setChildUserId(4L);
        link.setStatus("FROZEN");
        link.setFreezeReasonText("关系存在争议");
        link.setReviewDeadline(LocalDateTime.now().plusDays(2));
        when(familyLinkMapper.selectList(any(Wrapper.class))).thenReturn(List.of(link));
        ElderProfile elder = new ElderProfile();
        elder.setElderId(30L);
        elder.setName("合成老人");
        elder.setArchiveNo("A-30");
        elder.setAddress("合成地址");
        elder.setContactPhone("13800000030");
        when(elderProfileMapper.selectById(30L)).thenReturn(elder);

        List<BoundElderVO> result = service.listBoundElders(4L);

        assertEquals(1, result.size());
        BoundElderVO frozen = result.get(0);
        assertEquals("FROZEN", frozen.getStatus());
        assertNull(frozen.getName());
        assertNull(frozen.getArchiveNo());
        assertNull(frozen.getAddress());
        assertNull(frozen.getContactPhone());
        assertNotNull(frozen.getFreezeReason());
    }

    private CurrentUser child(long userId) {
        return new CurrentUser(String.valueOf(userId), "13800000004", "FAMILY", List.of("CHILD"), "CHILD", "10");
    }

    private CurrentUser admin(long userId, long communityId) {
        return new CurrentUser(String.valueOf(userId), "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN", String.valueOf(communityId));
    }
}
