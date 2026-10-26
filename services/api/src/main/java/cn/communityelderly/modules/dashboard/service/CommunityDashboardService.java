package cn.communityelderly.modules.dashboard.service;

import cn.communityelderly.modules.dashboard.dto.DashboardChartsVO;
import cn.communityelderly.modules.dashboard.dto.DashboardSummaryVO;
import cn.communityelderly.modules.dashboard.dto.RealtimeAlertFeedVO;

import java.util.List;

public interface CommunityDashboardService {

    DashboardSummaryVO getSummary(Long communityId);

    DashboardChartsVO getCharts(Long communityId, String period);

    List<RealtimeAlertFeedVO> getRealtimeAlerts(Long communityId, int limit);
}
