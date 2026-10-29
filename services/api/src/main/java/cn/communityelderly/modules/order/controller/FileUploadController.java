package cn.communityelderly.modules.order.controller;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.order.service.FileEvidenceService;
import cn.communityelderly.modules.order.service.WorkOrderService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {
    private final WorkOrderService workOrders;
    private final FileEvidenceService evidence;
    public FileUploadController(WorkOrderService workOrders, FileEvidenceService evidence) {
        this.workOrders = workOrders;
        this.evidence = evidence;
    }

    @PostMapping("/upload")
    @RequireRoles({"GRID_WORKER", "CARE_WORKER", "COMMUNITY_ADMIN"})
    @Transactional
    public Result<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "workOrderId", required = false) Long workOrderId) {
        UserContext.requireCurrentUser();
        try {
            if (workOrderId == null || workOrderId <= 0)
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "上传照片必须绑定工单");
            workOrders.assertCurrentMayUploadEvidence(workOrderId);
            return Result.success(evidence.upload(file, workOrderId));
        } catch (BusinessException exception) {
            evidence.rejected(workOrderId, "FILE_UPLOAD", exception.getErrorCode());
            throw exception;
        }
    }

    @GetMapping("/view/{filename:.+}")
    public ResponseEntity<Resource> viewPhoto(@PathVariable String filename) {
        UserContext.requireCurrentUser();
        Long workOrderId = null;
        try {
            var metadata = evidence.findMetadata(filename);
            workOrderId = metadata == null ? evidence.legacyWorkOrderId(filename) : metadata.workOrderId();
            workOrders.assertCurrentMayReadEvidence(workOrderId);
            if (metadata != null) evidence.requireVisibleVersion(metadata);
            Resource resource = metadata == null ? evidence.legacyResource(filename) : evidence.resource(metadata);
            String mime = metadata == null ? evidence.legacyMime(filename) : metadata.mimeType();
            evidence.readSucceeded(workOrderId);
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(mime))
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (BusinessException exception) {
            evidence.rejected(workOrderId, "FILE_READ", exception.getErrorCode());
            throw exception;
        }
    }
}
