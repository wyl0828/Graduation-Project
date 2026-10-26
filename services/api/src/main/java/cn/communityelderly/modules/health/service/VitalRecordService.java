package cn.communityelderly.modules.health.service;

import cn.communityelderly.modules.health.dto.VitalRecordCreateDTO;
import cn.communityelderly.modules.health.dto.VitalTrendQueryDTO;
import cn.communityelderly.modules.health.dto.VitalTrendVO;

public interface VitalRecordService {

    Long recordVital(VitalRecordCreateDTO dto);

    VitalTrendVO queryTrend(VitalTrendQueryDTO query);

    VitalTrendVO.DataPoint getLatestVital(Long elderId, String vitalType);
}
