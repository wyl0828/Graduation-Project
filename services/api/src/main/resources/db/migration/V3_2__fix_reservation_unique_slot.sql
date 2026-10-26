-- ==============================================================================
-- V3.2 修复预约时隙唯一约束
-- 允许已取消 (CANCELLED) / 已拒绝 (REJECTED) / 已中止 (SERVICE_STOPPED) 的历史预约释放时段槽位
-- ==============================================================================

ALTER TABLE `service_reservation`
    DROP INDEX `uk_elder_service_slot`,
    ADD COLUMN `active_slot_key` VARCHAR(128) GENERATED ALWAYS AS (
        CASE
            WHEN `status` IN ('CANCELLED', 'REJECTED', 'SERVICE_STOPPED') THEN NULL
            ELSE CONCAT(`elder_id`, '#', `service_type`, '#', `service_date`, '#', `slot_code`)
        END
    ) STORED COMMENT '有效预约防冲突生成键（仅有效状态非空）',
    ADD UNIQUE KEY `uk_active_elder_service_slot` (`active_slot_key`);
