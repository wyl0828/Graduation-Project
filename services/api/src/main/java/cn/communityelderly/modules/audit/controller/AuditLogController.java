package cn.communityelderly.modules.audit.controller;

import cn.communityelderly.common.api.PageResult;
import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.dto.AuditDiffVO;
import cn.communityelderly.modules.audit.dto.AuditLogQueryDTO;
import cn.communityelderly.modules.audit.dto.AuditLogVO;
import cn.communityelderly.modules.audit.entity.AuditLog;
import cn.communityelderly.modules.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/audit/logs")
@RequireRoles({"COMMUNITY_ADMIN"})
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public Result<PageResult<AuditLogVO>> queryAuditLogs(AuditLogQueryDTO queryDTO) {
        return Result.success(auditLogService.queryAuditLogs(queryDTO));
    }

    @GetMapping("/{auditId}/diff")
    public Result<AuditDiffVO> getAuditDiff(@PathVariable Long auditId) {
        return Result.success(auditLogService.getAuditDiff(auditId));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAuditLogs(AuditLogQueryDTO queryDTO) {
        String csvContent = auditLogService.exportAuditLogsAsCsv(queryDTO);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_compliance_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @PostMapping
    public Result<Long> recordManualAudit(@RequestBody AuditLog log, HttpServletRequest request) {
        CurrentUser user = UserContext.get();
        Long actorId = user != null && user.getUserId() != null ? Long.parseLong(user.getUserId()) : 0L;
        String actorRole = user != null ? user.getCurrentRole() : "SYSTEM";
        Long communityId = (user != null && user.getCommunityId() != null) ? Long.parseLong(user.getCommunityId()) : null;

        AuditLog saved = auditLogService.recordAudit(
                actorId,
                actorRole,
                communityId,
                log.getTargetType(),
                log.getTargetId(),
                log.getAction(),
                log.getResult(),
                request.getRemoteAddr(),
                log.getDetailsJson()
        );
        return Result.success(saved.getAuditId());
    }
}
