package cn.communityelderly.common.exception;

import cn.communityelderly.common.api.ErrorCode;

public class ConflictException extends BusinessException {
    public ConflictException(ErrorCode errorCode, String message, Object conflictDetails) {
        super(errorCode, message, conflictDetails);
    }

    public static ConflictException optimisticLock(long currentVersion, long expectedVersion) {
        return new ConflictException(
            ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
            "数据已被其他操作更新，请刷新重试",
            new OptimisticLockDetail(currentVersion, expectedVersion)
        );
    }

    public static ConflictException fieldValueConflict(Object conflicts) {
        return new ConflictException(
            ErrorCode.FIELD_VALUE_CONFLICT,
            "老人档案字段在此期间已被更新，更正申请基线已失效",
            conflicts
        );
    }

    public static class OptimisticLockDetail {
        private final long currentVersion;
        private final long expectedVersion;

        public OptimisticLockDetail(long currentVersion, long expectedVersion) {
            this.currentVersion = currentVersion;
            this.expectedVersion = expectedVersion;
        }

        public long getCurrentVersion() {
            return currentVersion;
        }

        public long getExpectedVersion() {
            return expectedVersion;
        }
    }
}
