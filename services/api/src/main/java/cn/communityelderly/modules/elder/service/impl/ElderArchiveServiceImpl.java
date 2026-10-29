package cn.communityelderly.modules.elder.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.elder.dto.*;
import cn.communityelderly.modules.elder.entity.ElderActivationTicket;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderActivationTicketMapper;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.order.service.WorkOrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ElderArchiveServiceImpl implements ElderArchiveService {

    private final ElderProfileMapper elderProfileMapper;
    private final ElderActivationTicketMapper activationTicketMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;
    private final WorkOrderService workOrderService;

    public ElderArchiveServiceImpl(ElderProfileMapper elderProfileMapper,
                                   ElderActivationTicketMapper activationTicketMapper,
                                   ObjectAuthorizationPolicy authorizationPolicy,
                                   WorkOrderService workOrderService) {
        this.elderProfileMapper = elderProfileMapper;
        this.activationTicketMapper = activationTicketMapper;
        this.authorizationPolicy = authorizationPolicy;
        this.workOrderService = workOrderService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ElderArchiveVO createElder(ElderCreateDTO dto) {
        // 手机号缺失联动校验
        if (!StringUtils.hasText(dto.getContactPhone()) && !StringUtils.hasText(dto.getContactPhoneMissingReason())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未填写联系电话时，必须说明缺失原因（如：高龄无手机等）");
        }

        // 紧急联系人联动校验
        if (!StringUtils.hasText(dto.getEmergencyContactPhone()) && !StringUtils.hasText(dto.getEmergencyContactPhoneMissingReason())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "未填写紧急联系人电话时，必须说明缺失原因");
        }

        CurrentUser user = authorizationPolicy.requireCurrentUser();
        authorizationPolicy.requireRole("COMMUNITY_ADMIN");
        Long operatorId = parseCurrentUserId(user);
        Long communityId = parseCurrentCommunityId(user);

        LocalDateTime now = LocalDateTime.now();
        String timestampPrefix = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        String archiveNo = "ARC" + timestampPrefix + randomSuffix;

        ElderProfile elder = new ElderProfile();
        elder.setArchiveNo(archiveNo);
        elder.setCommunityId(communityId);
        elder.setName(dto.getName().trim());
        elder.setSex(StringUtils.hasText(dto.getSex()) ? dto.getSex().toUpperCase() : "UNKNOWN");
        elder.setBirthDate(dto.getBirthDate() != null ? dto.getBirthDate() : LocalDate.of(1950, 1, 1));
        elder.setBirthDateUnknownReason(dto.getBirthDateUnknownReason());
        elder.setAddress(dto.getAddress().trim());
        elder.setContactPhone(dto.getContactPhone());
        elder.setContactPhoneMissingReason(dto.getContactPhoneMissingReason());
        elder.setEmergencyContactName(dto.getEmergencyContactName());
        elder.setEmergencyContactRelation(dto.getEmergencyContactRelation());
        elder.setEmergencyContactPhone(dto.getEmergencyContactPhone());
        elder.setEmergencyContactNameMissingReason(dto.getEmergencyContactNameMissingReason());
        elder.setEmergencyContactRelationMissingReason(dto.getEmergencyContactRelationMissingReason());
        elder.setEmergencyContactPhoneMissingReason(dto.getEmergencyContactPhoneMissingReason());
        elder.setStatus("ACTIVE");
        elder.setRowVersion(1L);
        elder.setCreatedBy(operatorId);
        elder.setCreatedAt(now);
        elder.setUpdatedBy(operatorId);
        elder.setUpdatedAt(now);

        elderProfileMapper.insert(elder);

        return convertToVO(elder);
    }

    @Override
    public PageResult<ElderArchiveVO> pageElders(ElderQueryDTO query) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN", "GRID_WORKER");
        Long communityId = parseCurrentCommunityId(authorizationPolicy.requireCurrentUser());
        Page<ElderProfile> pageParam = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<ElderProfile> wrapper = new LambdaQueryWrapper<ElderProfile>()
                .eq(ElderProfile::getCommunityId, communityId);

        if (StringUtils.hasText(query.getName())) {
            wrapper.like(ElderProfile::getName, query.getName().trim());
        }
        if (StringUtils.hasText(query.getArchiveNo())) {
            wrapper.eq(ElderProfile::getArchiveNo, query.getArchiveNo().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(ElderProfile::getStatus, query.getStatus().trim());
        }
        wrapper.orderByDesc(ElderProfile::getCreatedAt);

        Page<ElderProfile> result = elderProfileMapper.selectPage(pageParam, wrapper);

        List<ElderArchiveVO> voList = result.getRecords().stream().map(this::convertToVO).toList();

        return PageResult.of(voList, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public ElderArchiveVO getElderById(Long elderId) {
        authorizationPolicy.requireElderAccess(elderId);
        ElderProfile profile = getElderEntity(elderId);
        return convertToVO(profile);
    }

    @Override
    public ElderProfile getElderEntity(Long elderId) {
        ElderProfile profile = elderProfileMapper.selectById(elderId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "老人档案不存在 (elderId=" + elderId + ")");
        }
        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateElderStatus(Long elderId, String status, String reason) {
        authorizationPolicy.requireElderInCurrentCommunity(elderId, "COMMUNITY_ADMIN");
        // Share the elder-first lock order with reservation creation and confirmation.
        ElderProfile profile = elderProfileMapper.selectByIdForUpdate(elderId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        String normalizedStatus = status.toUpperCase();
        if ("INACTIVE".equals(normalizedStatus) && !"INACTIVE".equalsIgnoreCase(profile.getStatus())) {
            workOrderService.assertElderMayBeDeactivated(elderId);
        }
        profile.setStatus(normalizedStatus);
        profile.setStatusReason(reason);
        profile.setUpdatedAt(LocalDateTime.now());
        if (elderProfileMapper.updateById(profile) < 1) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "老人档案已被其他操作更新，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ElderProfile updateElderProfileSelf(Long elderId, ElderProfileUpdateDTO dto) {
        ElderProfile profile = authorizationPolicy.requireElderAccess(elderId);

        if (StringUtils.hasText(dto.getAddress())) {
            profile.setAddress(dto.getAddress().trim());
        }
        if (StringUtils.hasText(dto.getContactPhone())) {
            profile.setContactPhone(dto.getContactPhone().trim());
        }
        if (StringUtils.hasText(dto.getEmergencyContactName())) {
            profile.setEmergencyContactName(dto.getEmergencyContactName().trim());
        }
        if (StringUtils.hasText(dto.getEmergencyContactPhone())) {
            profile.setEmergencyContactPhone(dto.getEmergencyContactPhone().trim());
        }
        if (StringUtils.hasText(dto.getEmergencyContactRelation())) {
            profile.setEmergencyContactRelation(dto.getEmergencyContactRelation().trim());
        }

        profile.setUpdatedAt(LocalDateTime.now());
        elderProfileMapper.updateById(profile);
        return profile;
    }

    private ElderArchiveVO convertToVO(ElderProfile entity) {
        ElderArchiveVO vo = new ElderArchiveVO();
        vo.setElderId(entity.getElderId());
        vo.setArchiveNo(entity.getArchiveNo());
        vo.setName(entity.getName());
        vo.setSex(entity.getSex());
        vo.setBirthDate(entity.getBirthDate());
        if (entity.getBirthDate() != null) {
            vo.setAge(Period.between(entity.getBirthDate(), LocalDate.now()).getYears());
        } else {
            vo.setAge(75); // 兜底适老默认
        }
        vo.setAddress(entity.getAddress());
        vo.setContactPhone(entity.getContactPhone());
        vo.setEmergencyContactName(entity.getEmergencyContactName());
        vo.setEmergencyContactPhone(entity.getEmergencyContactPhone());
        vo.setEmergencyContactRelation(entity.getEmergencyContactRelation());
        vo.setStatus(entity.getStatus());
        vo.setRowVersion(entity.getRowVersion());
        vo.setCreatedAt(entity.getCreatedAt());

        // 检查是否存在当前有效未消费的激活码
        ElderActivationTicket activeTicket = activationTicketMapper.selectOne(
                new LambdaQueryWrapper<ElderActivationTicket>()
                        .eq(ElderActivationTicket::getElderId, entity.getElderId())
                        .eq(ElderActivationTicket::getStatus, "ISSUED")
                        .gt(ElderActivationTicket::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(ElderActivationTicket::getIssuedAt)
                        .last("LIMIT 1")
        );
        vo.setHasActiveActivationCode(activeTicket != null);
        vo.setLatestActivationTicketId(activeTicket != null ? activeTicket.getTicketId() : null);

        return vo;
    }

    private Long parseCurrentUserId(CurrentUser user) {
        try {
            return Long.parseLong(user.getUserId());
        } catch (RuntimeException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前登录身份无效");
        }
    }

    private Long parseCurrentCommunityId(CurrentUser user) {
        try {
            Long communityId = Long.parseLong(user.getCommunityId());
            if (communityId <= 0) throw new NumberFormatException("invalid community id");
            return communityId;
        } catch (RuntimeException exception) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED, "当前角色未分配有效社区范围");
        }
    }
}
