package cn.communityelderly.modules.order;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.order.controller.FileUploadController;
import cn.communityelderly.modules.order.service.WorkOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FileUploadControllerTest {

    @AfterEach
    void clearCurrentUser() {
        UserContext.clear();
    }

    @Test
    @DisplayName("护工上传现场照片前先验证工单承接授权且拒绝后不落盘")
    void careWorkerUploadRejectsUnassignedOrderBeforeSavingFile(@TempDir Path appStorageRoot) throws IOException {
        WorkOrderService service = mock(WorkOrderService.class);
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE))
                .when(service).assertCurrentMayUploadEvidence(201L);
        FileUploadController controller = controller(service, appStorageRoot);
        UserContext.set(user("77", "CARE_WORKER", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.uploadPhoto(photo(), 201L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(service).assertCurrentMayUploadEvidence(201L);
        assertNoStoredFiles(appStorageRoot);
    }

    @Test
    @DisplayName("护工缺少工单编号时在调用授权服务与保存文件前被拒绝")
    void careWorkerUploadRequiresWorkOrderIdBeforeSavingFile(@TempDir Path appStorageRoot) throws IOException {
        WorkOrderService service = mock(WorkOrderService.class);
        FileUploadController controller = controller(service, appStorageRoot);
        UserContext.set(user("77", "CARE_WORKER", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.uploadPhoto(photo(), null));

        assertEquals(ErrorCode.VALIDATION_FAILED, error.getErrorCode());
        verify(service, never()).assertCurrentMayUploadEvidence(any());
        assertNoStoredFiles(appStorageRoot);
    }

    @Test
    @DisplayName("原有社区管理员上传继续兼容且照片写入配置的存储根目录")
    void administratorBoundUploadUsesConfiguredAppStorageRoot(@TempDir Path appStorageRoot) throws IOException {
        WorkOrderService service = mock(WorkOrderService.class);
        FileUploadController controller = controller(service, appStorageRoot);
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));

        assertDoesNotThrow(() -> controller.uploadPhoto(validPhoto(), 201L));
        verify(service).assertCurrentMayUploadEvidence(201L);

        assertEquals(1L, countStoredFiles(appStorageRoot));
    }

    @Test
    void rejectsForgedJpegContent(@TempDir Path storage) throws IOException {
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));
        FileUploadController controller = controller(mock(WorkOrderService.class), storage);
        assertThrows(BusinessException.class, () -> controller.uploadPhoto(photo(), 201L));
        assertNoStoredFiles(storage);
    }

    @Test
    void anonymousCannotReadEvenKnownPhotoName(@TempDir Path storage) throws IOException {
        Files.write(storage.resolve("known.jpg"), new byte[]{1, 2, 3});
        FileUploadController controller = controller(mock(WorkOrderService.class), storage);
        assertThrows(RuntimeException.class, () -> controller.viewPhoto("known.jpg"));
    }

    @Test
    void administratorMustBindUploadToWorkOrder(@TempDir Path storage) {
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));
        FileUploadController controller = controller(mock(WorkOrderService.class), storage);
        assertThrows(BusinessException.class, () -> controller.uploadPhoto(photo(), null));
    }

    private FileUploadController controller(WorkOrderService orders, Path root) {
        var repo = mock(cn.communityelderly.modules.order.service.FileEvidenceRepository.class);
        org.mockito.Mockito.when(repo.create(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(), any())).thenReturn(11L);
        var service = new cn.communityelderly.modules.order.service.FileEvidenceService(repo,
                mock(cn.communityelderly.modules.order.service.FileEvidenceRejectionAudit.class),
                mock(cn.communityelderly.modules.audit.service.AuditLogService.class), new com.fasterxml.jackson.databind.ObjectMapper(), root.toString());
        return new FileUploadController(orders, service);
    }

    private MockMultipartFile validPhoto() throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        return new MockMultipartFile("file", "evidence.jpg", "image/jpeg", bytes.toByteArray());
    }

    private void assertNoStoredFiles(Path appStorageRoot) throws IOException {
        assertEquals(0L, Files.exists(appStorageRoot) ? countStoredFiles(appStorageRoot) : 0L);
    }

    private long countStoredFiles(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }

    private MockMultipartFile photo() {
        return new MockMultipartFile("file", "evidence.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private CurrentUser user(String userId, String role, String communityId) {
        return new CurrentUser(userId, "13900000000", "STAFF", List.of(role), role, communityId);
    }

}
