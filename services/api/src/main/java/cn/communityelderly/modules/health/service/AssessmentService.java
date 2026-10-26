package cn.communityelderly.modules.health.service;

import cn.communityelderly.modules.health.entity.HealthAlert;
import cn.communityelderly.modules.health.entity.VitalRecord;

public interface AssessmentService {

    HealthAlert evaluateVital(VitalRecord vitalRecord);
}
