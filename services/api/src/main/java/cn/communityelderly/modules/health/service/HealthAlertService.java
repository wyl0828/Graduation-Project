package cn.communityelderly.modules.health.service;

import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.modules.health.dto.AlertHandleDTO;
import cn.communityelderly.modules.health.dto.HealthAlertQueryDTO;
import cn.communityelderly.modules.health.dto.HealthAlertVO;

public interface HealthAlertService {

    PageResult<HealthAlertVO> pageAlerts(HealthAlertQueryDTO query);

    HealthAlertVO getAlertDetail(Long alertId);

    void handleAlert(Long alertId, AlertHandleDTO dto);
}
