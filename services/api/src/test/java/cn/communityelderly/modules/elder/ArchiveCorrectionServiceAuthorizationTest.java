package cn.communityelderly.modules.elder;

import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.UnauthorizedException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.dto.CorrectionApplyDTO;
import cn.communityelderly.modules.elder.dto.CorrectionReviewDTO;
import cn.communityelderly.modules.elder.entity.ElderCorrectionRequest;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderCorrectionItemMapper;
import cn.communityelderly.modules.elder.mapper.ElderCorrectionRequestMapper;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderIdentityLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.impl.ArchiveCorrectionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.conditions.Wrapper;

class ArchiveCorrectionServiceAuthorizationTest {

    private ElderProfileMapper elderProfileMapper;
    private ElderFamilyLinkMapper familyLinkMapper;
    private ElderCorrectionRequestMapper requestMapper;
    private ElderCorrectionItemMapper itemMapper;
    private UserAccountMapper userAccountMapper;
    private AuditLogService auditLogService;
    private ArchiveCorrectionServiceImpl service;

    @BeforeEach
    void setUp() {
        elderProfileMapper = mock(ElderProfileMapper.class);
        familyLinkMapper = mock(ElderFamilyLinkMapper.class);
        requestMapper = mock(ElderCorrectionRequestMapper.class);
        itemMapper = mock(ElderCorrectionItemMapper.class);
        userAccountMapper = mock(UserAccountMapper.class);
        auditLogService = mock(AuditLogService.class);
        ObjectAuthorizationPolicy policy = new ObjectAuthorizationPolicy(
                elderProfileMapper, mock(ElderIdentityLinkMapper.class), familyLinkMapper);
        service = new ArchiveCorrectionServiceImpl(elderProfileMapper, familyLinkMapper, requestMapper, itemMapper,
                userAccountMapper, auditLogService, policy);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("匿名档案更正申请不得回退到固定子女账号")
    void anonymousSubmissionIsRejectedBeforeDatabaseAccess() {
        CorrectionApplyDTO request = new CorrectionApplyDTO();
        request.setElderId(30L);

        assertThrows(UnauthorizedException.class, () -> service.submitCorrection(request));
        verifyNoInteractions(familyLinkMapper, requestMapper, itemMapper, elderProfileMapper);
    }

    @Test
    @DisplayName("外社区管理员不能审批其他社区的更正申请")
    void communityAdminCannotReviewOtherCommunityCorrection() {
        UserContext.set(admin(1L, 10L));
        ElderCorrectionRequest request = pendingRequest(8L, 30L, 4L, 7L, 2L);
        when(requestMapper.selectById(8L)).thenReturn(request);
        when(elderProfileMapper.selectById(30L)).thenReturn(elder(30L, 11L));
        CorrectionReviewDTO decision = approve();

        assertThrows(BusinessException.class, () -> service.reviewCorrection(8L, decision));
        verify(requestMapper, never()).updateById(any(ElderCorrectionRequest.class));
        verify(elderProfileMapper, never()).updateById(any(ElderProfile.class));
        verifyNoInteractions(itemMapper, auditLogService);
    }

    @Test
    @DisplayName("申请人关系冻结后管理员不能凭旧更正申请批准写入")
    void frozenApplicantRelationshipBlocksCorrectionApproval() {
        UserContext.set(admin(1L, 10L));
        ElderCorrectionRequest request = pendingRequest(8L, 30L, 4L, 7L, 2L);
        when(requestMapper.selectById(8L)).thenReturn(request);
        when(elderProfileMapper.selectById(30L)).thenReturn(elder(30L, 10L));
        UserAccount applicant = new UserAccount();
        applicant.setUserId(4L);
        applicant.setStatus("ACTIVE");
        when(userAccountMapper.selectById(4L)).thenReturn(applicant);
        ElderFamilyLink link = new ElderFamilyLink();
        link.setLinkId(7L);
        link.setElderId(30L);
        link.setChildUserId(4L);
        link.setLinkGeneration(2L);
        link.setStatus("FROZEN");
        when(familyLinkMapper.selectById(7L)).thenReturn(link);

        assertThrows(BusinessException.class, () -> service.reviewCorrection(8L, approve()));
        verify(requestMapper).updateById(request);
        org.junit.jupiter.api.Assertions.assertEquals("APPLICANT_AUTHORIZATION_REVOKED", request.getBlockCode());
        verify(elderProfileMapper, never()).updateById(any(ElderProfile.class));
        verify(itemMapper, never()).selectList(any(Wrapper.class));
        verify(auditLogService).recordAudit(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private ElderCorrectionRequest pendingRequest(long requestId, long elderId, long applicantId,
                                                    long linkId, long generation) {
        ElderCorrectionRequest request = new ElderCorrectionRequest();
        request.setRequestId(requestId);
        request.setElderId(elderId);
        request.setApplicantUserId(applicantId);
        request.setApplicantLinkId(linkId);
        request.setApplicantLinkGeneration(generation);
        request.setStatus("PENDING_REVIEW");
        return request;
    }

    private ElderProfile elder(long elderId, long communityId) {
        ElderProfile elder = new ElderProfile();
        elder.setElderId(elderId);
        elder.setCommunityId(communityId);
        elder.setRowVersion(1L);
        elder.setStatus("ACTIVE");
        return elder;
    }

    private CorrectionReviewDTO approve() {
        CorrectionReviewDTO review = new CorrectionReviewDTO();
        review.setApproved(true);
        return review;
    }

    private CurrentUser admin(long userId, long communityId) {
        return new CurrentUser(String.valueOf(userId), "13800000001", "STAFF", List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN", String.valueOf(communityId));
    }
}
