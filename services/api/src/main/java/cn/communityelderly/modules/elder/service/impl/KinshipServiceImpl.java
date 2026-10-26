package cn.communityelderly.modules.elder.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.elder.dto.*;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.entity.FamilyLinkApplication;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.mapper.FamilyLinkApplicationMapper;
import cn.communityelderly.modules.elder.service.KinshipService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class KinshipServiceImpl implements KinshipService {

    private final ElderFamilyLinkMapper familyLinkMapper;
    private final FamilyLinkApplicationMapper applicationMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final AuditLogService auditLogService;
    private final ObjectAuthorizationPolicy authorizationPolicy;

    public KinshipServiceImpl(ElderFamilyLinkMapper familyLinkMapper,
                              FamilyLinkApplicationMapper applicationMapper,
                              ElderProfileMapper elderProfileMapper,
                              AuditLogService auditLogService,
                              ObjectAuthorizationPolicy authorizationPolicy) {
        this.familyLinkMapper = familyLinkMapper;
        this.applicationMapper = applicationMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.auditLogService = auditLogService;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyKinship(KinshipApplyDTO dto) {
        CurrentUser currentUser = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("CHILD");
        Long childUserId = parseUserId(currentUser);

        ElderProfile elder = elderProfileMapper.selectById(dto.getElderId());
        if (elder == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "老人档案不存在 (elderId=" + dto.getElderId() + ")");
        }

        // 检查已有关系
        ElderFamilyLink existingLink = familyLinkMapper.selectOne(
                new LambdaQueryWrapper<ElderFamilyLink>()
                        .eq(ElderFamilyLink::getElderId, dto.getElderId())
                        .eq(ElderFamilyLink::getChildUserId, childUserId)
        );

        if (existingLink != null) {
            if ("FROZEN".equalsIgnoreCase(existingLink.getStatus())) {
                throw new BusinessException(ErrorCode.KINSHIP_UNDER_DISPUTE, "亲情绑定正处于民政争议冻结状态，冻结期内禁止重新提交绑定申请！");
            }
            if ("ACTIVE".equalsIgnoreCase(existingLink.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "您已与该长辈处于有效绑定状态，无需重复申请");
            }
            if ("PENDING".equalsIgnoreCase(existingLink.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "已有正在等待审核的绑定申请，请等待社区处理");
            }
        }

        LocalDateTime now = LocalDateTime.now();

        // 创建申请流水记录
        FamilyLinkApplication app = new FamilyLinkApplication();
        app.setElderId(dto.getElderId());
        app.setChildUserId(childUserId);
        app.setSubmittedByUserId(childUserId);
        app.setSubmissionMode("SELF");
        app.setRelationship(dto.getRelationship().trim());
        app.setApplyReason(dto.getApplyReason().trim());
        app.setStatus("PENDING");
        app.setEvidenceMetadata(dto.getEvidenceMetadata());
        app.setLinkGeneration(existingLink != null ? existingLink.getLinkGeneration() + 1 : 1L);
        app.setCreatedAt(now);
        applicationMapper.insert(app);

        // 更新或插入关系表
        if (existingLink == null) {
            existingLink = new ElderFamilyLink();
            existingLink.setElderId(dto.getElderId());
            existingLink.setChildUserId(childUserId);
            existingLink.setStatus("PENDING");
            existingLink.setCurrentApplicationId(app.getApplicationId());
            existingLink.setLinkGeneration(1L);
            existingLink.setRowVersion(1L);
            existingLink.setCreatedAt(now);
            existingLink.setUpdatedAt(now);
            familyLinkMapper.insert(existingLink);
        } else {
            existingLink.setStatus("PENDING");
            existingLink.setCurrentApplicationId(app.getApplicationId());
            existingLink.setLinkGeneration(existingLink.getLinkGeneration() + 1);
            existingLink.setFreezeReasonCode(null);
            existingLink.setFreezeReasonText(null);
            existingLink.setFrozenAt(null);
            existingLink.setReviewDeadline(null);
            existingLink.setUpdatedAt(now);
            familyLinkMapper.updateById(existingLink);
        }

        return app.getApplicationId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewApplication(Long applicationId, KinshipReviewDTO dto) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        FamilyLinkApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "绑定申请记录不存在");
        }
        authorizationPolicy.requireElderInCurrentCommunity(app.getElderId(), "COMMUNITY_ADMIN");

        if (!"PENDING".equalsIgnoreCase(app.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该申请已处理完毕，不可重复审批");
        }

        CurrentUser reviewer = authorizationPolicy.requireCurrentUser();
        Long reviewerId = parseUserId(reviewer);
        LocalDateTime now = LocalDateTime.now();

        ElderFamilyLink link = familyLinkMapper.selectOne(
                new LambdaQueryWrapper<ElderFamilyLink>()
                        .eq(ElderFamilyLink::getElderId, app.getElderId())
                        .eq(ElderFamilyLink::getChildUserId, app.getChildUserId())
        );
        if (link == null
                || !"PENDING".equalsIgnoreCase(link.getStatus())
                || !Objects.equals(link.getCurrentApplicationId(), applicationId)
                || !Objects.equals(link.getLinkGeneration(), app.getLinkGeneration())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "申请不再对应当前待审亲情关系，不能继续审批");
        }

        if (Boolean.TRUE.equals(dto.getApproved())) {
            app.setStatus("APPROVED");
            app.setReviewerUserId(reviewerId);
            app.setReviewedAt(now);
            app.setDecisionReason(StringUtils.hasText(dto.getDecisionReason()) ? dto.getDecisionReason() : "材料核验属实，审核通过");
            applicationMapper.updateById(app);

            if (link != null) {
                link.setStatus("ACTIVE");
                link.setUpdatedAt(now);
                familyLinkMapper.updateById(link);
            }
        } else {
            if (!StringUtils.hasText(dto.getDecisionReason())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "驳回申请必须明确填写拒绝原因");
            }
            app.setStatus("REJECTED");
            app.setReviewerUserId(reviewerId);
            app.setReviewedAt(now);
            app.setDecisionReason(dto.getDecisionReason().trim());
            applicationMapper.updateById(app);

            if (link != null) {
                link.setStatus("REJECTED");
                link.setUpdatedAt(now);
                familyLinkMapper.updateById(link);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeKinship(Long linkId, KinshipFreezeDTO dto) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        ElderFamilyLink link = familyLinkMapper.selectById(linkId);
        if (link == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "亲情关系记录不存在");
        }
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(link.getElderId(), "COMMUNITY_ADMIN");

        if (!"ACTIVE".equalsIgnoreCase(link.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅处于有效绑定(ACTIVE)状态的关系才可执行争议冻结");
        }

        LocalDateTime now = LocalDateTime.now();
        link.setStatus("FROZEN");
        link.setFreezeReasonCode(dto.getFreezeReasonCode());
        link.setFreezeReasonText(dto.getFreezeReasonText());
        link.setFrozenAt(now);
        link.setReviewDeadline(now.plusDays(7)); // 5 工作日倒计时
        link.setUpdatedAt(now);
        familyLinkMapper.updateById(link);

        Long operatorId = parseUserId(authorizationPolicy.requireCurrentUser());
        Long communityId = elder.getCommunityId();

        auditLogService.recordAudit(
                operatorId,
                "COMMUNITY_ADMIN",
                communityId,
                "FAMILY_LINK",
                String.valueOf(linkId),
                "FREEZE",
                "SUCCESS",
                "127.0.0.1",
                "{\"linkId\":" + linkId + ",\"freezeReasonCode\":\"" + dto.getFreezeReasonCode() + "\",\"freezeReasonText\":\"" + dto.getFreezeReasonText() + "\"}"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeKinship(Long linkId, String reason) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        ElderFamilyLink link = familyLinkMapper.selectById(linkId);
        if (link == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "亲情关系记录不存在");
        }
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(link.getElderId(), "COMMUNITY_ADMIN");

        if (!"FROZEN".equalsIgnoreCase(link.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅处于争议冻结(FROZEN)状态的关系才可执行解冻恢复");
        }

        LocalDateTime now = LocalDateTime.now();
        link.setStatus("ACTIVE");
        link.setFreezeReasonCode(null);
        link.setFreezeReasonText(null);
        link.setFrozenAt(null);
        link.setReviewDeadline(null);
        link.setUpdatedAt(now);
        familyLinkMapper.updateById(link);

        Long operatorId = parseUserId(authorizationPolicy.requireCurrentUser());
        Long communityId = elder.getCommunityId();

        auditLogService.recordAudit(
                operatorId,
                "COMMUNITY_ADMIN",
                communityId,
                "FAMILY_LINK",
                String.valueOf(linkId),
                "UNFREEZE",
                "SUCCESS",
                "127.0.0.1",
                "{\"linkId\":" + linkId + ",\"reason\":\"" + (reason != null ? reason : "") + "\"}"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlinkKinship(Long linkId, String reason) {
        CurrentUser currentUser = authorizationPolicy.requireCurrentUser();
        String currentRole = currentUser.getCurrentRole();
        if (!"CHILD".equalsIgnoreCase(currentRole) && !"COMMUNITY_ADMIN".equalsIgnoreCase(currentRole)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色无权解绑亲情关系");
        }
        ElderFamilyLink link = familyLinkMapper.selectById(linkId);
        if (link == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "亲情关系记录不存在");
        }

        if ("CHILD".equalsIgnoreCase(currentRole)) {
            Long userId = parseUserId(currentUser);
            if (!link.getChildUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ACCESS, "您不是该亲情绑定的当事人，无权解绑");
            }
        } else if ("COMMUNITY_ADMIN".equalsIgnoreCase(currentRole)) {
            authorizationPolicy.requireElderInCurrentCommunity(link.getElderId(), "COMMUNITY_ADMIN");
        } else {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色无权解绑亲情关系");
        }
        if (!"ACTIVE".equalsIgnoreCase(link.getStatus())) {
            throw new BusinessException(ErrorCode.KINSHIP_NOT_ACTIVE, "仅当前有效绑定关系可执行解绑");
        }

        link.setStatus("UNLINKED");
        link.setUpdatedAt(LocalDateTime.now());
        familyLinkMapper.updateById(link);
    }

    @Override
    public List<BoundElderVO> listBoundElders(Long childUserId) {
        authorizationPolicy.requireRole("CHILD");
        CurrentUser currentUser = authorizationPolicy.requireCurrentUser();
        Long authenticatedChildId = parseUserId(currentUser);
        if (childUserId == null || !authenticatedChildId.equals(childUserId)) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "只能查询当前子女账号自己的亲情关系");
        }

        List<ElderFamilyLink> links = familyLinkMapper.selectList(
                new LambdaQueryWrapper<ElderFamilyLink>()
                        .eq(ElderFamilyLink::getChildUserId, childUserId)
                        .in(ElderFamilyLink::getStatus, "ACTIVE", "PENDING", "FROZEN", "REJECTED")
                        .orderByDesc(ElderFamilyLink::getUpdatedAt)
        );

        List<BoundElderVO> result = new ArrayList<>();
        for (ElderFamilyLink link : links) {
            FamilyLinkApplication app = null;
            if (link.getCurrentApplicationId() != null) {
                app = applicationMapper.selectById(link.getCurrentApplicationId());
            }

            BoundElderVO vo = new BoundElderVO();
            vo.setLinkId(link.getLinkId());
            vo.setElderId(link.getElderId());
            vo.setRelationship(app != null ? app.getRelationship() : "家属");
            vo.setStatus(link.getStatus());
            if ("ACTIVE".equalsIgnoreCase(link.getStatus())) {
                ElderProfile elder = elderProfileMapper.selectById(link.getElderId());
                if (elder == null) continue;
                vo.setName(elder.getName());
                vo.setArchiveNo(elder.getArchiveNo());
                vo.setSex(elder.getSex());
                if (elder.getBirthDate() != null) {
                    vo.setAge(Period.between(elder.getBirthDate(), LocalDate.now()).getYears());
                } else {
                    vo.setAge(75);
                }
                vo.setAddress(elder.getAddress());
                vo.setContactPhone(elder.getContactPhone());
            } else if ("FROZEN".equalsIgnoreCase(link.getStatus())) {
                vo.setFreezeReason(link.getFreezeReasonText());
                vo.setReviewDeadline(link.getReviewDeadline());
            }

            result.add(vo);
        }

        return result;
    }

    @Override
    public List<FamilyLinkApplication> listApplications(String status) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN", "GRID_WORKER");
        List<Long> scopedElderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (scopedElderIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<FamilyLinkApplication> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(FamilyLinkApplication::getElderId, scopedElderIds);
        if (StringUtils.hasText(status)) {
            wrapper.eq(FamilyLinkApplication::getStatus, status.trim().toUpperCase());
        }
        wrapper.orderByDesc(FamilyLinkApplication::getCreatedAt);
        return applicationMapper.selectList(wrapper);
    }

    private Long parseUserId(CurrentUser user) {
        try {
            return Long.parseLong(user.getUserId());
        } catch (RuntimeException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
    }
}
