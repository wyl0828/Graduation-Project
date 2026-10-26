-- ==============================================================================
-- Flyway Migration V2: M01 身份认证与权限 & M02 老人档案与亲情绑定物理模型
-- 遵守总体设计基线 v1.1、跨模块设计与 E-01/E-02 专项设计
-- 引擎: InnoDB, 字符集: utf8mb4, 排序规则: utf8mb4_0900_ai_ci
-- ==============================================================================

-- 1. 全局单例维护门闩表 (D1 架构决策: Level 00 互斥门闩)
CREATE TABLE `environment_maintenance_gate` (
    `gate_id` INT NOT NULL DEFAULT 1 COMMENT '单行约束键，固定为1',
    `status` VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN: 允许普通业务写; MAINTENANCE: 排他维护/重置/联合备份',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '状态变更时间',
    PRIMARY KEY (`gate_id`),
    CONSTRAINT `chk_env_gate_single_row` CHECK (`gate_id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演示环境全局维护门闩单行表';

INSERT INTO `environment_maintenance_gate` (`gate_id`, `status`, `updated_at`)
VALUES (1, 'OPEN', CURRENT_TIMESTAMP(3));

-- 2. 社区基础数据表 (M01/M02 社区管辖范围权威实体)
CREATE TABLE `sys_community` (
    `community_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '社区唯一ID',
    `community_code` VARCHAR(32) NOT NULL COMMENT '社区编码 (业务唯一)',
    `community_name` VARCHAR(64) NOT NULL COMMENT '社区名称',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 正常, DISABLED: 停用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`community_id`),
    UNIQUE KEY `uk_community_code` (`community_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区管辖区域表';

-- 3. 业务账号表 (M01: 统一用户体系)
CREATE TABLE `sys_user` (
    `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '稳定用户账号ID，换号不变更',
    `account_kind` VARCHAR(16) NOT NULL COMMENT '账号大类: FAMILY 或 STAFF',
    `phone_normalized` VARCHAR(32) NOT NULL COMMENT '规范化11位手机号 (无空格横杠)',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'PBKDF2-HMAC-SHA-256版本化哈希',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 正常, DISABLED: 停用',
    `password_changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次密码变更时间',
    `disabled_at` DATETIME(3) NULL COMMENT '账号停用时间',
    `disabled_reason` VARCHAR(255) NULL COMMENT '账号停用原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_sys_user_phone` (`phone_normalized`),
    INDEX `idx_sys_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统业务账号表';

-- 4. 角色分配表 (M01: 授权与社区边界)
CREATE TABLE `sys_user_role` (
    `assignment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分配唯一ID',
    `user_id` BIGINT NOT NULL COMMENT '账号ID',
    `role_code` VARCHAR(32) NOT NULL COMMENT '角色代码: ELDER, CHILD, GRID_WORKER, CARE_WORKER, COMMUNITY_ADMIN',
    `community_id` BIGINT NULL COMMENT '工作人员角色所属社区范围，家庭角色为NULL',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 有效, REVOKED: 已撤销',
    `granted_by` BIGINT NOT NULL COMMENT '授权管理员ID',
    `granted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '授权时间',
    `revoked_at` DATETIME(3) NULL COMMENT '撤销时间',
    `revoked_by` BIGINT NULL COMMENT '撤销人ID',
    `revoke_reason` VARCHAR(255) NULL COMMENT '撤销原因',
    PRIMARY KEY (`assignment_id`),
    INDEX `idx_user_role_user_status` (`user_id`, `status`),
    INDEX `idx_user_role_comm_role` (`community_id`, `role_code`),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`),
    CONSTRAINT `fk_user_role_community` FOREIGN KEY (`community_id`) REFERENCES `sys_community` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色分配表';

-- 5. 服务端会话管理表 (M01: 可即时撤销的不透明令牌会话)
CREATE TABLE `auth_session` (
    `session_id` CHAR(36) NOT NULL COMMENT 'UUID会话主键',
    `user_id` BIGINT NOT NULL COMMENT '账号ID',
    `token_hash` BINARY(32) NOT NULL COMMENT '随机令牌的SHA-256摘要',
    `client_type` VARCHAR(16) NOT NULL COMMENT '客户端: WEB 或 MINIAPP',
    `active_assignment_id` BIGINT NULL COMMENT '当前生效的角色分配ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '会话建立时间 (绝对有效期起点)',
    `last_activity_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最后一次主动业务请求时间',
    `absolute_expires_at` DATETIME(3) NOT NULL COMMENT '绝对过期截止时间',
    `revoked_at` DATETIME(3) NULL COMMENT '撤销时间',
    `revoke_reason` VARCHAR(40) NULL COMMENT '撤销原因: LOGOUT, PASSWORD_RESET, PHONE_CHANGED, ROLE_CHANGED, ACCOUNT_DISABLED, SECURITY_EVENT',
    `csrf_token_hash` BINARY(32) NULL COMMENT 'Web端CSRF摘要',
    PRIMARY KEY (`session_id`),
    UNIQUE KEY `uk_session_token_hash` (`token_hash`),
    INDEX `idx_session_user_revoked` (`user_id`, `revoked_at`),
    INDEX `idx_session_abs_expires` (`absolute_expires_at`),
    CONSTRAINT `fk_auth_session_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务端会话管理表';

-- 6. 老人业务档案表 (M02: 唯一核心档案实体)
CREATE TABLE `elder_profile` (
    `elder_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '老人业务主键',
    `archive_no` VARCHAR(32) NOT NULL COMMENT '业务档案编号 (全局唯一)',
    `community_id` BIGINT NOT NULL COMMENT '管辖社区ID',
    `name` VARCHAR(64) NOT NULL COMMENT '老人姓名',
    `sex` VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '性别: MALE, FEMALE, UNKNOWN',
    `birth_date` DATE NULL COMMENT '出生日期',
    `birth_date_unknown_reason` VARCHAR(255) NULL COMMENT '出生日期缺失原因',
    `address` VARCHAR(255) NOT NULL COMMENT '现居详细地址',
    `contact_phone` VARCHAR(32) NULL COMMENT '联系电话',
    `contact_phone_missing_reason` VARCHAR(255) NULL COMMENT '联系电话缺失原因',
    `emergency_contact_name` VARCHAR(64) NULL COMMENT '紧急联系人姓名',
    `emergency_contact_relation` VARCHAR(32) NULL COMMENT '紧急联系人关系',
    `emergency_contact_phone` VARCHAR(32) NULL COMMENT '紧急联系人电话',
    `emergency_contact_name_missing_reason` VARCHAR(255) NULL COMMENT '紧急联系人姓名缺失原因',
    `emergency_contact_relation_missing_reason` VARCHAR(255) NULL COMMENT '紧急联系人关系缺失原因',
    `emergency_contact_phone_missing_reason` VARCHAR(255) NULL COMMENT '紧急联系人电话缺失原因',
    `id_number_ciphertext` VARBINARY(512) NULL COMMENT '加密身份证号',
    `id_number_lookup_hmac` CHAR(64) NULL COMMENT '规范化身份证号HMAC-SHA256查重摘要',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 正常在档, INACTIVE: 已停用',
    `status_reason` VARCHAR(255) NULL COMMENT '最近一次状态变更原因摘要',
    `row_version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `created_by` BIGINT NOT NULL COMMENT '创建管理员ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_by` BIGINT NOT NULL COMMENT '更新人ID',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`elder_id`),
    UNIQUE KEY `uk_elder_archive_no` (`archive_no`),
    UNIQUE KEY `uk_elder_id_number_hmac` (`id_number_lookup_hmac`),
    INDEX `idx_elder_comm_status` (`community_id`, `status`),
    INDEX `idx_elder_name_birth` (`name`, `birth_date`),
    INDEX `idx_elder_phone` (`contact_phone`),
    CONSTRAINT `fk_elder_community` FOREIGN KEY (`community_id`) REFERENCES `sys_community` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人业务档案表';

-- 7. 老人激活码签发记录表 (M01: 6位一次性激活码凭证)
CREATE TABLE `elder_activation_ticket` (
    `ticket_id` CHAR(36) NOT NULL COMMENT '非秘密定位符UUID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `code_mac` BINARY(32) NOT NULL COMMENT '6位数字激活码HMAC-SHA256摘要',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED: 已签发, USED: 已使用, LOCKED: 已锁死, EXPIRED: 已过期, SUPERSEDED: 已作废',
    `issued_by` BIGINT NOT NULL COMMENT '签发管理员ID',
    `issue_mode` VARCHAR(24) NOT NULL DEFAULT 'IN_PERSON' COMMENT 'IN_PERSON: 当面交付, SPECIAL_PROXY: 特殊代理代领',
    `identity_check_result` VARCHAR(16) NOT NULL DEFAULT 'VERIFIED' COMMENT '核验结论: VERIFIED 等',
    `special_reason` VARCHAR(500) NULL COMMENT '特殊代理代领说明',
    `proxy_basis_type` VARCHAR(40) NULL COMMENT '代理依据元数据',
    `issued_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '签发时间',
    `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间 (24小时)',
    `failed_attempts` INT NOT NULL DEFAULT 0 COMMENT '输错计数器 (连续输错5次锁死)',
    `used_at` DATETIME(3) NULL COMMENT '消费时间',
    `used_by_user_id` BIGINT NULL COMMENT '消费账号ID',
    `used_idempotency_key` VARCHAR(80) NULL COMMENT '激活消费幂等键',
    `invalidated_at` DATETIME(3) NULL COMMENT '失效时间',
    `invalidation_reason` VARCHAR(255) NULL COMMENT '失效原因',
    PRIMARY KEY (`ticket_id`),
    INDEX `idx_ticket_elder_status` (`elder_id`, `status`),
    CONSTRAINT `fk_ticket_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人账号激活码签发记录表';

-- 8. 老人业务档案与账号关联表 (M02权威模型，M01激活时写入)
CREATE TABLE `elder_identity_link` (
    `link_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID (双向唯一)',
    `user_id` BIGINT NOT NULL COMMENT '老人登录账号ID (双向唯一)',
    `linked_by_activation_ticket_id` CHAR(36) NOT NULL COMMENT '消费的一次性ticket_id (唯一)',
    `linked_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定时间',
    PRIMARY KEY (`link_id`),
    UNIQUE KEY `uk_identity_elder` (`elder_id`),
    UNIQUE KEY `uk_identity_user` (`user_id`),
    UNIQUE KEY `uk_identity_ticket` (`linked_by_activation_ticket_id`),
    CONSTRAINT `fk_identity_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`),
    CONSTRAINT `fk_identity_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`),
    CONSTRAINT `fk_identity_ticket` FOREIGN KEY (`linked_by_activation_ticket_id`) REFERENCES `elder_activation_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人业务档案与登录账号关联表';

-- 9. 亲情绑定申请历史表 (M02: 申请审批流水)
CREATE TABLE `family_link_application` (
    `application_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `child_user_id` BIGINT NOT NULL COMMENT '申请子女账号ID',
    `submitted_by_user_id` BIGINT NOT NULL COMMENT '提交人账号ID',
    `submission_mode` VARCHAR(24) NOT NULL DEFAULT 'SELF' COMMENT 'SELF: 本人申请, STAFF_ASSISTED: 管理员协助',
    `relationship` VARCHAR(32) NOT NULL COMMENT '称谓关系 (如: 儿子、女儿、监护人)',
    `apply_reason` VARCHAR(500) NOT NULL COMMENT '申请事由说明',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING: 待审核, APPROVED: 已批准, REJECTED: 已拒绝',
    `evidence_metadata` TEXT NULL COMMENT '核验辅助元数据',
    `reviewer_user_id` BIGINT NULL COMMENT '审核管理员ID',
    `reviewed_at` DATETIME(3) NULL COMMENT '审核时间',
    `decision_reason` VARCHAR(500) NULL COMMENT '审批意见/拒绝原因',
    `link_generation` BIGINT NOT NULL DEFAULT 1 COMMENT '授权代次',
    `idempotency_key` VARCHAR(80) NULL COMMENT '提交幂等键',
    `request_hash` CHAR(64) NULL COMMENT '规范化请求哈希',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    PRIMARY KEY (`application_id`),
    INDEX `idx_app_elder_child_status` (`elder_id`, `child_user_id`, `status`),
    INDEX `idx_app_child_created` (`child_user_id`, `created_at`),
    CONSTRAINT `fk_app_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`),
    CONSTRAINT `fk_app_child` FOREIGN KEY (`child_user_id`) REFERENCES `sys_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='亲情绑定申请历史表';

-- 10. 老人与子女亲情绑定关系表 (M02: 权威授权关系状态)
CREATE TABLE `elder_family_link` (
    `link_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关系ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `child_user_id` BIGINT NOT NULL COMMENT '子女账号ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, ACTIVE, FROZEN, REJECTED, UNLINKED, REVOKED',
    `current_application_id` BIGINT NULL COMMENT '当前关联申请版本',
    `link_generation` BIGINT NOT NULL DEFAULT 1 COMMENT '关系代次计数',
    `freeze_reason_code` VARCHAR(32) NULL COMMENT '争议冻结代码',
    `freeze_reason_text` VARCHAR(255) NULL COMMENT '争议冻结原因说明',
    `frozen_at` DATETIME(3) NULL COMMENT '冻结时间',
    `review_deadline` DATETIME(3) NULL COMMENT '5工作日复核截止时间',
    `row_version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`link_id`),
    UNIQUE KEY `uk_family_elder_child` (`elder_id`, `child_user_id`),
    INDEX `idx_family_child_status` (`child_user_id`, `status`),
    CONSTRAINT `fk_family_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`),
    CONSTRAINT `fk_family_child` FOREIGN KEY (`child_user_id`) REFERENCES `sys_user` (`user_id`),
    CONSTRAINT `fk_family_app` FOREIGN KEY (`current_application_id`) REFERENCES `family_link_application` (`application_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人与子女亲情绑定关系表';

-- 11. 老人档案更正申请表 (M02 / FZ-01 闭环)
CREATE TABLE `elder_correction_request` (
    `request_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '更正申请ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `applicant_user_id` BIGINT NOT NULL COMMENT '提交申请的子女账号ID',
    `applicant_link_id` BIGINT NOT NULL COMMENT '提交时的关系ID',
    `applicant_link_generation` BIGINT NOT NULL COMMENT '提交时的授权代次',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW: 待审核, APPROVED_APPLIED: 已批准并应用, REJECTED: 已拒绝',
    `reason` VARCHAR(500) NOT NULL COMMENT '更正申请理由',
    `submitted_archive_version` BIGINT NOT NULL COMMENT '提交时老人档案乐观锁版本',
    `submitted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
    `reviewer_user_id` BIGINT NULL COMMENT '审核管理员ID',
    `reviewed_at` DATETIME(3) NULL COMMENT '审核时间',
    `decision_reason` VARCHAR(500) NULL COMMENT '审批决定说明',
    `block_code` VARCHAR(32) NULL COMMENT '阻断代码 (如 FIELD_VALUE_CONFLICT)',
    `block_at` DATETIME(3) NULL COMMENT '阻断记录时间',
    `row_version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    `idempotency_key` VARCHAR(80) NULL COMMENT '幂等键',
    `request_hash` CHAR(64) NULL COMMENT '请求摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`request_id`),
    INDEX `idx_corr_elder_status` (`elder_id`, `status`, `submitted_at`),
    INDEX `idx_corr_applicant` (`applicant_user_id`, `submitted_at`),
    CONSTRAINT `fk_corr_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`),
    CONSTRAINT `fk_corr_applicant` FOREIGN KEY (`applicant_user_id`) REFERENCES `sys_user` (`user_id`),
    CONSTRAINT `fk_corr_link` FOREIGN KEY (`applicant_link_id`) REFERENCES `elder_family_link` (`link_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人档案更正申请表';

-- 12. 档案更正申请明细项表 (FZ-01: 固化 baseline 快照与对比证据)
CREATE TABLE `elder_correction_item` (
    `item_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '明细主键',
    `request_id` BIGINT NOT NULL COMMENT '所属申请ID',
    `field_code` VARCHAR(64) NOT NULL COMMENT '更正字段名 (如: address, contact_phone, emergency_contact_phone 等)',
    `baseline_value_protected` TEXT NULL COMMENT '申请提交时刻该字段在数据库中的规范化基准值快照',
    `proposed_value_protected` TEXT NULL COMMENT '子女建议更正的新值',
    `before_value_protected` TEXT NULL COMMENT '实际批准修改生效前值',
    `after_value_protected` TEXT NULL COMMENT '实际批准修改生效后值',
    `applied_at` DATETIME(3) NULL COMMENT '字段实际应用更新时间',
    PRIMARY KEY (`item_id`),
    UNIQUE KEY `uk_corr_item_field` (`request_id`, `field_code`),
    CONSTRAINT `fk_corr_item_req` FOREIGN KEY (`request_id`) REFERENCES `elder_correction_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='档案更正申请明细项表';

-- 13. 老人档案关键生命周期事件表 (领域事件追加日志)
CREATE TABLE `elder_archive_event` (
    `event_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型: CREATED, STATUS_CHANGED, INFO_UPDATED, CORRECTION_APPLIED',
    `old_status` VARCHAR(16) NULL COMMENT '旧状态',
    `new_status` VARCHAR(16) NULL COMMENT '新状态',
    `reason` VARCHAR(255) NULL COMMENT '变更原因',
    `actor_user_id` BIGINT NOT NULL COMMENT '操作人ID',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    `metadata` TEXT NULL COMMENT '扩展上下文',
    PRIMARY KEY (`event_id`),
    INDEX `idx_arch_event_elder_time` (`elder_id`, `occurred_at`),
    CONSTRAINT `fk_arch_event_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人档案生命周期与关键事件表';

-- 14. 亲情关系状态变更历史事件表 (领域事件追加日志)
CREATE TABLE `elder_family_link_event` (
    `event_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '事件ID',
    `link_id` BIGINT NOT NULL COMMENT '关系ID',
    `application_id` BIGINT NULL COMMENT '关联申请ID',
    `old_status` VARCHAR(16) NULL COMMENT '旧状态',
    `new_status` VARCHAR(16) NOT NULL COMMENT '新状态',
    `event_type` VARCHAR(32) NOT NULL COMMENT '事件类型: APPLICATION_SUBMITTED, APPROVED, REJECTED, UNLINKED, FROZEN, UNFROZEN, REVOKED',
    `reason` VARCHAR(255) NULL COMMENT '操作原因',
    `evidence_metadata` TEXT NULL COMMENT '核验凭据元数据',
    `actor_user_id` BIGINT NOT NULL COMMENT '操作人ID',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件发生时间',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    PRIMARY KEY (`event_id`),
    INDEX `idx_link_event_link_time` (`link_id`, `occurred_at`),
    CONSTRAINT `fk_link_event_link` FOREIGN KEY (`link_id`) REFERENCES `elder_family_link` (`link_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='亲情关系状态变更历史事件表';

-- 15. 短信与验证码安全挑战表 (M01: 注册、激活、恢复、换号挑战)
CREATE TABLE `phone_verification_challenge` (
    `challenge_id` CHAR(36) NOT NULL COMMENT '挑战UUID',
    `user_id` BIGINT NULL COMMENT '已知用户ID (注册时可为空)',
    `phone_normalized` VARCHAR(32) NOT NULL COMMENT '目标手机号',
    `purpose` VARCHAR(32) NOT NULL COMMENT '用途: REGISTER, ACTIVATION, PASSWORD_RESET, PHONE_CHANGE_OLD, PHONE_CHANGE_NEW',
    `related_request_id` CHAR(36) NULL COMMENT '关联业务ID (如ticket_id或换号request_id)',
    `code_mac` BINARY(32) NOT NULL COMMENT '验证码HMAC摘要',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
    `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '输错计数',
    `consumed_at` DATETIME(3) NULL COMMENT '消费时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING: 待验证, CONSUMED: 已消费, EXPIRED: 已过期, LOCKED: 已锁定, INVALIDATED: 已作废',
    PRIMARY KEY (`challenge_id`),
    INDEX `idx_challenge_phone_purpose` (`phone_normalized`, `purpose`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信与验证码安全挑战表';

-- 16. 手机号变更申请表 (M01 / FZ-02: 换号与安全通知状态)
CREATE TABLE `phone_change_request` (
    `request_id` CHAR(36) NOT NULL COMMENT '换号申请UUID',
    `user_id` BIGINT NOT NULL COMMENT '申请账号ID',
    `old_phone` VARCHAR(32) NOT NULL COMMENT '原手机号快照',
    `new_phone` VARCHAR(32) NOT NULL COMMENT '新目标手机号',
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING_OLD_VERIFY' COMMENT 'PENDING_OLD_VERIFY, PENDING_MANUAL_REVIEW, PENDING_NEW_VERIFY, COMPLETED, REJECTED, EXPIRED',
    `old_phone_verified_at` DATETIME(3) NULL COMMENT '原号验证完成时间',
    `manual_review_status` VARCHAR(16) NULL COMMENT '人工核验结论',
    `manual_reviewer_id` BIGINT NULL COMMENT '人工核验管理员ID',
    `manual_reviewed_at` DATETIME(3) NULL COMMENT '人工核验时间',
    `manual_review_reason` VARCHAR(500) NULL COMMENT '人工核验依据说明',
    `new_phone_verified_at` DATETIME(3) NULL COMMENT '新号验证完成时间',
    `completed_at` DATETIME(3) NULL COMMENT '换号生效完成时间',
    `expires_at` DATETIME(3) NOT NULL COMMENT '申请过期时间 (30分钟)',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发起时间',
    PRIMARY KEY (`request_id`),
    INDEX `idx_phone_change_user` (`user_id`, `status`),
    CONSTRAINT `fk_phone_change_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='手机号变更申请表';
