package cn.communityelderly.modules.elder.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.ConflictException;
import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.dto.CorrectionApplyDTO;
import cn.communityelderly.modules.elder.dto.CorrectionDetailVO;
import cn.communityelderly.modules.elder.dto.CorrectionReviewDTO;
import cn.communityelderly.modules.elder.entity.ElderCorrectionItem;
import cn.communityelderly.modules.elder.entity.ElderCorrectionRequest;
import cn.communityelderly.modules.elder.entity.ElderFamilyLink;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderCorrectionItemMapper;
import cn.communityelderly.modules.elder.mapper.ElderCorrectionRequestMapper;
import cn.communityelderly.modules.elder.mapper.ElderFamilyLinkMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.ArchiveCorrectionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ArchiveCorrectionServiceImpl implements ArchiveCorrectionService {

    private final ElderProfileMapper elderProfileMapper;
    private final ElderFamilyLinkMapper familyLinkMapper;
    private final ElderCorrectionRequestMapper correctionRequestMapper;
    private final ElderCorrectionItemMapper correctionItemMapper;
    private final UserAccountMapper userAccountMapper;
    private final AuditLogService auditLogService;
    private final ObjectAuthorizationPolicy authorizationPolicy;

    public ArchiveCorrectionServiceImpl(ElderProfileMapper elderProfileMapper,
                                        ElderFamilyLinkMapper familyLinkMapper,
                                        ElderCorrectionRequestMapper correctionRequestMapper,
                                        ElderCorrectionItemMapper correctionItemMapper,
                                        UserAccountMapper userAccountMapper,
                                        AuditLogService auditLogService,
                                        ObjectAuthorizationPolicy authorizationPolicy) {
        this.elderProfileMapper = elderProfileMapper;
        this.familyLinkMapper = familyLinkMapper;
        this.correctionRequestMapper = correctionRequestMapper;
        this.correctionItemMapper = correctionItemMapper;
        this.userAccountMapper = userAccountMapper;
        this.auditLogService = auditLogService;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitCorrection(CorrectionApplyDTO dto) {
        CurrentUser currentUser = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("CHILD");
        Long childUserId = parseUserId(currentUser);

        // 检查与老人的亲情关系有效性
        ElderFamilyLink link = authorizationPolicy.requireActiveChildLink(dto.getElderId());
        if (!Objects.equals(link.getChildUserId(), childUserId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN_ACCESS, "您未绑定该长辈，无权提交档案更正申请");
        }

        ElderProfile elder = elderProfileMapper.selectById(dto.getElderId());
        if (elder == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "老人档案不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(elder.getStatus())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN_ACCESS, "老人档案已停用，不能提交新的档案更正申请");
        }

        LocalDateTime now = LocalDateTime.now();

        // 固化基线快照并保存申请主记录
        ElderCorrectionRequest request = new ElderCorrectionRequest();
        request.setElderId(dto.getElderId());
        request.setApplicantUserId(childUserId);
        request.setApplicantLinkId(link.getLinkId());
        request.setApplicantLinkGeneration(link.getLinkGeneration());
        request.setStatus("PENDING_REVIEW");
        request.setReason(dto.getReason().trim());
        request.setSubmittedArchiveVersion(elder.getRowVersion());
        request.setSubmittedAt(now);
        request.setRowVersion(1L);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        correctionRequestMapper.insert(request);

        // 逐项固化 baseline_value
        for (CorrectionApplyDTO.Item item : dto.getItems()) {
            String baselineValue = readFieldValue(elder, item.getFieldCode());
            ElderCorrectionItem corrItem = new ElderCorrectionItem();
            corrItem.setRequestId(request.getRequestId());
            corrItem.setFieldCode(item.getFieldCode().trim());
            corrItem.setBaselineValueProtected(baselineValue);
            corrItem.setProposedValueProtected(item.getProposedValue() != null ? item.getProposedValue().trim() : "");
            correctionItemMapper.insert(corrItem);
        }

        return request.getRequestId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = {ConflictException.class, ForbiddenException.class})
    public void reviewCorrection(Long requestId, CorrectionReviewDTO dto) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        ElderCorrectionRequest request = correctionRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "更正申请记录不存在");
        }
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(request.getElderId(), "COMMUNITY_ADMIN");

        if (!"PENDING_REVIEW".equalsIgnoreCase(request.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该更正申请已被处理，不可重复审批");
        }

        CurrentUser reviewer = authorizationPolicy.requireCurrentUser();
        Long reviewerId = parseUserId(reviewer);
        LocalDateTime now = LocalDateTime.now();

        if (Boolean.FALSE.equals(dto.getApproved())) {
            if (!StringUtils.hasText(dto.getDecisionReason())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "驳回更正申请必须明确填写拒绝原因");
            }
            request.setStatus("REJECTED");
            request.setReviewerUserId(reviewerId);
            request.setReviewedAt(now);
            request.setDecisionReason(dto.getDecisionReason().trim());
            request.setUpdatedAt(now);
            correctionRequestMapper.updateById(request);
            return;
        }

        // 批准审批：触发 FZ-01 原子比对基线快照
        UserAccount applicant = userAccountMapper.selectById(request.getApplicantUserId());
        ElderFamilyLink applicantLink = request.getApplicantLinkId() != null
                ? familyLinkMapper.selectById(request.getApplicantLinkId()) : null;
        if (applicant == null
                || !"ACTIVE".equalsIgnoreCase(applicant.getStatus())
                || applicantLink == null
                || !Objects.equals(applicantLink.getElderId(), request.getElderId())
                || !Objects.equals(applicantLink.getChildUserId(), request.getApplicantUserId())
                || !Objects.equals(applicantLink.getLinkGeneration(), request.getApplicantLinkGeneration())
                || !"ACTIVE".equalsIgnoreCase(applicantLink.getStatus())) {
            request.setBlockCode("APPLICANT_AUTHORIZATION_REVOKED");
            request.setBlockAt(now);
            request.setUpdatedAt(now);
            correctionRequestMapper.updateById(request);
            auditLogService.recordAudit(
                    reviewerId,
                    "COMMUNITY_ADMIN",
                    elder.getCommunityId(),
                    "ELDER_CORRECTION_REQUEST",
                    String.valueOf(requestId),
                    "APPROVE_CORRECTION_BLOCKED",
                    "DENIED",
                    "127.0.0.1",
                    "{\"requestId\":" + requestId + ",\"blockCode\":\"APPLICANT_AUTHORIZATION_REVOKED\"}"
            );
            throw new ForbiddenException(ErrorCode.KINSHIP_NOT_ACTIVE, "申请人的账号或亲情授权已失效，不能批准该更正申请");
        }

        List<ElderCorrectionItem> items = correctionItemMapper.selectList(
                new LambdaQueryWrapper<ElderCorrectionItem>()
                        .eq(ElderCorrectionItem::getRequestId, requestId)
        );

        List<Map<String, String>> conflicts = new ArrayList<>();
        for (ElderCorrectionItem item : items) {
            String currentDbValue = readFieldValue(elder, item.getFieldCode());
            String baselineValue = item.getBaselineValueProtected();

            // 比对基线快照是否与数据库当前值一致
            if (!Objects.equals(normalizeValue(baselineValue), normalizeValue(currentDbValue))) {
                conflicts.add(Map.of(
                        "fieldCode", item.getFieldCode(),
                        "baselineValue", baselineValue != null ? baselineValue : "",
                        "currentDbValue", currentDbValue != null ? currentDbValue : "",
                        "proposedValue", item.getProposedValueProtected() != null ? item.getProposedValueProtected() : ""
                ));
            }
        }

        // 若存在快照冲突：判定 409 并发冲突！noRollbackFor 确保 blockCode 留痕成功入库
        if (!conflicts.isEmpty()) {
            request.setBlockCode("FIELD_VALUE_CONFLICT");
            request.setBlockAt(now);
            request.setUpdatedAt(now);
            correctionRequestMapper.updateById(request);

            throw ConflictException.fieldValueConflict(conflicts);
        }

        // 基线快照完全一致：原子更新老人档案字段并递增版本
        for (ElderCorrectionItem item : items) {
            String beforeVal = readFieldValue(elder, item.getFieldCode());
            writeFieldValue(elder, item.getFieldCode(), item.getProposedValueProtected());

            item.setBeforeValueProtected(beforeVal);
            item.setAfterValueProtected(item.getProposedValueProtected());
            item.setAppliedAt(now);
            correctionItemMapper.updateById(item);
        }

        elder.setUpdatedBy(reviewerId);
        elder.setUpdatedAt(now);
        elderProfileMapper.updateById(elder);

        request.setStatus("APPROVED_APPLIED");
        request.setReviewerUserId(reviewerId);
        request.setReviewedAt(now);
        request.setDecisionReason(StringUtils.hasText(dto.getDecisionReason()) ? dto.getDecisionReason() : "快照核验一致，审批通过并应用更正");
        request.setBlockCode(null);
        request.setUpdatedAt(now);
        correctionRequestMapper.updateById(request);

        // 记录系统安全审计日志
        auditLogService.recordAudit(
                reviewerId,
                "COMMUNITY_ADMIN",
                elder.getCommunityId(),
                "ELDER_PROFILE",
                String.valueOf(elder.getElderId()),
                "APPROVE_CORRECTION",
                "SUCCESS",
                "127.0.0.1",
                "{\"requestId\":" + requestId + ",\"reason\":\"" + request.getDecisionReason() + "\"}"
        );
    }

    @Override
    public CorrectionDetailVO getCorrectionDetail(Long requestId) {
        ElderCorrectionRequest request = correctionRequestMapper.selectById(requestId);
        if (request == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "更正申请不存在");
        }
        authorizationPolicy.requireElderAccess(request.getElderId());

        ElderProfile elder = elderProfileMapper.selectById(request.getElderId());
        UserAccount applicant = userAccountMapper.selectById(request.getApplicantUserId());

        List<ElderCorrectionItem> items = correctionItemMapper.selectList(
                new LambdaQueryWrapper<ElderCorrectionItem>()
                        .eq(ElderCorrectionItem::getRequestId, requestId)
        );

        CorrectionDetailVO vo = new CorrectionDetailVO();
        vo.setRequestId(request.getRequestId());
        vo.setElderId(request.getElderId());
        vo.setElderName(elder != null ? elder.getName() : "");
        vo.setArchiveNo(elder != null ? elder.getArchiveNo() : "");
        vo.setApplicantUserId(request.getApplicantUserId());
        vo.setApplicantPhone(applicant != null ? applicant.getPhoneNormalized() : "");
        vo.setStatus(request.getStatus());
        vo.setReason(request.getReason());
        vo.setBlockCode(request.getBlockCode());
        vo.setSubmittedAt(request.getSubmittedAt());
        vo.setReviewedAt(request.getReviewedAt());
        vo.setDecisionReason(request.getDecisionReason());

        List<CorrectionDetailVO.ItemVO> itemVOList = new ArrayList<>();
        for (ElderCorrectionItem item : items) {
            CorrectionDetailVO.ItemVO itemVO = new CorrectionDetailVO.ItemVO();
            itemVO.setFieldCode(item.getFieldCode());
            itemVO.setFieldName(translateFieldName(item.getFieldCode()));
            itemVO.setBaselineValue(item.getBaselineValueProtected());
            itemVO.setProposedValue(item.getProposedValueProtected());

            String currentDbValue = (elder != null) ? readFieldValue(elder, item.getFieldCode()) : "";
            itemVO.setCurrentDbValue(currentDbValue);

            boolean hasConflict = !Objects.equals(normalizeValue(item.getBaselineValueProtected()), normalizeValue(currentDbValue));
            itemVO.setHasConflict(hasConflict);

            itemVOList.add(itemVO);
        }

        vo.setItems(itemVOList);
        return vo;
    }

    @Override
    public List<CorrectionDetailVO> listCorrections(String status) {
        List<Long> scopedElderIds = authorizationPolicy.listElderIdsForCurrentUser();
        if (scopedElderIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<ElderCorrectionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ElderCorrectionRequest::getElderId, scopedElderIds);
        if (StringUtils.hasText(status)) {
            wrapper.eq(ElderCorrectionRequest::getStatus, status.trim().toUpperCase());
        }
        wrapper.orderByDesc(ElderCorrectionRequest::getSubmittedAt);

        List<ElderCorrectionRequest> requests = correctionRequestMapper.selectList(wrapper);
        return requests.stream().map(req -> getCorrectionDetail(req.getRequestId())).toList();
    }

    private String readFieldValue(ElderProfile elder, String fieldCode) {
        if (elder == null || !StringUtils.hasText(fieldCode)) return "";
        return switch (fieldCode.toLowerCase()) {
            case "address" -> elder.getAddress();
            case "contactphone", "contact_phone" -> elder.getContactPhone();
            case "emergencycontactname", "emergency_contact_name" -> elder.getEmergencyContactName();
            case "emergencycontactphone", "emergency_contact_phone" -> elder.getEmergencyContactPhone();
            case "emergencycontactrelation", "emergency_contact_relation" -> elder.getEmergencyContactRelation();
            default -> "";
        };
    }

    private void writeFieldValue(ElderProfile elder, String fieldCode, String newValue) {
        if (elder == null || !StringUtils.hasText(fieldCode)) return;
        switch (fieldCode.toLowerCase()) {
            case "address" -> elder.setAddress(newValue);
            case "contactphone", "contact_phone" -> elder.setContactPhone(newValue);
            case "emergencycontactname", "emergency_contact_name" -> elder.setEmergencyContactName(newValue);
            case "emergencycontactphone", "emergency_contact_phone" -> elder.setEmergencyContactPhone(newValue);
            case "emergencycontactrelation", "emergency_contact_relation" -> elder.setEmergencyContactRelation(newValue);
        }
    }

    private String normalizeValue(String val) {
        return val != null ? val.trim() : "";
    }

    private Long parseUserId(CurrentUser user) {
        try {
            return Long.parseLong(user.getUserId());
        } catch (RuntimeException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
    }

    private String translateFieldName(String fieldCode) {
        return switch (fieldCode.toLowerCase()) {
            case "address" -> "现居地址";
            case "contactphone", "contact_phone" -> "联系电话";
            case "emergencycontactname", "emergency_contact_name" -> "紧急联系人姓名";
            case "emergencycontactphone", "emergency_contact_phone" -> "紧急联系人电话";
            case "emergencycontactrelation", "emergency_contact_relation" -> "紧急联系人关系";
            default -> fieldCode;
        };
    }
}
