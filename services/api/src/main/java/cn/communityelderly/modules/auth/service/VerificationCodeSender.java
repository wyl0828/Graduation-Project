package cn.communityelderly.modules.auth.service;

/**
 * Delivers a one-time SMS verification code. The isolated demo environment has no SMS
 * provider, so the default implementation records the message in the application log.
 * A real provider adapter must implement this interface without changing the callers.
 */
public interface VerificationCodeSender {

    void send(String phoneNormalized, String purpose, String code);
}
