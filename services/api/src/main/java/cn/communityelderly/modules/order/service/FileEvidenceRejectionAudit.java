package cn.communityelderly.modules.order.service;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileEvidenceRejectionAudit {
    private final AuditLogService audit;
    public FileEvidenceRejectionAudit(AuditLogService audit) { this.audit = audit; }

    // A rejected business transaction must not erase the security evidence.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long workOrderId, String action, ErrorCode errorCode) {
        CurrentUser user = UserContext.get();
        if (user == null) return;
        String safeReason = errorCode == null ? "UNKNOWN" : errorCode.name();
        audit.recordAudit(Long.valueOf(user.getUserId()), user.getCurrentRole(),
                user.getCommunityId() == null ? null : Long.valueOf(user.getCommunityId()),
                "WORK_ORDER", String.valueOf(workOrderId), action, "DENIED", null,
                "{\"errorCode\":\"" + safeReason + "\"}");
    }
}
