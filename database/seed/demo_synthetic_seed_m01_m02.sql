-- ==============================================================================
-- 社区居家养老服务系统 演示环境合成种子数据 (M01/M02 核心测试态覆盖)
-- 仅供本机隔离演示库 elderly_care_demo 使用，全部数据均为虚构/合成数据
-- 覆盖核心场景:
-- 1. 四类测试账号: 管理员(13800000001)、网格员(13800000002)、老人(13800000003)、子女(13800000004/05/06)
-- 2. 老人状态: 已激活老人(张建国, elder_id=1)、待激活老人(李秀英, elder_id=2)、关联争议老人(王大明, elder_id=3)
-- 3. 激活凭证: 李秀英的 6 位待消费激活码 (code=123456, 状态=ISSUED)
-- 4. 亲情绑定多态:
--    - ACTIVE (已生效绑定: 张建国 <-> 张伟)
--    - PENDING (待审批申请: 张建国 <-> 李强)
--    - FROZEN (争议冻结中: 王大明 <-> 王芳, 5工作日复核)
-- 5. 档案更正闭环 (FZ-01):
--    - 张伟提交的地址与联系人更正申请 (PENDING_REVIEW, 包含 address 与 phone 的 baseline 固化值)
-- ==============================================================================

-- 仅允许通过已校验目标库的包装脚本在首次加载时执行。
-- 哨兵行与主键冲突在所有 SQL mode 下都会报错，且发生在首条业务 INSERT 前。
CREATE TEMPORARY TABLE `DEMO_SEED_WRONG_DATABASE_M01_M02` (
    `guard_id` TINYINT NOT NULL PRIMARY KEY
);
INSERT INTO `DEMO_SEED_WRONG_DATABASE_M01_M02` (`guard_id`) VALUES (0);
INSERT INTO `DEMO_SEED_WRONG_DATABASE_M01_M02` (`guard_id`)
VALUES (IF(DATABASE() = 'elderly_care_demo' AND @demo_seed_authorized = 'first-load-synthetic-demo-v1', 1, 0));
DROP TEMPORARY TABLE `DEMO_SEED_WRONG_DATABASE_M01_M02`;

