-- ==============================================================================
-- V3.3 工单现场凭据照片存储支持
-- 为 work_order 增加 photo_urls 字段持久化现场服务履约凭据
-- ==============================================================================

ALTER TABLE `work_order`
    ADD COLUMN `photo_urls` TEXT NULL COMMENT '现场履约凭据照片URL列表 (分号分隔)' AFTER `photo_exemption_reason`;
