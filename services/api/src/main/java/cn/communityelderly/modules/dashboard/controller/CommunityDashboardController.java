package cn.communityelderly.modules.dashboard.controller;

import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.exception.ForbiddenException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.dashboard.dto.DashboardChartsVO;
import cn.communityelderly.modules.dashboard.dto.DashboardSummaryVO;
import cn.communityelderly.modules.dashboard.dto.RealtimeAlertFeedVO;
import cn.communityelderly.modules.dashboard.service.CommunityDashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard/community")
@RequireRoles({"COMMUNITY_ADMIN", "GRID_WORKER"})
public class CommunityDashboardController {

    private final CommunityDashboardService dashboardService;

    public CommunityDashboardController(CommunityDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    private Long resolveCommunityId(Long requestedCommunityId) {
        CurrentUser user = UserContext.requireCurrentUser();
        if (user.getCommunityId() == null) {
            throw new ForbiddenException("当前角色未分配社区范围");
        }
        long assignedCommunityId;
        try {
            assignedCommunityId = Long.parseLong(user.getCommunityId());
        } catch (NumberFormatException ignored) {
            throw new ForbiddenException("当前角色社区范围无效");
        }
        if (requestedCommunityId != null && requestedCommunityId != assignedCommunityId) {
            throw new ForbiddenException("超出当前角色管辖社区范围");
        }
        return assignedCommunityId;
    }

    @GetMapping("/summary")
    public Result<DashboardSummaryVO> getSummary(@RequestParam(value = "communityId", required = false) Long communityId) {
        Long targetCommunityId = resolveCommunityId(communityId);
        return Result.success(dashboardService.getSummary(targetCommunityId));
    }

    @GetMapping("/charts")
    public Result<DashboardChartsVO> getCharts(@RequestParam(value = "communityId", required = false) Long communityId,
                                              @RequestParam(value = "period", defaultValue = "7D") String period) {
        Long targetCommunityId = resolveCommunityId(communityId);
        return Result.success(dashboardService.getCharts(targetCommunityId, period));
    }

    @GetMapping("/alerts-feed")
    public Result<List<RealtimeAlertFeedVO>> getRealtimeAlerts(@RequestParam(value = "communityId", required = false) Long communityId,
                                                              @RequestParam(value = "limit", defaultValue = "10") int limit) {
        Long targetCommunityId = resolveCommunityId(communityId);
        return Result.success(dashboardService.getRealtimeAlerts(targetCommunityId, limit));
    }
}
