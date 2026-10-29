-- 固定性能数据集 v1；仅配合 load_fixed_dataset.ps1 导入。
-- 目标必须是完成当前 Flyway 迁移的全新 elderly_care_perf 本机数据库。
-- 该文件不删除、不覆盖任何现有数据；每次正式复跑都应重建该隔离库。
-- 规模：2 社区、100 老人档案、5000 体征、500 工单、500 预约。
-- 登录口令不写入本文件；负载工具只从进程环境变量读取凭据。
CREATE TEMPORARY TABLE _perf_seed_guard (
    gate_id TINYINT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO _perf_seed_guard (gate_id) VALUES (0);
INSERT INTO _perf_seed_guard (gate_id)
SELECT IF(
    DATABASE() = 'elderly_care_perf'
    AND @perf_fixed_seed_authorized = 'fixed-dataset-v1'
    AND (SELECT COUNT(*) FROM sys_community) = 0
    AND (SELECT COUNT(*) FROM sys_user) = 0
    AND (SELECT COUNT(*) FROM sys_user_role) = 0
    AND (SELECT COUNT(*) FROM elder_profile) = 0
    AND (SELECT COUNT(*) FROM vital_record) = 0
    AND (SELECT COUNT(*) FROM alert_rule_version) = 0
    AND (SELECT COUNT(*) FROM service_catalog) = 0
    AND (SELECT COUNT(*) FROM work_order) = 0
    AND (SELECT COUNT(*) FROM service_reservation) = 0,
    1,
    NULL
);
DROP TEMPORARY TABLE _perf_seed_guard;

CREATE TEMPORARY TABLE _perf_digit (
    d TINYINT UNSIGNED NOT NULL PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO _perf_digit (d) VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

CREATE TEMPORARY TABLE _perf_sequence (
    n SMALLINT UNSIGNED NOT NULL PRIMARY KEY
) ENGINE=MEMORY;
INSERT INTO _perf_sequence (n)
SELECT ones.d + tens.d * 10 + hundreds.d * 100 + thousands.d * 1000
FROM _perf_digit AS ones
CROSS JOIN _perf_digit AS tens
CROSS JOIN _perf_digit AS hundreds
CROSS JOIN _perf_digit AS thousands;

START TRANSACTION;

INSERT INTO sys_community (community_id, community_code, community_name, status, created_at, updated_at)
VALUES
    (1, 'PERF-V1-C01', '性能合成社区一', 'ACTIVE', '2026-10-01 08:00:00.000', '2026-10-01 08:00:00.000'),
    (2, 'PERF-V1-C02', '性能合成社区二', 'ACTIVE', '2026-10-01 08:00:00.000', '2026-10-01 08:00:00.000');

INSERT INTO sys_user (
    user_id, account_kind, phone_normalized, password_hash, status,
    password_changed_at, disabled_at, disabled_reason, created_at, updated_at
)
VALUES (
    1, 'STAFF', '13800000001',
    '$2a$10$f0eXYyUYjEZuHBhdYlSvvOjFx7F0OHRrFtBHKocdR1XQF5RKPbWz6',
    'ACTIVE', '2026-10-01 08:00:00.000', NULL, NULL,
    '2026-10-01 08:00:00.000', '2026-10-01 08:00:00.000'
);

INSERT INTO sys_user_role (
    assignment_id, user_id, role_code, community_id, status, granted_by, granted_at
)
VALUES (1, 1, 'COMMUNITY_ADMIN', 1, 'ACTIVE', 1, '2026-10-01 08:00:00.000');

INSERT INTO elder_profile (
    elder_id, archive_no, community_id, name, sex, birth_date, birth_date_unknown_reason,
    address, contact_phone, contact_phone_missing_reason,
    emergency_contact_name, emergency_contact_relation, emergency_contact_phone,
    emergency_contact_name_missing_reason, emergency_contact_relation_missing_reason,
    emergency_contact_phone_missing_reason, id_number_ciphertext, id_number_lookup_hmac,
    status, status_reason, row_version, created_by, created_at, updated_by, updated_at
)
SELECT
    n + 1,
    CONCAT('PERF1-ELDER-', LPAD(n + 1, 3, '0')),
    IF(n < 50, 1, 2),
    CONCAT('合成老人', LPAD(n + 1, 3, '0')),
    IF(MOD(n, 2) = 0, 'FEMALE', 'MALE'),
    DATE(TIMESTAMPADD(YEAR, MOD(n * 7, 35), '1938-01-01')),
    NULL,
    CONCAT('性能测试合成地址-', LPAD(n + 1, 3, '0')),
    NULL, '性能测试固定数据，无联系电话',
    NULL, NULL, NULL,
    '性能测试固定数据，无紧急联系人姓名',
    '性能测试固定数据，无紧急联系人关系',
    '性能测试固定数据，无紧急联系人电话',
    NULL, NULL,
    'ACTIVE', '固定性能数据集 v1', 1, 1,
    '2026-10-01 08:00:00.000', 1, '2026-10-01 08:00:00.000'
FROM _perf_sequence AS n
WHERE n.n < 100;

INSERT INTO service_catalog (
    catalog_id, service_type, service_name, description,
    estimated_duration_min, icon_url, status, sort_order
)
VALUES
    (1, 'DAILY_CARE', '合成日常照料', '固定性能数据集 v1 合成目录项', 60, NULL, 'ACTIVE', 1),
    (2, 'MEDICAL_ESCORT', '合成就医陪诊', '固定性能数据集 v1 合成目录项', 120, NULL, 'ACTIVE', 2),
    (3, 'CLEANING', '合成居家保洁', '固定性能数据集 v1 合成目录项', 90, NULL, 'ACTIVE', 3),
    (4, 'MEAL_DELIVERY', '合成助餐送餐', '固定性能数据集 v1 合成目录项', 30, NULL, 'ACTIVE', 4);

INSERT INTO alert_rule_version (
    rule_version_id, rule_name, vital_type, rule_json, status,
    effective_from, effective_to, created_by, created_at
)
VALUES (
    1, '性能数据集血压规则', 'BLOOD_PRESSURE',
    '{"systolic_high":140,"diastolic_high":90,"urgent_systolic":160,"urgent_diastolic":100}',
    'ACTIVE', '2026-01-01 00:00:00.000', NULL, 1, '2026-10-01 08:00:00.000'
);

INSERT INTO vital_record (
    vital_id, elder_id, vital_type, value_primary, value_secondary, unit, source_type,
    recorded_at, created_by, created_at, remark, idempotency_key
)
SELECT
    n + 1,
    MOD(n, 100) + 1,
    'BLOOD_PRESSURE',
    118 + MOD(n, 18),
    72 + MOD(n, 14),
    'mmHg',
    'SIMULATED',
    TIMESTAMPADD(MINUTE, n * 8, '2026-09-28 00:00:00.000'),
    1,
    TIMESTAMPADD(MINUTE, n * 8 + 1, '2026-09-28 00:00:00.000'),
    '固定性能数据集 v1 合成体征',
    CONCAT('PERF1-VITAL-', LPAD(n + 1, 5, '0'))
FROM _perf_sequence AS n
WHERE n.n < 5000;

INSERT INTO work_order (
    order_no, source_type, source_reference_id, elder_id, community_id, service_type,
    order_title, order_description, urgency_level, status, current_assignee_id,
    dispatch_time, accept_time, service_start_time, service_finish_time,
    service_result_summary, result_photo_required, photo_exemption_reason,
    reviewer_user_id, reviewed_at, review_comment, row_version, created_at, updated_at
)
SELECT
    CONCAT('PF1WO', LPAD(n.n + 1, 6, '0')),
    'MANUAL',
    CONCAT('PERF1-WO-', LPAD(n.n + 1, 6, '0')),
    MOD(n.n, 100) + 1,
    IF(MOD(n.n, 100) < 50, 1, 2),
    CASE MOD(n.n, 4)
        WHEN 0 THEN 'DAILY_CARE'
        WHEN 1 THEN 'MEDICAL_ESCORT'
        WHEN 2 THEN 'CLEANING'
        ELSE 'MEAL_DELIVERY'
    END,
    CONCAT('性能合成工单-', LPAD(n.n + 1, 4, '0')),
    '固定性能数据集 v1 合成工单',
    'NORMAL',
    CASE MOD(n.n, 4)
        WHEN 0 THEN 'PENDING_DISPATCH'
        WHEN 1 THEN 'PENDING_ACCEPT'
        WHEN 2 THEN 'PROCESSING'
        ELSE 'CLOSED'
    END,
    CASE WHEN MOD(n.n, 4) IN (1, 2, 3) THEN 1 ELSE NULL END,
    CASE WHEN MOD(n.n, 4) IN (1, 2, 3) THEN TIMESTAMPADD(MINUTE, n.n + 5, '2026-10-01 08:00:00.000') ELSE NULL END,
    CASE WHEN MOD(n.n, 4) IN (2, 3) THEN TIMESTAMPADD(MINUTE, n.n + 10, '2026-10-01 08:00:00.000') ELSE NULL END,
    CASE WHEN MOD(n.n, 4) IN (2, 3) THEN TIMESTAMPADD(MINUTE, n.n + 15, '2026-10-01 08:00:00.000') ELSE NULL END,
    CASE WHEN MOD(n.n, 4) = 3 THEN TIMESTAMPADD(MINUTE, n.n + 75, '2026-10-01 08:00:00.000') ELSE NULL END,
    CASE WHEN MOD(n.n, 4) = 3 THEN '固定性能数据集 v1 合成服务结果' ELSE NULL END,
    0,
    NULL,
    CASE WHEN MOD(n.n, 4) = 3 THEN 1 ELSE NULL END,
    CASE WHEN MOD(n.n, 4) = 3 THEN TIMESTAMPADD(MINUTE, n.n + 80, '2026-10-01 08:00:00.000') ELSE NULL END,
    CASE WHEN MOD(n.n, 4) = 3 THEN '固定性能数据集 v1 合成审核' ELSE NULL END,
    1 + MOD(n.n, 4),
    TIMESTAMPADD(MINUTE, n.n, '2026-10-01 08:00:00.000'),
    TIMESTAMPADD(MINUTE, n.n + 80, '2026-10-01 08:00:00.000')
FROM _perf_sequence AS n
WHERE n.n < 500;

INSERT INTO service_reservation (
    reservation_no, elder_id, applicant_user_id, service_type, service_date,
    slot_code, service_address, special_notes, status, reject_reason,
    row_version, created_at, updated_at
)
SELECT
    CONCAT('PF1RSV', LPAD(n.n + 1, 6, '0')),
    MOD(n.n, 100) + 1,
    1,
    CASE MOD(FLOOR(n.n / 100), 4)
        WHEN 0 THEN 'DAILY_CARE'
        WHEN 1 THEN 'MEDICAL_ESCORT'
        WHEN 2 THEN 'CLEANING'
        ELSE 'MEAL_DELIVERY'
    END,
    DATE(TIMESTAMPADD(DAY, FLOOR(n.n / 100), '2026-10-01 00:00:00.000')),
    IF(MOD(n.n, 2) = 0, 'MORNING', 'AFTERNOON'),
    CONCAT('性能测试合成地址-', LPAD(MOD(n.n, 100) + 1, 3, '0')),
    '固定性能数据集 v1 合成预约',
    CASE MOD(n.n, 5)
        WHEN 0 THEN 'PENDING_CONFIRMATION'
        WHEN 1 THEN 'CONFIRMED'
        WHEN 2 THEN 'COMPLETED'
        WHEN 3 THEN 'CANCELLED'
        ELSE 'REJECTED'
    END,
    CASE WHEN MOD(n.n, 5) = 4 THEN '固定性能数据集合成拒绝样本' ELSE NULL END,
    1,
    TIMESTAMPADD(MINUTE, n.n, '2026-10-01 08:00:00.000'),
    TIMESTAMPADD(MINUTE, n.n + 5, '2026-10-01 08:00:00.000')
FROM _perf_sequence AS n
WHERE n.n < 500;

DROP TEMPORARY TABLE _perf_sequence;
DROP TEMPORARY TABLE _perf_digit;
COMMIT;