-- ------------------------------------------------------------------------------
-- 1. 基础社区数据
-- ------------------------------------------------------------------------------
INSERT INTO `sys_community` (`community_id`, `community_code`, `community_name`, `status`, `created_at`, `updated_at`)
VALUES
(1, 'COMM_001', '朝阳社区', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000'),
(2, 'COMM_002', '幸福社区', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000');

-- ------------------------------------------------------------------------------
-- 2. 基础用户数据 (密码统一为测试哈希格式，初始密码均为各角色标准演示密码)
-- ------------------------------------------------------------------------------
-- 密码明文说明:
-- 13800000001: Admin123456 (社区管理员)
-- 13800000002: Worker123456 (网格员)
-- 13800000003: Elder123456 (老人 张建国)
-- 13800000004: Child123456 (子女 张伟 - 已绑定)
-- 13800000005: Child123456 (子女 李强 - 待审核)
-- 13800000006: Child123456 (子女 王芳 - 争议冻结)
INSERT INTO `sys_user` (`user_id`, `account_kind`, `phone_normalized`, `password_hash`, `status`, `password_changed_at`, `created_at`, `updated_at`)
VALUES
(1, 'STAFF', '13800000001','$2a$10$f0eXYyUYjEZuHBhdYlSvvOjFx7F0OHRrFtBHKocdR1XQF5RKPbWz6', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000'),
(2, 'STAFF', '13800000002','$2a$10$lUAGHnNmNVGGWfnMR2tHv.WZE1ogXUjDmOwOnLP7oF.BanqfpLysC', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000'),
(3, 'FAMILY', '13800000003','$2a$10$kMcHHqBuJGb.8ecb5LOr7u3UFa3cq5ceset2XmfztF.bTyuJ0HuH6', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000'),
(4, 'FAMILY', '13800000004','$2a$10$0p3PLWCaXUvxpXY3JRw8S.RFLZVF7gR3jYimbgmBExXDT8RLQgq7i', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000'),
(5, 'FAMILY', '13800000005','$2a$10$uYcMdLanjy8Hm8wY6QPYSuiovA3WoSzfSlVIO/.ih5YxUXzidzf5S', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000'),
(6, 'FAMILY', '13800000006','$2a$10$/HY9iydIUXRd.WDKJJ9Nyu2VMzpKEHTvuz8zPPx7I2wgyU8xQT7nW', 'ACTIVE', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000', '2026-10-25 08:00:00.000');

-- ------------------------------------------------------------------------------
-- 3. 用户角色分配表
-- ------------------------------------------------------------------------------
INSERT INTO `sys_user_role` (`assignment_id`, `user_id`, `role_code`, `community_id`, `status`, `granted_by`, `granted_at`)
VALUES
(1, 1, 'COMMUNITY_ADMIN', 1, 'ACTIVE', 1, '2026-10-25 08:00:00.000'),
(2, 2, 'GRID_WORKER', 1, 'ACTIVE', 1, '2026-10-25 08:00:00.000'),
(3, 3, 'ELDER', NULL, 'ACTIVE', 1, '2026-10-25 08:30:00.000'),
(4, 4, 'CHILD', NULL, 'ACTIVE', 1, '2026-10-25 08:30:00.000'),
(5, 5, 'CHILD', NULL, 'ACTIVE', 1, '2026-10-25 08:30:00.000'),
(6, 6, 'CHILD', NULL, 'ACTIVE', 1, '2026-10-25 08:30:00.000');

-- ------------------------------------------------------------------------------
-- 4. 老人业务档案数据
-- ------------------------------------------------------------------------------
INSERT INTO `elder_profile` (
    `elder_id`, `archive_no`, `community_id`, `name`, `sex`, `birth_date`, `birth_date_unknown_reason`,
    `address`, `contact_phone`, `contact_phone_missing_reason`,
    `emergency_contact_name`, `emergency_contact_relation`, `emergency_contact_phone`,
    `emergency_contact_name_missing_reason`, `emergency_contact_relation_missing_reason`, `emergency_contact_phone_missing_reason`,
    `id_number_lookup_hmac`, `status`, `status_reason`, `row_version`, `created_by`, `created_at`, `updated_by`, `updated_at`
)
VALUES
-- 老人 1: 张建国 (已建档、已激活账号)
(
    1, 'ARC202610250001', 1, '张建国', 'MALE', '1952-05-18', NULL,
    '朝阳社区幸福路12号院3号楼2单元101', '13800000003', NULL,
    '张伟', '儿子', '13800000004',
    NULL, NULL, NULL,
    'hmac_sha256_mock_110101195205180011_for_test', 'ACTIVE', '正常建档激活', 1, 1, '2026-10-25 08:10:00.000', 1, '2026-10-25 08:10:00.000'
),
-- 老人 2: 李秀英 (已建档、待激活，电话缺失有说明)
(
    2, 'ARC202610250002', 1, '李秀英', 'FEMALE', '1956-08-22', NULL,
    '朝阳社区康乐家园5栋102', NULL, '老人暂无个人手机，与配偶共用或由社区上门联系',
    '李强', '儿子', '13800000005',
    NULL, NULL, NULL,
    'hmac_sha256_mock_110101195608220022_for_test', 'ACTIVE', '社区新摸排建档', 1, 1, '2026-10-25 08:15:00.000', 1, '2026-10-25 08:15:00.000'
),
-- 老人 3: 王大明 (已建档、已激活，涉及亲情监护争议)
(
    3, 'ARC202610250003', 1, '王大明', 'MALE', '1948-12-05', NULL,
    '朝阳社区颐和园路8号2单元301', '13800000010', NULL,
    '王芳', '女儿', '13800000006',
    NULL, NULL, NULL,
    'hmac_sha256_mock_110101194812050033_for_test', 'ACTIVE', '正常建档', 1, 1, '2026-10-25 08:20:00.000', 1, '2026-10-25 08:20:00.000'
);

-- ------------------------------------------------------------------------------
-- 5. 激活码签发记录与账号绑定
-- ------------------------------------------------------------------------------
-- 老人1 激活码已消费 (关联到 user_id = 3)
INSERT INTO `elder_activation_ticket` (
    `ticket_id`, `elder_id`, `code_mac`, `status`, `issued_by`, `issue_mode`, `identity_check_result`,
    `issued_at`, `expires_at`, `failed_attempts`, `used_at`, `used_by_user_id`, `used_idempotency_key`
)
VALUES
(
    'act-ticket-20261025-0001', 1, UNHEX(SHA2('123456', 256)),
    'USED', 1, 'IN_PERSON', 'VERIFIED',
    '2026-10-25 08:10:00.000', '2026-10-26 08:10:00.000', 0,
    '2026-10-25 08:30:00.000', 3, 'idem-act-001'
);

INSERT INTO `elder_identity_link` (`link_id`, `elder_id`, `user_id`, `linked_by_activation_ticket_id`, `linked_at`)
VALUES
(1, 1, 3, 'act-ticket-20261025-0001', '2026-10-25 08:30:00.000');

-- 老人2 激活码待使用 (状态 ISSUED，6位激活码: 123456, 有效期24小时)
-- 哈希必须真的是 SHA-256('123456')，签发/过期时间用相对时间，避免演示库到期后无法激活。
INSERT INTO `elder_activation_ticket` (
    `ticket_id`, `elder_id`, `code_mac`, `status`, `issued_by`, `issue_mode`, `identity_check_result`,
    `issued_at`, `expires_at`, `failed_attempts`
)
VALUES
(
    'act-ticket-20261025-0002', 2, UNHEX(SHA2('123456', 256)),
    'ISSUED', 1, 'IN_PERSON', 'VERIFIED',
    DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 23 HOUR), 0
);

-- ------------------------------------------------------------------------------
-- 6. 亲情绑定与申请历史 (覆盖 ACTIVE、PENDING、FROZEN 三大核心测试态)
-- ------------------------------------------------------------------------------
-- 场景 A: 张伟申请绑定张建国 (已批准，当前状态 ACTIVE)
INSERT INTO `family_link_application` (
    `application_id`, `elder_id`, `child_user_id`, `submitted_by_user_id`, `submission_mode`,
    `relationship`, `apply_reason`, `status`, `reviewer_user_id`, `reviewed_at`, `decision_reason`, `link_generation`, `created_at`
)
VALUES
(
    1, 1, 4, 4, 'SELF',
    '儿子', '本人系张建国长子，在市区工作，申请远程关注父亲健康及代办助老服务',
    'APPROVED', 1, '2026-10-25 08:45:00.000', '户口簿与老人当面核验无误，符合亲情绑定要求', 1, '2026-10-25 08:35:00.000'
);

INSERT INTO `elder_family_link` (
    `link_id`, `elder_id`, `child_user_id`, `status`, `current_application_id`, `link_generation`, `row_version`, `created_at`, `updated_at`
)
VALUES
(1, 1, 4, 'ACTIVE', 1, 1, 1, '2026-10-25 08:45:00.000', '2026-10-25 08:45:00.000');

-- 场景 B: 李强申请绑定张建国 (待审核状态 PENDING)
INSERT INTO `family_link_application` (
    `application_id`, `elder_id`, `child_user_id`, `submitted_by_user_id`, `submission_mode`,
    `relationship`, `apply_reason`, `status`, `link_generation`, `created_at`
)
VALUES
(
    2, 1, 5, 5, 'SELF',
    '外甥', '表哥张伟因公出差，申请作为共同看护人协助照料舅舅生活',
    'PENDING', 1, '2026-10-25 09:30:00.000'
);

INSERT INTO `elder_family_link` (
    `link_id`, `elder_id`, `child_user_id`, `status`, `current_application_id`, `link_generation`, `row_version`, `created_at`, `updated_at`
)
VALUES
(2, 1, 5, 'PENDING', 2, 1, 1, '2026-10-25 09:30:00.000', '2026-10-25 09:30:00.000');

-- 场景 C: 王芳绑定王大明 (处于争议冻结状态 FROZEN)
INSERT INTO `family_link_application` (
    `application_id`, `elder_id`, `child_user_id`, `submitted_by_user_id`, `submission_mode`,
    `relationship`, `apply_reason`, `status`, `reviewer_user_id`, `reviewed_at`, `decision_reason`, `link_generation`, `created_at`
)
VALUES
(
    3, 3, 6, 6, 'SELF',
    '女儿', '申请绑定照顾父亲',
    'APPROVED', 1, '2026-10-20 09:00:00.000', '核验通过', 1, '2026-10-20 08:30:00.000'
);

INSERT INTO `elder_family_link` (
    `link_id`, `elder_id`, `child_user_id`, `status`, `current_application_id`, `link_generation`,
    `freeze_reason_code`, `freeze_reason_text`, `frozen_at`, `review_deadline`, `row_version`, `created_at`, `updated_at`
)
VALUES
(
    3, 3, 6, 'FROZEN', 3, 1,
    'SUSPECTED_DISPUTE', '老人其他亲属反映监护权与财产赡养存在争议，社区介入开展5工作日核验',
    '2026-10-25 09:00:00.000', '2026-10-30 18:00:00.000', 2, '2026-10-20 09:00:00.000', '2026-10-25 09:00:00.000'
);

-- ------------------------------------------------------------------------------
-- 7. 档案更正申请闭环 (FZ-01: 张伟申请更正张建国地址与电话)
-- ------------------------------------------------------------------------------
INSERT INTO `elder_correction_request` (
    `request_id`, `elder_id`, `applicant_user_id`, `applicant_link_id`, `applicant_link_generation`,
    `status`, `reason`, `submitted_archive_version`, `submitted_at`, `row_version`, `created_at`, `updated_at`
)
VALUES
(
    1, 1, 4, 1, 1,
    'PENDING_REVIEW', '父亲近期搬迁至同小区3单元201室，且紧急联系电话变更为工作手机，特此申请更新资料',
    1, '2026-10-25 10:00:00.000', 1, '2026-10-25 10:00:00.000', '2026-10-25 10:00:00.000'
);

-- 字段 1: 现居地址变更
INSERT INTO `elder_correction_item` (
    `item_id`, `request_id`, `field_code`, `baseline_value_protected`, `proposed_value_protected`
)
VALUES
(
    1, 1, 'address',
    '朝阳社区幸福路12号院3号楼2单元101', '朝阳社区幸福路12号院3号楼3单元201'
);

-- 字段 2: 紧急联系人电话变更
INSERT INTO `elder_correction_item` (
    `item_id`, `request_id`, `field_code`, `baseline_value_protected`, `proposed_value_protected`
)
VALUES
(
    2, 1, 'emergency_contact_phone',
    '13800000004', '13900000004'
);

-- ------------------------------------------------------------------------------
-- 8. 审计与历史事件样例 (保持一致性记录)
-- ------------------------------------------------------------------------------
INSERT INTO `elder_archive_event` (
    `event_id`, `elder_id`, `event_type`, `old_status`, `new_status`, `reason`, `actor_user_id`, `occurred_at`, `trace_id`
)
VALUES
(1, 1, 'CREATED', NULL, 'ACTIVE', '管理员建档', 1, '2026-10-25 08:10:00.000', 'trace-seed-001'),
(2, 2, 'CREATED', NULL, 'ACTIVE', '管理员建档', 1, '2026-10-25 08:15:00.000', 'trace-seed-002'),
(3, 3, 'CREATED', NULL, 'ACTIVE', '管理员建档', 1, '2026-10-25 08:20:00.000', 'trace-seed-003');

INSERT INTO `elder_family_link_event` (
    `event_id`, `link_id`, `application_id`, `old_status`, `new_status`, `event_type`, `reason`, `actor_user_id`, `occurred_at`, `trace_id`
)
VALUES
(1, 1, 1, 'PENDING', 'ACTIVE', 'APPROVED', '核验通过', 1, '2026-10-25 08:45:00.000', 'trace-seed-004'),
(2, 2, 2, NULL, 'PENDING', 'APPLICATION_SUBMITTED', '子女申请', 5, '2026-10-25 09:30:00.000', 'trace-seed-005'),
(3, 3, 3, 'ACTIVE', 'FROZEN', 'FROZEN', '亲属反映监护争议', 1, '2026-10-25 09:00:00.000', 'trace-seed-006');

