package cn.communityelderly.common.exception;

import cn.communityelderly.common.api.ErrorCode;

public class UnauthorizedException extends BusinessException {
    public UnauthorizedException() {
        super(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    public UnauthorizedException(String message) {
        super(ErrorCode.AUTHENTICATION_REQUIRED, message);
    }
}
