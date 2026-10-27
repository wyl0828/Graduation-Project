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
                .when(service).assertCurrentCareWorkerMayUpload(201L);
        FileUploadController controller = new FileUploadController(service, appStorageRoot.toString());
        UserContext.set(user("77", "CARE_WORKER", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.uploadPhoto(photo(), 201L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND_OR_NOT_ACCESSIBLE, error.getErrorCode());
        verify(service).assertCurrentCareWorkerMayUpload(201L);
        assertNoStoredFiles(appStorageRoot);
    }

    @Test
    @DisplayName("护工缺少工单编号时在调用授权服务与保存文件前被拒绝")
    void careWorkerUploadRequiresWorkOrderIdBeforeSavingFile(@TempDir Path appStorageRoot) throws IOException {
        WorkOrderService service = mock(WorkOrderService.class);
        FileUploadController controller = new FileUploadController(service, appStorageRoot.toString());
        UserContext.set(user("77", "CARE_WORKER", "1"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.uploadPhoto(photo(), null));

        assertEquals(ErrorCode.VALIDATION_FAILED, error.getErrorCode());
        verify(service, never()).assertCurrentCareWorkerMayUpload(any());
        assertNoStoredFiles(appStorageRoot);
    }

    @Test
    @DisplayName("原有社区管理员上传继续兼容且照片写入配置的存储根目录")
    void administratorUploadUsesConfiguredAppStorageRoot(@TempDir Path appStorageRoot) throws IOException {
        WorkOrderService service = mock(WorkOrderService.class);
        FileUploadController controller = new FileUploadController(service, appStorageRoot.toString());
        UserContext.set(user("10", "COMMUNITY_ADMIN", "1"));

        assertDoesNotThrow(() -> controller.uploadPhoto(photo(), null));
        verify(service, never()).assertCurrentCareWorkerMayUpload(any());

        assertEquals(1L, countStoredFiles(appStorageRoot));
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
