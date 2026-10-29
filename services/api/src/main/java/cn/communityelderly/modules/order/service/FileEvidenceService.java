package cn.communityelderly.modules.order.service;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class FileEvidenceService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private final FileEvidenceRepository repository;
    private final FileEvidenceRejectionAudit rejectionAudit;
    private final AuditLogService audit;
    private final ObjectMapper json;
    private final Path root;

    public FileEvidenceService(FileEvidenceRepository repository, FileEvidenceRejectionAudit rejectionAudit,
                               AuditLogService audit, ObjectMapper json,
                               @Value("${app.storage.root:.local-data/files}") String storageRoot) {
        this.repository = repository;
        this.rejectionAudit = rejectionAudit;
        this.audit = audit;
        this.json = json;
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Transactional
    public Map<String, String> upload(MultipartFile file, Long workOrderId) {
        byte[] bytes;
        String original = file.getOriginalFilename();
        String extension = original != null && original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String expectedMime = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> throw invalid("仅支持 JPEG、PNG 现场照片");
        };
        if (workOrderId == null || workOrderId <= 0) throw invalid("上传必须关联工单");
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid("照片不能为空且不得超过 5 MB");
        if (!expectedMime.equals(file.getContentType())) throw invalid("文件扩展名与声明 MIME 类型不一致");
        try {
            bytes = file.getBytes();
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid("照片不能为空且不得超过 5 MB");
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("文件实际内容不是有效的 JPEG/PNG 图片");
                ImageReader reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!("image/jpeg".equals(expectedMime) && "JPEG".equalsIgnoreCase(format))
                            && !("image/png".equals(expectedMime) && "PNG".equalsIgnoreCase(format)))
                        throw invalid("文件扩展名与实际内容不一致");
                    reader.setInput(input, true, true);
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels <= 0 || pixels > 40_000_000L) throw invalid("图片像素尺寸超过安全范围");
                    if (reader.read(0) == null) throw invalid("图片内容无法完整解析");
                } finally { reader.dispose(); }
            }
            String uuid = UUID.randomUUID().toString();
            String filename = uuid + ("image/png".equals(expectedMime) ? ".png" : ".jpg");
            Path path = controlledPath(filename);
            Files.createDirectories(root);
            Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) deleteUncommitted(path);
                    }
                });
            }
            try {
                Long versionId = repository.create(uuid, actorId(), filename, safeOriginal(original), expectedMime,
                        bytes.length, hash(bytes), workOrderId);
                success(workOrderId, "FILE_UPLOAD");
                return Map.of("url", "/api/v1/files/view/" + filename, "filename", filename,
                        "originalName", safeOriginal(original), "assetUuid", uuid);
            } catch (RuntimeException error) {
                deleteUncommitted(path);
                throw error;
            }
        } catch (IOException error) {
            throw invalid("现场照片内容无效或存储失败");
        }
    }

    private String safeOriginal(String original) {
        String value = original == null ? "photo" : original.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "");
        return value.substring(0, Math.min(value.length(), 128));
    }

    private void deleteUncommitted(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Uncommitted upload cleanup failed: {}", path);
        }
    }

    public FileEvidenceRepository.Evidence findMetadata(String filename) {
        UserContext.requireCurrentUser();
        if (filename == null || !filename.matches("[a-zA-Z0-9-]+\\.(?:jpg|jpeg|png|webp)")) throw inaccessible();
        return repository.findByFilename(filename);
    }

    public Long legacyWorkOrderId(String filename) {
        findMetadata(filename); // applies the same strict filename boundary
        List<Long> orderIds = repository.legacyWorkOrderIds(filename);
        if (orderIds == null || orderIds.size() != 1) throw inaccessible();
        return orderIds.get(0);
    }

    // Legacy bytes have no verified digest. They remain historical display only.
    public Resource legacyResource(String filename) {
        findMetadata(filename);
        for (Path candidate : List.of(root.resolve(filename), root.resolve("photos").resolve(filename))) {
            Path normalized = candidate.normalize();
            if (normalized.startsWith(root) && Files.isRegularFile(normalized, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                try {
                    if (!normalized.toRealPath().startsWith(root.toRealPath())) throw inaccessible();
                    return new UrlResource(normalized.toUri());
                } catch (IOException error) { throw inaccessible(); }
            }
        }
        throw inaccessible();
    }

    public String legacyMime(String filename) {
        return filename.endsWith(".png") ? "image/png" : filename.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }

    public FileEvidenceRepository.Evidence requireMetadata(String filename) {
        UserContext.requireCurrentUser();
        controlledPath(filename);
        FileEvidenceRepository.Evidence evidence = repository.findByFilename(filename);
        if (evidence == null) throw inaccessible();
        return evidence;
    }

    public void requireVisibleVersion(FileEvidenceRepository.Evidence evidence) {
        String role = UserContext.requireCurrentUser().getCurrentRole();
        if (("ELDER".equals(role) || "CHILD".equals(role))
                && (!repository.linked(evidence.workOrderId(), evidence.versionId())
                    || !currentVersions(evidence.workOrderId()).contains(evidence.versionId()))) throw inaccessible();
    }

    public Resource resource(FileEvidenceRepository.Evidence evidence) {
        verifyStoredFile(evidence);
        try { return new UrlResource(controlledPath(evidence.filename()).toUri()); }
        catch (IOException exception) { throw inaccessible(); }
    }

    public List<Long> validate(Long workOrderId, List<Long> versions, boolean mustBeLinked) {
        if (versions == null || versions.isEmpty() || versions.size() > 5
                || versions.stream().anyMatch(Objects::isNull) || new HashSet<>(versions).size() != versions.size())
            throw invalid("每次服务结果必须引用 1 至 5 张已校验照片，且不能重复");
        for (Long versionId : versions.stream().sorted().toList()) {
            FileEvidenceRepository.Evidence evidence = repository.find(versionId);
            if (evidence == null || !Objects.equals(workOrderId, evidence.workOrderId())) throw inaccessible();
            if (mustBeLinked && !repository.linked(workOrderId, versionId)) throw inaccessible();
            verifyStoredFile(evidence);
        }
        return List.copyOf(versions);
    }

    public List<Long> resolveAndLink(Long workOrderId, List<String> assetUuids) {
        if (assetUuids == null || assetUuids.isEmpty() || assetUuids.size() > 5
                || new HashSet<>(assetUuids).size() != assetUuids.size()) throw invalid("每次结果需要 1 至 5 个不同的已校验文件资产");
        List<Long> versions = new ArrayList<>();
        for (String uuid : assetUuids) {
            try {
                if (uuid == null || !UUID.fromString(uuid).toString().equalsIgnoreCase(uuid)) throw inaccessible();
            } catch (IllegalArgumentException error) { throw inaccessible(); }
            var file = repository.findByAssetUuid(uuid);
            if (file == null || !Objects.equals(file.workOrderId(), workOrderId)) throw inaccessible();
            versions.add(file.versionId());
        }
        link(workOrderId, versions);
        return versions;
    }

    public List<String> assetUuids(Long workOrderId, List<Long> versions) {
        List<String> uuids = new ArrayList<>();
        for (Long version : versions) {
            var file = repository.find(version);
            if (file != null && Objects.equals(file.workOrderId(), workOrderId) && repository.linked(workOrderId, version)) uuids.add(file.assetUuid());
        }
        return uuids;
    }

    public void link(Long workOrderId, List<Long> versions) {
        validate(workOrderId, versions, false);
        for (Long version : versions.stream().sorted().toList()) repository.link(repository.find(version), workOrderId);
        success(workOrderId, "FILE_EVIDENCE_LINK");
    }

    public List<Long> currentVersions(Long workOrderId) {
        String payload = repository.latestSubmission(workOrderId);
        if (payload == null) return List.of();
        try {
            var node = json.readTree(payload).get("evidenceVersionIds");
            if (node == null || !node.isArray()) return List.of();
            List<Long> result = new ArrayList<>();
            for (var item : node) {
                if (!item.canConvertToLong()) throw invalid("照片版本历史无效");
                result.add(item.longValue());
            }
            return result;
        } catch (IOException exception) { throw invalid("照片版本历史无效"); }
    }

    public List<String> urls(Long workOrderId, List<Long> versions) {
        List<String> urls = new ArrayList<>();
        for (Long id : versions) {
            var evidence = repository.find(id);
            if (evidence != null && Objects.equals(evidence.workOrderId(), workOrderId) && repository.linked(workOrderId, id))
                urls.add("/api/v1/files/view/" + evidence.filename());
        }
        return urls;
    }

    public void readSucceeded(Long workOrderId) { success(workOrderId, "FILE_READ"); }

    public void rejected(Long workOrderId, String action, ErrorCode errorCode) {
        rejectionAudit.record(workOrderId, action, errorCode);
    }

    private void verifyStoredFile(FileEvidenceRepository.Evidence evidence) {
        try {
            Path path = controlledPath(evidence.filename());
            if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(path)
                    || Files.size(path) != evidence.size() || evidence.size() > MAX_BYTES
                    || !hash(Files.readAllBytes(path)).equals(evidence.sha256()))
                throw invalid("照片凭据缺失或摘要不一致，请重新上传");
        } catch (IOException exception) { throw invalid("照片凭据不可读取，请重新上传"); }
    }

    private Path controlledPath(String filename) {
        if (filename == null || !filename.matches("[a-zA-Z0-9-]+\\.(?:jpg|png)")) throw inaccessible();
        Path path = root.resolve(filename).normalize();
        if (!path.startsWith(root)) throw inaccessible();
        return path;
    }

    private String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private Long actorId() { return Long.valueOf(UserContext.requireCurrentUser().getUserId()); }
    private void success(Long workOrderId, String action) {
        CurrentUser user = UserContext.requireCurrentUser();
        audit.recordAudit(actorId(), user.getCurrentRole(), user.getCommunityId() == null ? null : Long.valueOf(user.getCommunityId()),
                "WORK_ORDER", String.valueOf(workOrderId), action, "SUCCESS", null, "{}");
    }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.VALIDATION_FAILED, message); }
    private BusinessException inaccessible() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE); }
}
