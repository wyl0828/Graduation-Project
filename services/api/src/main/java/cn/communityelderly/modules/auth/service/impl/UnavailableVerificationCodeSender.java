package cn.communityelderly.modules.auth.service.impl;

import cn.communityelderly.common.api.ErrorCode;
import cn.communityelderly.common.exception.BusinessException;
import cn.communityelderly.modules.auth.service.VerificationCodeSender;
import org.springframework.stereotype.Component;

/**
 * Default sender until a trusted SMS provider is configured. It deliberately does not
 * log or return a verification code.
 */
@Component
public class UnavailableVerificationCodeSender implements VerificationCodeSender {

    @Override
    public void send(String phoneNormalized, String purpose, String code) {
        throw new BusinessException(ErrorCode.PHONE_VERIFICATION_UNAVAILABLE,
                ErrorCode.PHONE_VERIFICATION_UNAVAILABLE.getDefaultMessage());
    }
}
