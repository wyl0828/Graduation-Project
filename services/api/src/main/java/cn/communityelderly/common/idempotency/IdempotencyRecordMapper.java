package cn.communityelderly.common.idempotency;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

    @Insert("""
            INSERT IGNORE INTO idempotency_record (
                principal_scope, operation_scope, idempotency_key, request_hash,
                record_state, response_status, response_body, trace_id, owner_token,
                created_at, expires_at
            ) VALUES (
                #{principalScope}, #{operationScope}, #{idempotencyKey}, #{requestHash},
                'PROCESSING', NULL, NULL, #{traceId}, #{ownerToken},
                NOW(3), DATE_ADD(NOW(3), INTERVAL #{expireSeconds} SECOND)
            )
            """)
    int insertProcessing(@Param("principalScope") String principalScope,
                         @Param("operationScope") String operationScope,
                         @Param("idempotencyKey") String idempotencyKey,
                         @Param("requestHash") String requestHash,
                         @Param("traceId") String traceId,
                         @Param("ownerToken") String ownerToken,
                         @Param("expireSeconds") int expireSeconds);

    @Select("""
            SELECT record_id AS recordId,
                   principal_scope AS principalScope,
                   operation_scope AS operationScope,
                   idempotency_key AS idempotencyKey,
                   request_hash AS requestHash,
                   record_state AS recordState,
                   response_status AS responseStatus,
                   response_body AS responseBody,
                   trace_id AS traceId,
                   owner_token AS ownerToken,
                   created_at AS createdAt,
                   expires_at AS expiresAt,
                   expires_at <= NOW(3) AS expired
              FROM idempotency_record
             WHERE principal_scope = #{principalScope}
               AND operation_scope = #{operationScope}
               AND idempotency_key = #{idempotencyKey}
             FOR UPDATE
            """)
    IdempotencyRecord selectForUpdate(@Param("principalScope") String principalScope,
                                      @Param("operationScope") String operationScope,
                                      @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE idempotency_record
               SET request_hash = #{requestHash},
                   record_state = 'PROCESSING',
                   response_status = NULL,
                   response_body = NULL,
                   trace_id = #{traceId},
                   owner_token = #{ownerToken},
                   created_at = NOW(3),
                   expires_at = DATE_ADD(NOW(3), INTERVAL #{expireSeconds} SECOND)
             WHERE record_id = #{recordId}
               AND expires_at <= NOW(3)
            """)
    int takeOverExpired(@Param("recordId") long recordId,
                        @Param("requestHash") String requestHash,
                        @Param("traceId") String traceId,
                        @Param("ownerToken") String ownerToken,
                        @Param("expireSeconds") int expireSeconds);

    @Update("""
            UPDATE idempotency_record
               SET record_state = 'COMPLETED',
                   response_status = #{responseStatus},
                   response_body = #{responseBody},
                   trace_id = #{traceId}
             WHERE record_id = #{recordId}
               AND owner_token = #{ownerToken}
               AND record_state = 'PROCESSING'
            """)
    int complete(@Param("recordId") long recordId,
                 @Param("ownerToken") String ownerToken,
                 @Param("responseStatus") int responseStatus,
                 @Param("responseBody") String responseBody,
                 @Param("traceId") String traceId);

}
