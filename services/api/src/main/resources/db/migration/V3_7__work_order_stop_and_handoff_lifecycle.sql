-- Keep the original assignee and execution state when access is revoked; the
-- handoff row records that dispatch must complete a successor transfer.
ALTER TABLE `work_order_handoff`
    MODIFY COLUMN `to_user_id` BIGINT NULL COMMENT '新承接人；待重新分派时为空',
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'REQUESTED'
        COMMENT 'REQUESTED, ACCEPTED, REJECTED, CANCELLED, REASSIGNMENT_REQUIRED';

-- Snapshot the work completed, the work still outstanding, and the follow-up
-- plan with the stop request. The reviewer executes an approval atomically.
ALTER TABLE `service_stop_request`
    ADD COLUMN `completed_content` TEXT NULL COMMENT '中止前已完成内容' AFTER `stop_reason`,
    ADD COLUMN `incomplete_content` TEXT NULL COMMENT '中止时未完成内容' AFTER `completed_content`,
    ADD COLUMN `follow_up_plan` TEXT NULL COMMENT '中止后的后续安排' AFTER `incomplete_content`,
    ADD COLUMN `executor_user_id` BIGINT NULL COMMENT '批准中止并执行状态终结的管理员ID' AFTER `review_comment`,
    ADD COLUMN `executed_at` DATETIME(3) NULL COMMENT '服务中止执行时间' AFTER `executor_user_id`;
