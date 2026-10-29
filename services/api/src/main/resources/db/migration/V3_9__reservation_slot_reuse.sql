-- Support the active-slot locking current read with a composite lookup index.
-- V3.2's generated-key unique index remains the database-level active-slot guard.
ALTER TABLE `service_reservation`
    ADD INDEX `idx_elder_service_slot` (`elder_id`, `service_type`, `service_date`, `slot_code`);
