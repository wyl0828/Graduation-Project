package cn.communityelderly.modules.reservation;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.common.security.CurrentUser;
import cn.communityelderly.common.security.UserContext;
import cn.communityelderly.modules.reservation.dto.ReservationCreateDTO;
import cn.communityelderly.modules.reservation.service.ServiceReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class ReservationSlotReuseIntegrationTest {

    private static final Long CHILD_ID = 4L;
    private static final Long ELDER_ID = 1L;
    private static final String SERVICE_TYPE = "CLEANING";

    @Autowired
    private ServiceReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @Transactional
    @DisplayName("REJECTED、CANCELLED 和 SERVICE_STOPPED 预约释放时段后允许同槽重约")
    void releasedTerminalStatusesAllowAnotherReservationForSameSlot() {
        UserContext.set(childUser());
        ReservationCreateDTO request = requestForDate(uniqueFutureDate());

        Long reservationId = reservationService.createReservation(request, CHILD_ID);
        for (String releasedStatus : List.of("REJECTED", "CANCELLED", "SERVICE_STOPPED")) {
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE service_reservation SET status = ? WHERE reservation_id = ?",
                    releasedStatus, reservationId));
            reservationId = reservationService.createReservation(request, CHILD_ID);
        }

        Long sameSlotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_reservation WHERE elder_id = ? AND service_type = ? "
                        + "AND service_date = ? AND slot_code = ?",
                Long.class, ELDER_ID, SERVICE_TYPE, request.getServiceDate(), request.getSlotCode());
        assertEquals(4L, sameSlotCount);
    }

    @Test
    @DisplayName("移除永久时段唯一键并保留有效预约的部分唯一约束")
    void schemaUsesNonUniqueLookupIndexForReservationSlots() {
        Long permanentUniqueIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_reservation' "
                        + "AND index_name = 'uk_elder_service_slot' "
                        + "AND non_unique = 0",
                Long.class);
        Long activeUniqueIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_reservation' "
                        + "AND index_name = 'uk_active_elder_service_slot' AND non_unique = 0",
                Long.class);
        Long activeSlotKeyColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_reservation' "
                        + "AND column_name = 'active_slot_key'",
                Long.class);
        Integer lookupIndexNonUnique = jdbcTemplate.queryForObject(
                "SELECT MAX(non_unique) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'service_reservation' "
                        + "AND index_name = 'idx_elder_service_slot'",
                Integer.class);

        assertEquals(0L, permanentUniqueIndexes);
        assertEquals(1L, activeUniqueIndexes);
        assertEquals(1L, activeSlotKeyColumns);
        assertNotNull(lookupIndexNonUnique);
        assertEquals(1, lookupIndexNonUnique);
    }

    @Test
    @DisplayName("RR 事务先读旧快照时，第二个同槽预约仍通过当前读发现已提交预约")
    void secondTransactionRejectsSlotCreatedAfterItsRepeatableReadSnapshot() throws Exception {
        ReservationCreateDTO request = requestForDate(uniqueFutureDate());
        CountDownLatch oldSnapshotReady = new CountDownLatch(1);
        CountDownLatch firstCreateCommitted = new CountDownLatch(1);
        AtomicReference<Long> secondCreatedId = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Long firstCreatedId = null;

        try {
            Future<Throwable> secondAttempt = executor.submit(() -> {
                UserContext.set(childUser());
                TransactionTemplate repeatableRead = new TransactionTemplate(transactionManager);
                repeatableRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                try {
                    repeatableRead.execute(status -> {
                        Long initialCount = jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM service_reservation WHERE elder_id = ? AND service_type = ? "
                                        + "AND service_date = ? AND slot_code = ? "
                                        + "AND status NOT IN ('REJECTED', 'CANCELLED', 'SERVICE_STOPPED')",
                                Long.class, ELDER_ID, SERVICE_TYPE, request.getServiceDate(), request.getSlotCode());
                        assertEquals(0L, initialCount);
                        oldSnapshotReady.countDown();
                        await(firstCreateCommitted);
                        secondCreatedId.set(reservationService.createReservation(request, CHILD_ID));
                        return null;
                    });
                    return null;
                } catch (Throwable error) {
                    return error;
                } finally {
                    UserContext.clear();
                }
            });

            assertTrue(oldSnapshotReady.await(10, TimeUnit.SECONDS), "第二个事务应先建立空槽快照");
            UserContext.set(childUser());
            firstCreatedId = reservationService.createReservation(request, CHILD_ID);
            assertTrue(firstCreatedId > 0);
            UserContext.clear();
            firstCreateCommitted.countDown();

            Throwable secondError = secondAttempt.get(15, TimeUnit.SECONDS);
            assertTrue(secondError instanceof BusinessException,
                    "第二个事务应以业务冲突结束，实际结果为 " + secondError);
            assertEquals(ErrorCode.RESERVATION_SLOT_CONFLICT,
                    ((BusinessException) secondError).getErrorCode());
            assertEquals(1L, countActiveReservations(request));
            assertNull(secondCreatedId.get());
        } finally {
            firstCreateCommitted.countDown();
            executor.shutdownNow();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            jdbcTemplate.update("DELETE FROM service_reservation WHERE elder_id = ? AND applicant_user_id = ? "
                            + "AND service_type = ? AND service_date = ? AND slot_code = ?",
                    ELDER_ID, CHILD_ID, SERVICE_TYPE, request.getServiceDate(), request.getSlotCode());
            UserContext.clear();
        }
    }

    private long countActiveReservations(ReservationCreateDTO request) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service_reservation WHERE elder_id = ? AND service_type = ? "
                        + "AND service_date = ? AND slot_code = ? "
                        + "AND status NOT IN ('REJECTED', 'CANCELLED', 'SERVICE_STOPPED')",
                Long.class, ELDER_ID, SERVICE_TYPE, request.getServiceDate(), request.getSlotCode());
    }

    private LocalDate uniqueFutureDate() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(20 + Math.floorMod(UUID.randomUUID().hashCode(), 300));
    }

    private ReservationCreateDTO requestForDate(LocalDate serviceDate) {
        ReservationCreateDTO request = new ReservationCreateDTO();
        request.setElderId(ELDER_ID);
        request.setServiceType(SERVICE_TYPE);
        request.setServiceDate(serviceDate);
        request.setSlotCode("MORNING");
        request.setServiceAddress("集成测试地址");
        request.setSpecialNotes("预约时段复用集成测试");
        return request;
    }

    private CurrentUser childUser() {
        return new CurrentUser("4", "13912345678", "FAMILY", List.of("CHILD"), "CHILD", "1");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待首个预约提交超时");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待首个预约提交时线程中断", exception);
        }
    }
}
