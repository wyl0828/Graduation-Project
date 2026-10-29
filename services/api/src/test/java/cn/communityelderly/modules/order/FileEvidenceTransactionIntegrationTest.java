package cn.communityelderly.modules.order;

import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import cn.communityelderly.modules.order.controller.FileUploadController;
import cn.communityelderly.modules.order.dto.*;
import cn.communityelderly.modules.order.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class FileEvidenceTransactionIntegrationTest {
    @Autowired WorkOrderMapper orders;
    @Autowired WorkOrderService service;
    @Autowired FileUploadController uploads;
    @Autowired FileEvidenceService evidence;
    @Autowired FileEvidenceRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Value("${app.storage.root:.local-data/files}") String storageRoot;
    final List<Long> createdOrders = new ArrayList<>();
    final List<Path> files = new ArrayList<>();

    @BeforeEach void worker() { UserContext.set(user("2", "GRID_WORKER")); }
    @AfterEach void cleanup() throws Exception {
        UserContext.clear();
        for (Long id : createdOrders) {
            var assetIds = jdbc.queryForList("SELECT asset_id FROM file_upload_session WHERE work_order_id = ?", Long.class,id);
            jdbc.update("DELETE FROM business_file_link WHERE business_type = 'WORK_ORDER' AND business_id = ?", String.valueOf(id));
            jdbc.update("DELETE FROM file_upload_session WHERE work_order_id = ?",id);
            for (Long asset : assetIds) {
                jdbc.update("DELETE FROM file_asset_version WHERE asset_id = ?",asset);
                jdbc.update("DELETE FROM file_asset WHERE asset_id = ?",asset);
            }
            jdbc.update("DELETE FROM work_order_action_history WHERE work_order_id = ?",id);
            jdbc.update("DELETE FROM audit_log WHERE target_type = 'WORK_ORDER' AND target_id = ?",String.valueOf(id));
            jdbc.update("DELETE FROM work_order WHERE work_order_id = ?",id);
        }
        for (Path path:files) Files.deleteIfExists(path);
    }

    @Test void submissionCommitsBusinessLinkStatusAndExactHistoryVersionTogether() throws Exception {
        Long id=createOrder(); Long version=upload(id);
        assertEquals(0, countLinks(id)); // Upload is staging, not evidence.
        service.submitExecutionResult(id, submission(version),2L);
        assertEquals("PENDING_RESULT_REVIEW",orders.selectById(id).getStatus());
        assertEquals(1,countLinks(id));
        assertEquals(List.of(version),evidence.currentVersions(id));
        assertEquals(1,auditCount(id,"FILE_EVIDENCE_LINK","SUCCESS"));
    }

    @Test void rollingBackBusinessStatusAlsoRollsBackLinksHistoryAndSuccessAudit() throws Exception {
        Long id=createOrder(); Long version=upload(id);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.submitExecutionResult(id,submission(version),2L);
            status.setRollbackOnly();
        });
        assertEquals("PROCESSING",orders.selectById(id).getStatus());
        assertEquals(0,countLinks(id));
        assertTrue(evidence.currentVersions(id).isEmpty());
        assertEquals(0,auditCount(id,"FILE_EVIDENCE_LINK","SUCCESS"));
    }

    @Test void rejectedSubmissionAuditSurvivesBusinessRollback() {
        Long id=createOrder();
        var dto=new WorkOrderExecutionDTO();dto.setResultSummary("未经验证的历史地址");
        dto.setPhotoUrls(List.of("/api/v1/files/view/legacy.jpg"));
        BusinessException denied = assertThrows(BusinessException.class,()->service.submitExecutionResult(id,dto,2L));
        assertEquals("PROCESSING",orders.selectById(id).getStatus());
        assertEquals(0,countLinks(id));
        assertEquals(1,auditCount(id,"FILE_EVIDENCE_SUBMIT","DENIED"));
        assertTrue(auditDetails(id,"FILE_EVIDENCE_SUBMIT").contains(denied.getErrorCode().name()));
    }

    @Test void missingPhysicalEvidencePreventsCloseAndKeepsRejectionAudit() throws Exception {
        Long id=createOrder();Long version=upload(id);
        service.submitExecutionResult(id,submission(version),2L);
        Files.delete(Path.of(storageRoot).resolve(repository.find(version).filename()));
        UserContext.set(user("1","COMMUNITY_ADMIN"));
        BusinessException denied = assertThrows(BusinessException.class,()->service.reviewWorkOrder(id,review("APPROVE"),1L));
        assertEquals("PENDING_RESULT_REVIEW",orders.selectById(id).getStatus());
        assertEquals(1,countLinks(id));
        assertEquals(1,auditCount(id,"FILE_EVIDENCE_CLOSE","DENIED"));
        assertTrue(auditDetails(id,"FILE_EVIDENCE_CLOSE").contains(denied.getErrorCode().name()));
    }

    @Test void replacementKeepsOldEvidenceAndPinsOnlyLatestSubmissionForReview() throws Exception {
        Long id=createOrder();Long first=upload(id);
        service.submitExecutionResult(id,submission(first),2L);
        UserContext.set(user("1","COMMUNITY_ADMIN"));
        service.reviewWorkOrder(id,review("REJECT"),1L);
        worker();Long second=upload(id);
        service.submitExecutionResult(id,submission(second),2L);
        assertEquals(2,countLinks(id));
        assertEquals(List.of(second),evidence.currentVersions(id));
        assertTrue(Files.exists(Path.of(storageRoot).resolve(repository.find(first).filename())));
        UserContext.set(user("1","COMMUNITY_ADMIN"));
        service.reviewWorkOrder(id,review("APPROVE"),1L);
        assertEquals("CLOSED",orders.selectById(id).getStatus());
    }

    @Test void successfulAndDeniedReadsHaveAuditsAndFamilyNeedsClosedSubmittedResult() throws Exception {
        Long id=createOrder();Long version=upload(id);
        String filename=repository.find(version).filename();
        assertEquals(200,uploads.viewPhoto(filename).getStatusCode().value());
        UserContext.set(user("1","COMMUNITY_ADMIN"));
        assertEquals(200,uploads.viewPhoto(filename).getStatusCode().value());
        assertEquals(2,auditCount(id,"FILE_READ","SUCCESS"));
        UserContext.set(user("1","SYSTEM_ADMIN"));
        BusinessException denied = assertThrows(BusinessException.class,()->uploads.viewPhoto(filename));
        assertEquals(1,auditCount(id,"FILE_READ","DENIED"));
        assertTrue(auditDetails(id,"FILE_READ").contains(denied.getErrorCode().name()));
        UserContext.set(new CurrentUser("4","13912345678","FAMILY",List.of("CHILD"),"CHILD","1"));
        assertThrows(BusinessException.class,()->uploads.viewPhoto(filename));
        assertEquals(2,auditCount(id,"FILE_READ","DENIED"));
        worker();service.submitExecutionResult(id,submission(version),2L);
        UserContext.set(user("1","COMMUNITY_ADMIN"));service.reviewWorkOrder(id,review("APPROVE"),1L);
        UserContext.set(new CurrentUser("4","13912345678","FAMILY",List.of("CHILD"),"CHILD","1"));
        assertEquals(200,uploads.viewPhoto(filename).getStatusCode().value());
        assertEquals(3,auditCount(id,"FILE_READ","SUCCESS"));
    }

    private Long createOrder() {
        WorkOrder order=new WorkOrder();
        order.setOrderNo("FILE-"+UUID.randomUUID().toString().replace("-", "").substring(0, 24));order.setSourceType("MANUAL");
        order.setSourceReferenceId("FILE-EVIDENCE-TEST");
        order.setElderId(1L);order.setCommunityId(1L);order.setServiceType("DAILY_CARE");
        order.setOrderTitle("文件证据事务测试");order.setUrgencyLevel("NORMAL");
        order.setStatus("PROCESSING");order.setCurrentAssigneeId(2L);
        orders.insert(order);createdOrders.add(order.getWorkOrderId());return order.getWorkOrderId();
    }
    private Long upload(Long id) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB),"jpeg",output);
        var result=uploads.uploadPhoto(new MockMultipartFile("file","proof.jpg","image/jpeg",output.toByteArray()),id).getData();
        Long version=repository.findByAssetUuid(result.get("assetUuid")).versionId();
        files.add(Path.of(storageRoot).resolve(repository.find(version).filename()));
        return version;
    }
    private WorkOrderExecutionDTO submission(Long version) {
        var dto=new WorkOrderExecutionDTO();dto.setResultSummary("上门照护已经完成");dto.setEvidenceAssetUuids(List.of(repository.find(version).assetUuid()));return dto;
    }
    private WorkOrderReviewDTO review(String action) {
        var dto=new WorkOrderReviewDTO();dto.setAction(action);dto.setReviewComment("核验现场实际结果");return dto;
    }
    private int countLinks(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM business_file_link WHERE business_type = 'WORK_ORDER' AND business_id = ?",Integer.class,String.valueOf(id));
    }
    private int auditCount(Long id,String action,String result) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE target_type='WORK_ORDER' AND target_id=? AND action=? AND result=?",Integer.class,String.valueOf(id),action,result);
    }
    private String auditDetails(Long id,String action) {
        return jdbc.queryForObject("SELECT details_json FROM audit_log WHERE target_type='WORK_ORDER' AND target_id=? AND action=? AND result='DENIED' ORDER BY audit_id DESC LIMIT 1",String.class,String.valueOf(id),action);
    }
    private CurrentUser user(String id,String role) {return new CurrentUser(id,"13800138002","STAFF",List.of(role),role,"1");}
}
