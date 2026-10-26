-- 持久化登录失败限制。手机号和来源地址只保存 HMAC 摘要。
CREATE TABLE `auth_rate_limit` (
    `key_type` VARCHAR(8) NOT NULL COMMENT 'PHONE 或 IP',
    `key_hash` BINARY(32) NOT NULL COMMENT '服务端密钥 HMAC-SHA-256 摘要',
    `window_started_at` DATETIME(3) NOT NULL COMMENT '连续失败窗口起点',
    `failure_count` INT NOT NULL DEFAULT 0 COMMENT '当前窗口连续失败次数',
    `cooldown_until` DATETIME(3) NULL COMMENT '冷却截止时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`key_type`, `key_hash`),
    CONSTRAINT `chk_auth_rate_limit_key_type` CHECK (`key_type` IN ('PHONE', 'IP')),
    CONSTRAINT `chk_auth_rate_limit_failure_count` CHECK (`failure_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录失败限流记录';
