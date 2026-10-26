package cn.communityelderly.modules.auth.mapper;

import cn.communityelderly.modules.auth.entity.PhoneVerificationChallenge;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PhoneVerificationChallengeMapper extends BaseMapper<PhoneVerificationChallenge> {

    @Update("""
            UPDATE phone_verification_challenge
            SET status = 'CONSUMED', consumed_at = #{now}
            WHERE challenge_id = #{challengeId}
              AND phone_normalized = #{phone}
              AND purpose = #{purpose}
              AND related_request_id = #{relatedRequestId}
              AND code_mac = #{expectedCodeMac}
              AND status = 'PENDING'
              AND consumed_at IS NULL
              AND expires_at > #{now}
              AND attempt_count < 5
            """)
    int consumeIfPending(@Param("challengeId") String challengeId,
                         @Param("phone") String phone,
                         @Param("purpose") String purpose,
                         @Param("relatedRequestId") String relatedRequestId,
                         @Param("expectedCodeMac") byte[] expectedCodeMac,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE phone_verification_challenge
            SET status = CASE WHEN attempt_count + 1 >= 5 THEN 'LOCKED' ELSE status END,
                attempt_count = attempt_count + 1
            WHERE challenge_id = #{challengeId}
              AND phone_normalized = #{phone}
              AND purpose = #{purpose}
              AND related_request_id = #{relatedRequestId}
              AND status = 'PENDING'
              AND consumed_at IS NULL
              AND expires_at > #{now}
              AND attempt_count < 5
            """)
    int recordFailedAttempt(@Param("challengeId") String challengeId,
                            @Param("phone") String phone,
                            @Param("purpose") String purpose,
                            @Param("relatedRequestId") String relatedRequestId,
                            @Param("now") LocalDateTime now);
}
