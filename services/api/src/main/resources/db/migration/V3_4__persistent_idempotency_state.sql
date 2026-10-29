-- V3 is already published. Add an explicit processing state so response_status
-- remains an HTTP status and can stay NULL until the response is committed.
ALTER TABLE `idempotency_record`
    MODIFY COLUMN `response_status` INT NULL COMMENT '已完成请求的原HTTP状态码，处理中为空',
    MODIFY COLUMN `response_body` MEDIUMTEXT NULL COMMENT '已完成请求的原JSON响应体，处理中为空',
    ADD COLUMN `record_state` VARCHAR(16) NOT NULL DEFAULT 'COMPLETED'
        COMMENT '幂等记录状态：PROCESSING 或 COMPLETED' AFTER `request_hash`,
    ADD COLUMN `owner_token` CHAR(36) NULL
        COMMENT '当前处理尝试的所有权令牌，防止过期接管后的旧进程覆盖' AFTER `trace_id`;

ALTER TABLE `idempotency_record`
    ADD CONSTRAINT `chk_idempotency_record_state`
        CHECK (`record_state` IN ('PROCESSING', 'COMPLETED'));
