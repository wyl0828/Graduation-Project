package cn.communityelderly.modules.auth.service.impl;

import cn.communityelderly.modules.auth.mapper.PhoneVerificationChallengeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PhoneVerificationChallengeAttemptService {

    private final PhoneVerificationChallengeMapper mapper;

    public PhoneVerificationChallengeAttemptService(PhoneVerificationChallengeMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordFailure(String challengeId, String phone, String purpose,
                              String relatedRequestId, LocalDateTime now) {
        mapper.recordFailedAttempt(challengeId, phone, purpose, relatedRequestId, now);
    }
}
