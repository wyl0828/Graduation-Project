package cn.communityelderly.modules.health.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.auth.entity.UserAccount;
import cn.communityelderly.modules.auth.mapper.UserAccountMapper;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.health.dto.AlertHandleDTO;
import cn.communityelderly.modules.health.dto.HealthAlertQueryDTO;
import cn.communityelderly.modules.health.dto.HealthAlertVO;
import cn.communityelderly.modules.health.entity.AlertTriggerEvidence;
import cn.communityelderly.modules.health.entity.HealthAlert;
import cn.communityelderly.modules.health.entity.VitalRecord;
import cn.communityelderly.modules.health.mapper.AlertTriggerEvidenceMapper;
import cn.communityelderly.modules.health.mapper.HealthAlertMapper;
import cn.communityelderly.modules.health.mapper.VitalRecordMapper;
import cn.communityelderly.modules.health.service.HealthAlertService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class HealthAlertServiceImpl implements HealthAlertService {

    private final HealthAlertMapper healthAlertMapper;
    private final AlertTriggerEvidenceMapper evidenceMapper;
    private final ElderProfileMapper elderProfileMapper;
    private final VitalRecordMapper vitalRecordMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectAuthorizationPolicy authorizationPolicy;

    public HealthAlertServiceImpl(HealthAlertMapper healthAlertMapper,
                                  AlertTriggerEvidenceMapper evidenceMapper,
                                  ElderProfileMapper elderProfileMapper,
                                  VitalRecordMapper vitalRecordMapper,
                                  UserAccountMapper userAccountMapper,
                                  ObjectAuthorizationPolicy authorizationPolicy) {
        this.healthAlertMapper = healthAlertMapper;
        this.evidenceMapper = evidenceMapper;
        this.elderProfileMapper = elderProfileMapper;
        this.vitalRecordMapper = vitalRecordMapper;
        this.userAccountMapper = userAccountMapper;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    public PageResult<HealthAlertVO> pageAlerts(HealthAlertQueryDTO query) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN", "GRID_WORKER", "CHILD");
        Page<HealthAlert> pageParam = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<HealthAlert> wrapper = new LambdaQueryWrapper<>();

        if (query.getElderId() != null) {
            authorizationPolicy.requireElderAccess(query.getElderId());
            wrapper.eq(HealthAlert::getElderId, query.getElderId());
        } else {
            List<Long> scopedElderIds = authorizationPolicy.listElderIdsForCurrentUser();
            if (scopedElderIds.isEmpty()) {
                return PageResult.of(new ArrayList<>(), query.getPage(), query.getSize(), 0);
            }
            wrapper.in(HealthAlert::getElderId, scopedElderIds);
        }
        if (StringUtils.hasText(query.getAlertLevel())) {
            wrapper.eq(HealthAlert::getAlertLevel, query.getAlertLevel().trim().toUpperCase());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(HealthAlert::getStatus, query.getStatus().trim().toUpperCase());
        }
        wrapper.orderByDesc(HealthAlert::getCreatedAt);

        Page<HealthAlert> result = healthAlertMapper.selectPage(pageParam, wrapper);

        List<HealthAlertVO> voList = new ArrayList<>();
        for (HealthAlert alert : result.getRecords()) {
            voList.add(convertToVO(alert));
        }

        return PageResult.of(voList, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public HealthAlertVO getAlertDetail(Long alertId) {
        authorizationPolicy.requireRole("COMMUNITY_ADMIN", "GRID_WORKER", "CHILD");
        HealthAlert alert = healthAlertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        authorizationPolicy.requireElderAccess(alert.getElderId());
        return convertToVO(alert);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleAlert(Long alertId, AlertHandleDTO dto) {
        HealthAlert alert = healthAlertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
        }
        authorizationPolicy.requireElderInCurrentCommunity(alert.getElderId(), "COMMUNITY_ADMIN");

        if ("CLOSED".equalsIgnoreCase(alert.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该告警已结案，不可重复处理");
        }

        CurrentUser user = authorizationPolicy.requireCurrentUser();
        Long handlerId = Long.parseLong(user.getUserId());
        LocalDateTime now = LocalDateTime.now();

        alert.setStatus("CLOSED");
        alert.setHandledBy(handlerId);
        alert.setHandledAt(now);
        alert.setHandlingResult(dto.getHandlingResult().trim());
        alert.setUpdatedAt(now);

        healthAlertMapper.updateById(alert);
    }

    private HealthAlertVO convertToVO(HealthAlert alert) {
        HealthAlertVO vo = new HealthAlertVO();
        vo.setAlertId(alert.getAlertId());
        vo.setElderId(alert.getElderId());
        vo.setAlertCategory(alert.getAlertCategory());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setStatus(alert.getStatus());
        vo.setCreatedAt(alert.getCreatedAt());
        vo.setHandledBy(alert.getHandledBy());
        vo.setHandledAt(alert.getHandledAt());
        vo.setHandlingResult(alert.getHandlingResult());

        ElderProfile elder = elderProfileMapper.selectById(alert.getElderId());
        if (elder != null) {
            vo.setElderName(elder.getName());
            vo.setElderAddress(elder.getAddress());
            if (elder.getBirthDate() != null) {
                vo.setElderAge(Period.between(elder.getBirthDate(), LocalDate.now()).getYears());
            } else {
                vo.setElderAge(75);
            }
        }

        AlertTriggerEvidence evidence = evidenceMapper.selectOne(
                new LambdaQueryWrapper<AlertTriggerEvidence>()
                        .eq(AlertTriggerEvidence::getAlertId, alert.getAlertId())
                        .last("LIMIT 1")
        );
        if (evidence != null) {
            vo.setMetricName(evidence.getMetricName());
            vo.setActualValue(evidence.getActualValue());
            vo.setThresholdValue(evidence.getThresholdValue());
        }

        VitalRecord vital = vitalRecordMapper.selectById(alert.getVitalId());
        if (vital != null) {
            vo.setSourceType(vital.getSourceType());
        }

        if (alert.getHandledBy() != null) {
            UserAccount handler = userAccountMapper.selectById(alert.getHandledBy());
            vo.setHandlerName(handler != null ? handler.getPhoneNormalized() : "社区管理员");
        }

        return vo;
    }
}
