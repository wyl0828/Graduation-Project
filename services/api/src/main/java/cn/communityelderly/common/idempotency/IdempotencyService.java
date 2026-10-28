package cn.communityelderly.common.idempotency;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class IdempotencyService {
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";

    private final IdempotencyRecordMapper mapper;

    public IdempotencyService(IdempotencyRecordMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(String principalScope,
                               String operationScope,
                               String idempotencyKey,
                               String requestHash,
                               String traceId,
                               int expireSeconds) {
        String ownerToken = UUID.randomUUID().toString();
        int inserted = mapper.insertProcessing(principalScope, operationScope, idempotencyKey, requestHash,
                traceId, ownerToken, expireSeconds);

        IdempotencyRecord record = mapper.selectForUpdate(principalScope, operationScope, idempotencyKey);
        if (record == null) {
            throw new IllegalStateException("幂等占位写入后无法读取记录");
        }

        if (inserted == 1) {
            return Reservation.owned(record.getRecordId(), ownerToken);
        }

        if (Boolean.TRUE.equals(record.getExpired())) {
            int takenOver = mapper.takeOverExpired(record.getRecordId(), requestHash,
                    traceId, ownerToken, expireSeconds);
            if (takenOver == 1) {
                return Reservation.owned(record.getRecordId(), ownerToken);
            }
            throw new IllegalStateException("幂等过期记录未能原子接管");
        }

        if (!requestHash.equals(record.getRequestHash())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                    "幂等键已被使用且请求方法、URI、查询参数或JSON正文不同");
        }

        if (COMPLETED.equals(record.getRecordState())) {
            if (record.getResponseStatus() == null || !StringUtils.hasText(record.getResponseBody())) {
                throw new IllegalStateException("已完成的幂等记录缺少可重放响应");
            }
            return Reservation.replay(record.getRecordId(), record.getResponseStatus(),
                    record.getResponseBody(), record.getTraceId());
        }

        if (PROCESSING.equals(record.getRecordState())) {
            throw new BusinessException(ErrorCode.DUPLICATE_OPERATION,
                    "相同请求仍在处理中，请稍后重试");
        }

        throw new IllegalStateException("幂等记录状态无效");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Reservation reservation, int responseStatus, String responseBody, String traceId) {
        if (!reservation.owned()) {
            throw new IllegalArgumentException("只有占位记录的所有者可以保存响应");
        }
        int updated = mapper.complete(reservation.recordId(), reservation.ownerToken(),
                responseStatus, responseBody, traceId);
        if (updated != 1) {
            throw new IllegalStateException("幂等占位记录已被接管或无法完成");
        }
    }

    public record Reservation(boolean owned,
                              long recordId,
                              String ownerToken,
                              Integer responseStatus,
                              String responseBody,
                              String traceId) {
        static Reservation owned(long recordId, String ownerToken) {
            return new Reservation(true, recordId, ownerToken, null, null, null);
        }

        static Reservation replay(long recordId, int status, String body, String traceId) {
            return new Reservation(false, recordId, null, status, body, traceId);
        }
    }
}
