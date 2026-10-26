package cn.communityelderly.modules.auth.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AuthRateLimitMapper {

    @Select("""
            SELECT COUNT(1)
            FROM auth_rate_limit
            WHERE key_type = #{keyType}
              AND key_hash = #{keyHash}
              AND cooldown_until > #{now}
            """)
    int hasActiveCooldown(@Param("keyType") String keyType,
                          @Param("keyHash") byte[] keyHash,
                          @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO auth_rate_limit
                (key_type, key_hash, window_started_at, failure_count, cooldown_until, updated_at)
            VALUES
                (#{keyType}, #{keyHash}, #{now}, 1,
                 CASE WHEN #{threshold} <= 1 THEN #{cooldownUntil} ELSE NULL END, #{now})
            ON DUPLICATE KEY UPDATE
                cooldown_until = CASE
                    WHEN window_started_at <= #{windowCutoff} THEN NULL
                    WHEN cooldown_until > #{now} THEN cooldown_until
                    WHEN failure_count + 1 >= #{threshold} THEN #{cooldownUntil}
                    ELSE cooldown_until
                END,
                failure_count = CASE
                    WHEN window_started_at <= #{windowCutoff} THEN 1
                    ELSE failure_count + 1
                END,
                window_started_at = CASE
                    WHEN window_started_at <= #{windowCutoff} THEN #{now}
                    ELSE window_started_at
                END,
                updated_at = #{now}
            """)
    int recordFailure(@Param("keyType") String keyType,
                      @Param("keyHash") byte[] keyHash,
                      @Param("now") LocalDateTime now,
                      @Param("windowCutoff") LocalDateTime windowCutoff,
                      @Param("cooldownUntil") LocalDateTime cooldownUntil,
                      @Param("threshold") int threshold);

    @Update("""
            UPDATE auth_rate_limit
            SET failure_count = 0,
                window_started_at = #{now},
                cooldown_until = NULL,
                updated_at = #{now}
            WHERE key_type = 'PHONE'
              AND key_hash = #{keyHash}
            """)
    int resetPhoneFailures(@Param("keyHash") byte[] keyHash,
                           @Param("now") LocalDateTime now);
}
