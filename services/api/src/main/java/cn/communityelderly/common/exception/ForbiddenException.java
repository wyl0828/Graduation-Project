package cn.communityelderly.common.exception;

import cn.communityelderly.common.api.ErrorCode;

public class ForbiddenException extends BusinessException {
    public ForbiddenException() {
        super(ErrorCode.ACCESS_DENIED);
    }

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public ForbiddenException(ErrorCode code, String message) {
        super(code, message);
    }
}
