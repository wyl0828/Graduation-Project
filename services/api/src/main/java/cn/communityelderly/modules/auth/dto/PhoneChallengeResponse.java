package cn.communityelderly.modules.auth.dto;

import java.time.LocalDateTime;

/**
 * HTTP response for a phone challenge. The plaintext code is deliberately not included:
 * the delivery adapter owns it. Only the challenge id and its expiry are exposed.
 */
public class PhoneChallengeResponse {

    private final String challengeId;
    private final LocalDateTime expiresAt;

    public PhoneChallengeResponse(String challengeId, LocalDateTime expiresAt) {
        this.challengeId = challengeId;
        this.expiresAt = expiresAt;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

}
