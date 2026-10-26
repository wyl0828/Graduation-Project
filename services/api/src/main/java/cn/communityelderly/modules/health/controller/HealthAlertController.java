package cn.communityelderly.modules.health.controller;

import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.modules.health.dto.AlertHandleDTO;
import cn.communityelderly.modules.health.dto.HealthAlertQueryDTO;
import cn.communityelderly.modules.health.dto.HealthAlertVO;
import cn.communityelderly.modules.health.service.HealthAlertService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/health/alerts")
public class HealthAlertController {

    private final HealthAlertService healthAlertService;

    public HealthAlertController(HealthAlertService healthAlertService) {
        this.healthAlertService = healthAlertService;
    }

    @GetMapping
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER", "CHILD"})
    public Result<PageResult<HealthAlertVO>> pageAlerts(HealthAlertQueryDTO query) {
        return Result.success(healthAlertService.pageAlerts(query));
    }

    @GetMapping("/{alertId}")
    @RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER", "CHILD"})
    public Result<HealthAlertVO> getAlertDetail(@PathVariable Long alertId) {
        return Result.success(healthAlertService.getAlertDetail(alertId));
    }

    @PostMapping("/{alertId}/handle")
    @RequireRoles({"COMMUNITY_ADMIN"})
    public Result<Void> handleAlert(@PathVariable Long alertId, @Valid @RequestBody AlertHandleDTO dto) {
        healthAlertService.handleAlert(alertId, dto);
        return Result.success(null);
    }
}
