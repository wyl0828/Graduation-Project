package cn.communityelderly.modules.audit.service;

import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.modules.audit.dto.AuditDiffVO;
import cn.communityelderly.modules.audit.dto.AuditLogQueryDTO;
import cn.communityelderly.modules.audit.dto.AuditLogVO;
import cn.communityelderly.modules.audit.entity.AuditLog;

public interface AuditLogService {

    AuditLog recordAudit(Long actorUserId, String actorRole, Long communityId, String targetType, String targetId, String action, String result, String clientIp, String detailsJson);

    PageResult<AuditLogVO> queryAuditLogs(AuditLogQueryDTO queryDTO);

    AuditDiffVO getAuditDiff(Long auditId);

    String exportAuditLogsAsCsv(AuditLogQueryDTO queryDTO);
}
