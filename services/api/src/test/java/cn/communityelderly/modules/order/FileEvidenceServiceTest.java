package cn.communityelderly.modules.order;

import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.audit.service.AuditLogService;
import cn.communityelderly.modules.order.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileEvidenceServiceTest {
    @TempDir Path root;
    FileEvidenceRepository repo;
    FileEvidenceService service;
    @BeforeEach void setUp() {
        repo = mock(FileEvidenceRepository.class);
        service = new FileEvidenceService(repo, mock(FileEvidenceRejectionAudit.class), mock(AuditLogService.class), new ObjectMapper(), root.toString());
        UserContext.set(new CurrentUser("2", "13800138002", "STAFF", List.of("GRID_WORKER"), "GRID_WORKER", "1"));
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void crossOrderReuseIsRejectedBeforeLinkWrites() throws Exception {
        var evidence = stored(11L, 201L);
        when(repo.find(11L)).thenReturn(evidence);
        assertThrows(BusinessException.class, () -> service.link(202L, List.of(11L)));
        verify(repo, never()).link(any(), any());
    }
    @Test void missingPhysicalFileIsRejected() throws Exception {
        var evidence = stored(11L, 201L);
        Files.delete(root.resolve(evidence.filename()));
        when(repo.find(11L)).thenReturn(evidence);
        assertThrows(BusinessException.class, () -> service.validate(201L, List.of(11L), false));
    }
    @Test void modifiedContentCannotPassRecordedDigest() throws Exception {
        var evidence = stored(11L, 201L);
        Files.write(root.resolve(evidence.filename()), new byte[]{3, 2, 1});
        when(repo.find(11L)).thenReturn(evidence);
        assertThrows(BusinessException.class, () -> service.validate(201L, List.of(11L), false));
    }
    @Test void sixPhotosAndDuplicatesAreRejected() {
        assertThrows(BusinessException.class, () -> service.validate(201L, List.of(1L,2L,3L,4L,5L,6L), false));
        assertThrows(BusinessException.class, () -> service.validate(201L, List.of(1L,1L), false));
        verifyNoInteractions(repo);
    }
    @Test void validUploadPersistsSizeMimeHashAndUploaderWithoutEvidenceLink() throws Exception {
        byte[] bytes = jpeg();
        when(repo.create(any(), eq(2L), any(), eq("photo.jpg"), eq("image/jpeg"), eq((long)bytes.length), any(), eq(201L))).thenReturn(11L);
        var result = service.upload(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes), 201L);
        assertNotNull(result.get("assetUuid"));
        verify(repo).create(any(), eq(2L), any(), eq("photo.jpg"), eq("image/jpeg"), eq((long)bytes.length),
                eq(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))), eq(201L));
        verify(repo, never()).link(any(), any());
        try (var files = Files.list(root)) { assertEquals(1L, files.count()); }
    }
    @Test void mimeAndActualFormatMustAgreeWithExtension() throws Exception {
        assertThrows(BusinessException.class, () -> service.upload(new MockMultipartFile("file", "photo.png", "image/png", jpeg()), 201L));
        assertThrows(BusinessException.class, () -> service.upload(new MockMultipartFile("file", "photo.jpg", "image/png", jpeg()), 201L));
        assertThrows(BusinessException.class, () -> service.upload(new MockMultipartFile("file", "photo.webp", "image/webp", jpeg()), 201L));
        verifyNoInteractions(repo);
    }
    @Test void oversizedUploadRejectedBeforePersistence() {
        assertThrows(BusinessException.class, () -> service.upload(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[(int)FileEvidenceService.MAX_BYTES+1]), 201L));
        verifyNoInteractions(repo);
    }
    @Test void unlinkedUploadCannotBeApprovedAsEvidence() throws Exception {
        when(repo.find(11L)).thenReturn(stored(11L, 201L));
        assertThrows(BusinessException.class, () -> service.validate(201L, List.of(11L), true));
    }
    @Test void exactlyFiveVerifiedLinkedVersionsAreAccepted() throws Exception {
        for (long id=1;id<=5;id++) {
            when(repo.find(id)).thenReturn(stored(id, 201L));
            when(repo.linked(201L,id)).thenReturn(true);
        }
        assertEquals(5, service.validate(201L, List.of(1L,2L,3L,4L,5L), true).size());
    }
    @Test void childCannotReadAnUnsubmittedUploadEvenIfOrderIsClosed() throws Exception {
        var evidence=stored(11L,201L);
        UserContext.set(new CurrentUser("4", "13912345678", "FAMILY", List.of("CHILD"), "CHILD", "1"));
        assertThrows(BusinessException.class, () -> service.requireVisibleVersion(evidence));
    }
    @Test void legacyPhotoRequiresExactUniqueOrderLinkAndUsesOldPhotosDirectory() throws Exception {
        Files.createDirectory(root.resolve("photos"));
        Files.write(root.resolve("photos/legacy.jpg"),new byte[]{1,2,3});
        when(repo.legacyWorkOrderIds("legacy.jpg")).thenReturn(List.of(201L));
        var orders=mock(WorkOrderService.class);
        var controller=new cn.communityelderly.modules.order.controller.FileUploadController(orders,service);
        var response=controller.viewPhoto("legacy.jpg");
        assertEquals(200,response.getStatusCode().value());
        assertEquals("private, no-store",response.getHeaders().getCacheControl());
        verify(orders).assertCurrentMayReadEvidence(201L);
        verify(repo,never()).create(any(),any(),any(),any(),any(),anyLong(),any(),any());
    }
    @Test void legacyPhotoIsRejectedWhenNoExactBusinessUrlOrAmbiguousOwnerExists() {
        when(repo.legacyWorkOrderIds("legacy.jpg")).thenReturn(List.of(),List.of(201L,202L));
        assertThrows(BusinessException.class,()->service.legacyWorkOrderId("legacy.jpg"));
        assertThrows(BusinessException.class,()->service.legacyWorkOrderId("legacy.jpg"));
        assertThrows(BusinessException.class,()->service.legacyWorkOrderId("../legacy.jpg"));
    }
    @Test void assetUuidIsResolvedServerSideAndCannotBeReplayedForAnotherOrder() throws Exception {
        var stored=stored(11L,201L);
        when(repo.findByAssetUuid(stored.assetUuid())).thenReturn(stored);
        assertThrows(BusinessException.class,()->service.resolveAndLink(202L,List.of(stored.assetUuid())));
        verify(repo,never()).link(any(),any());
    }

    private FileEvidenceRepository.Evidence stored(Long versionId, Long orderId) throws Exception {
        String filename = "stored-"+versionId+".jpg";
        byte[] bytes = {1,2,3};
        Files.write(root.resolve(filename), bytes);
        return new FileEvidenceRepository.Evidence(versionId,versionId,orderId,2L,filename,"image/jpeg",bytes.length,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), UUID.randomUUID().toString());
    }
    private byte[] jpeg() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB), "jpeg", output);
        return output.toByteArray();
    }
}
