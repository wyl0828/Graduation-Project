package cn.communityelderly.modules.health.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.health.dto.VitalRecordCreateDTO;
import cn.communityelderly.modules.health.dto.VitalTrendQueryDTO;
import cn.communityelderly.modules.health.dto.VitalTrendVO;
import cn.communityelderly.modules.health.entity.VitalRecord;
import cn.communityelderly.modules.health.mapper.VitalRecordMapper;
import cn.communityelderly.modules.health.service.AssessmentService;
import cn.communityelderly.modules.health.service.VitalRecordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class VitalRecordServiceImpl implements VitalRecordService {

    private final VitalRecordMapper vitalRecordMapper;
    private final AssessmentService assessmentService;
    private final ObjectAuthorizationPolicy authorizationPolicy;

    public VitalRecordServiceImpl(VitalRecordMapper vitalRecordMapper,
                                  AssessmentService assessmentService,
                                  ObjectAuthorizationPolicy authorizationPolicy) {
        this.vitalRecordMapper = vitalRecordMapper;
        this.assessmentService = assessmentService;
        this.authorizationPolicy = authorizationPolicy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long recordVital(VitalRecordCreateDTO dto) {
        ElderProfile elder = authorizationPolicy.requireElderInCurrentCommunity(dto.getElderId(), "COMMUNITY_ADMIN");
        if (!"ACTIVE".equalsIgnoreCase(elder.getStatus())) throw inaccessible();
        Long creatorId = Long.parseLong(authorizationPolicy.requireCurrentUser().getUserId());
        LocalDateTime now = LocalDateTime.now();

        VitalRecord record = new VitalRecord();
        record.setElderId(dto.getElderId());
        record.setVitalType(dto.getVitalType().trim().toUpperCase());
        record.setValuePrimary(dto.getValuePrimary());
        record.setValueSecondary(dto.getValueSecondary());
        record.setUnit(dto.getUnit().trim());
        record.setSourceType(StringUtils.hasText(dto.getSourceType()) ? dto.getSourceType().trim().toUpperCase() : "MANUAL");
        record.setRecordedAt(dto.getRecordedAt() != null ? dto.getRecordedAt() : now);
        record.setCreatedBy(creatorId);
        record.setCreatedAt(now);
        record.setRemark(dto.getRemark());
        record.setIdempotencyKey(dto.getIdempotencyKey());

        vitalRecordMapper.insert(record);

        // 即刻执行阈值评估判定
        assessmentService.evaluateVital(record);

        return record.getVitalId();
    }

    @Override
    public VitalTrendVO queryTrend(VitalTrendQueryDTO query) {
        authorizationPolicy.requireRole("ELDER", "CHILD", "COMMUNITY_ADMIN");
        ElderProfile elder = authorizationPolicy.requireElderAccess(query.getElderId());

        LocalDateTime since = LocalDateTime.now().minusDays(query.getDays());
        List<VitalRecord> records = vitalRecordMapper.selectList(
                new LambdaQueryWrapper<VitalRecord>()
                        .eq(VitalRecord::getElderId, query.getElderId())
                        .eq(VitalRecord::getVitalType, query.getVitalType().toUpperCase())
                        .ge(VitalRecord::getRecordedAt, since)
                        .orderByAsc(VitalRecord::getRecordedAt)
        );

        VitalTrendVO vo = new VitalTrendVO();
        vo.setElderId(elder.getElderId());
        vo.setElderName(elder.getName());
        vo.setVitalType(query.getVitalType().toUpperCase());

        List<VitalTrendVO.DataPoint> points = new ArrayList<>();
        for (VitalRecord r : records) {
            VitalTrendVO.DataPoint p = new VitalTrendVO.DataPoint();
            p.setVitalId(r.getVitalId());
            p.setRecordedAt(r.getRecordedAt());
            p.setValuePrimary(r.getValuePrimary());
            p.setValueSecondary(r.getValueSecondary());
            p.setSourceType(r.getSourceType());

            // 标记是否超标
            boolean abnormal = false;
            if ("BLOOD_PRESSURE".equalsIgnoreCase(r.getVitalType())) {
                abnormal = (r.getValuePrimary() != null && r.getValuePrimary().compareTo(BigDecimal.valueOf(140)) >= 0) ||
                           (r.getValueSecondary() != null && r.getValueSecondary().compareTo(BigDecimal.valueOf(90)) >= 0);
            } else if ("BLOOD_SUGAR".equalsIgnoreCase(r.getVitalType())) {
                abnormal = (r.getValuePrimary() != null && r.getValuePrimary().compareTo(BigDecimal.valueOf(7.8)) >= 0);
            }
            p.setIsAbnormal(abnormal);
            points.add(p);
        }

        vo.setDataPoints(points);

        if (!points.isEmpty()) {
            VitalTrendVO.DataPoint last = points.get(points.size() - 1);
            vo.setLatestRecordedAt(last.getRecordedAt());
            if ("BLOOD_PRESSURE".equalsIgnoreCase(query.getVitalType()) && last.getValueSecondary() != null) {
                vo.setLatestValueText(last.getValuePrimary() + " / " + last.getValueSecondary() + " mmHg");
            } else {
                vo.setLatestValueText(last.getValuePrimary() + "");
            }
            vo.setLatestStatus(Boolean.TRUE.equals(last.getIsAbnormal()) ? "HIGH" : "NORMAL");
        } else {
            vo.setLatestValueText("暂无数据");
            vo.setLatestStatus("NORMAL");
        }

        return vo;
    }

    @Override
    public VitalTrendVO.DataPoint getLatestVital(Long elderId, String vitalType) {
        authorizationPolicy.requireRole("ELDER", "CHILD", "COMMUNITY_ADMIN");
        authorizationPolicy.requireElderAccess(elderId);
        VitalRecord record = vitalRecordMapper.selectOne(
                new LambdaQueryWrapper<VitalRecord>()
                        .eq(VitalRecord::getElderId, elderId)
                        .eq(VitalRecord::getVitalType, StringUtils.hasText(vitalType) ? vitalType.toUpperCase() : "BLOOD_PRESSURE")
                        .orderByDesc(VitalRecord::getRecordedAt)
                        .last("LIMIT 1")
        );

        if (record == null) return null;

        VitalTrendVO.DataPoint p = new VitalTrendVO.DataPoint();
        p.setVitalId(record.getVitalId());
        p.setRecordedAt(record.getRecordedAt());
        p.setValuePrimary(record.getValuePrimary());
        p.setValueSecondary(record.getValueSecondary());
        p.setSourceType(record.getSourceType());
        return p;
    }

    private BusinessException inaccessible() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE);
    }
}
