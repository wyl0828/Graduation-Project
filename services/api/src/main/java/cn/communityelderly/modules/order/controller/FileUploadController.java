package cn.communityelderly.modules.order.controller;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.api.Result;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.RequireRoles;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.order.service.WorkOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private final Path storageDirectory;
    private final WorkOrderService workOrderService;

    @Autowired
    public FileUploadController(WorkOrderService workOrderService) {
        this(workOrderService, System.getenv("APP_STORAGE_ROOT"));
    }

    public FileUploadController(WorkOrderService workOrderService, String appStorageRoot) {
        this.workOrderService = workOrderService;
        this.storageDirectory = resolveStorageDirectory(appStorageRoot);
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (IOException e) {
            throw new RuntimeException("无法初始化现场凭据照片存储目录: " + storageDirectory, e);
        }
    }

    private static Path resolveStorageDirectory(String appStorageRoot) {
        if (StringUtils.hasText(appStorageRoot)) {
            return Paths.get(appStorageRoot).toAbsolutePath().normalize();
        }
        return Paths.get(".local-data", "files", "photos").toAbsolutePath().normalize();
    }

    @PostMapping("/upload")
    @RequireRoles({"GRID_WORKER", "CARE_WORKER", "COMMUNITY_ADMIN"})
    public Result<Map<String, String>> uploadPhoto(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "workOrderId", required = false) Long workOrderId) {
        CurrentUser current = UserContext.requireCurrentUser();
        if ("CARE_WORKER".equalsIgnoreCase(current.getCurrentRole())) {
            if (workOrderId == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "护工上传现场照片必须关联本人承接的工单");
            }
            workOrderService.assertCurrentCareWorkerMayUpload(workOrderId);
        }

        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "上传的文件不能为空");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "上传照片大小不得超过 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (StringUtils.hasText(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "仅支持 jpg、jpeg、png、webp 格式的现场照片");
        }

        String safeFileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path targetPath = this.storageDirectory.resolve(safeFileName).normalize();

        if (!targetPath.startsWith(this.storageDirectory)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "非法的文件路径");
        }

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "现场照片凭据保存失败: " + e.getMessage());
        }

        String viewUrl = "/api/v1/files/view/" + safeFileName;
        Map<String, String> response = new HashMap<>();
        response.put("url", viewUrl);
        response.put("filename", safeFileName);
        response.put("originalName", originalFilename);

        return Result.success(response);
    }

    @GetMapping("/view/{filename:.+}")
    public ResponseEntity<Resource> viewPhoto(@PathVariable String filename) {
        if (!StringUtils.hasText(filename) || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        Path filePath = this.storageDirectory.resolve(filename).normalize();
        if (!filePath.startsWith(this.storageDirectory) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
