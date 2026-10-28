package cn.communityelderly.modules.elder;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.ObjectAuthorizationPolicy;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.elder.entity.ElderProfile;
import cn.communityelderly.modules.elder.mapper.ElderProfileMapper;
import cn.communityelderly.modules.elder.service.ElderArchiveService;
import cn.communityelderly.modules.emergency.entity.WorkOrder;
import cn.communityelderly.modules.emergency.mapper.WorkOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class ElderDeactivationCurrentReadIntegrationTest {

    @Autowired
    private ElderArchiveService elderArchiveService;

    @Autowired
    private ObjectAuthorizationPolicy authorizationPolicy;

    @Autowired
    private ElderProfileMapper elderProfileMapper;

    @Autowired
    private WorkOrderMapper workOrderMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long elderId;
    private Long workOrderId;

    @BeforeEach
    void createElder() {
        ElderProfile elder = new ElderProfile();
        elder.setArchiveNo("EDRACE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        elder.setCommunityId(1L);
        elder.setName("停档并发快照测试老人");
        elder.setSex("UNKNOWN");
        elder.setAddress("停档并发快照测试地址");
        elder.setStatus("ACTIVE");
        elder.setRowVersion(1L);
        elder.setCreatedBy(1L);
        elder.setUpdatedBy(1L);
        assertEquals(1, elderProfileMapper.insert(elder));
        elderId = elder.getElderId();
    }

    @AfterEach
    void cleanUp() {
        UserContext.clear();
        if (workOrderId != null) {
            workOrderMapper.deleteById(workOrderId);
        }
        if (elderId != null) {
            elderProfileMapper.deleteById(elderId);
        }
    }

    @Test
    void deactivationMustSeeOrderCommittedAfterAuthorizationSnapshot() throws Exception {
        CountDownLatch writerLockedElder = new CountDownLatch(1);
        CountDownLatch archiveSnapshotEstablished = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate writerTransaction = new TransactionTemplate(transactionManager);
        TransactionTemplate archiveTransaction = new TransactionTemplate(transactionManager);
        archiveTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        try {
            Future<?> writer = executor.submit(() -> {
                writerTransaction.executeWithoutResult(status -> {
                    assertNotNull(elderProfileMapper.selectByIdForUpdate(elderId));
                    writerLockedElder.countDown();
                    await(archiveSnapshotEstablished, "停档事务应先建立授权读取快照");
                    WorkOrder order = createOpenOrder(elderId);
                    assertEquals(1, workOrderMapper.insert(order));
                    workOrderId = order.getWorkOrderId();
                });
                writerCommitted.countDown();
            });

            Future<Throwable> archive = executor.submit(() -> {
                UserContext.set(communityAdmin());
                try {
                    await(writerLockedElder, "建单事务应先锁定老人档案");
                    return capture(() -> archiveTransaction.executeWithoutResult(status -> {
                        authorizationPolicy.requireElderInCurrentCommunity(elderId, "COMMUNITY_ADMIN");
                        archiveSnapshotEstablished.countDown();
                        await(writerCommitted, "新工单事务应在停档锁档案前提交");
                        elderArchiveService.updateElderStatus(elderId, "INACTIVE", "并发停档快照回归");
                    }));
                } finally {
                    UserContext.clear();
                }
            });

            writer.get(20, TimeUnit.SECONDS);
            Throwable failure = archive.get(20, TimeUnit.SECONDS);
            assertInstanceOf(BusinessException.class, failure,
                    "停档事务必须读取到授权快照建立后提交的未结工单");
            assertEquals(ErrorCode.ORDER_STATUS_INVALID, ((BusinessException) failure).getErrorCode());
            assertNotNull(workOrderMapper.selectById(workOrderId));
            assertEquals("ACTIVE", elderProfileMapper.selectById(elderId).getStatus());
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private WorkOrder createOpenOrder(Long targetElderId) {
        WorkOrder order = new WorkOrder();
        order.setOrderNo("EDRACE-WO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
        order.setSourceType("MANUAL");
        order.setSourceReferenceId("elder-race-" + UUID.randomUUID());
        order.setElderId(targetElderId);
        order.setCommunityId(1L);
        order.setServiceType("DAILY_CARE");
        order.setOrderTitle("停档并发快照测试工单");
        order.setOrderDescription("在停档授权快照后提交的未结工单");
        order.setUrgencyLevel("NORMAL");
        order.setStatus("PENDING_DISPATCH");
        order.setResultPhotoRequired(false);
        order.setRowVersion(1L);
        return order;
    }

    private CurrentUser communityAdmin() {
        return new CurrentUser("1", "13800138000", "STAFF", List.of("COMMUNITY_ADMIN"),
                "COMMUNITY_ADMIN", "1");
    }

    private void await(CountDownLatch latch, String message) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }

    private Throwable capture(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }
}
