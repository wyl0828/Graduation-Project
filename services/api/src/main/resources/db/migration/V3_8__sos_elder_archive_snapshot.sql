ALTER TABLE `sos_event`
    ADD COLUMN `elder_inactive_at_acceptance` TINYINT(1) NULL
        COMMENT 'SOS受理时档案是否停用；历史记录为NULL表示当时状态未知'
        AFTER `occurred_at`;
