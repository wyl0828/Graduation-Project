-- ==============================================================================
-- Flyway Migration V3: 核心业务物理全量数据库模型 (M03 ~ M12)
-- 涵盖: 健康体征(M03)、预警与SOS(M05)、用药管理(M04)、助老预约(M07)、
--       社区工单(M06)、IoT仿真(M10)、通知与文件(M11)、审计与幂等(M12)
-- 引擎: InnoDB, 字符集: utf8mb4, 排序规则: utf8mb4_0900_ai_ci
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. 跨模块基础设施：通用幂等表与全局审计日志表 (M12)
-- ------------------------------------------------------------------------------

-- (1) 通用幂等记录表
CREATE TABLE `idempotency_record` (
    `record_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `principal_scope` VARCHAR(64) NOT NULL COMMENT '认证主体标识或安全上下文 (用户ID/客户端)',
    `operation_scope` VARCHAR(128) NOT NULL COMMENT '操作范围 (HTTP方法 + 路由模板)',
    `idempotency_key` VARCHAR(80) NOT NULL COMMENT '客户端提供的幂等键',
    `request_hash` CHAR(64) NOT NULL COMMENT '规范化请求正文SHA-256摘要',
    `response_status` INT NOT NULL COMMENT '已提交的原HTTP状态码',
    `response_body` MEDIUMTEXT NOT NULL COMMENT '已提交的原响应JSON报文',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '当时请求的链路追踪ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '初次记录时间',
    `expires_at` DATETIME(3) NOT NULL COMMENT '幂等记录有效保留截止时间',
    PRIMARY KEY (`record_id`),
    UNIQUE KEY `uk_idempotency_scope_key` (`principal_scope`, `operation_scope`, `idempotency_key`),
    INDEX `idx_idempotency_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨模块写操作通用幂等防重表';

-- (2) 全局不可篡改审计日志表
CREATE TABLE `audit_log` (
    `audit_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计主键',
    `actor_user_id` BIGINT NOT NULL COMMENT '操作人账号ID (0表示系统任务)',
    `actor_role` VARCHAR(32) NOT NULL COMMENT '操作人当时角色',
    `community_id` BIGINT NULL COMMENT '操作人所属社区ID',
    `target_type` VARCHAR(64) NOT NULL COMMENT '操作目标类型 (如: ELDER_PROFILE, WORK_ORDER, FAMILY_LINK)',
    `target_id` VARCHAR(64) NOT NULL COMMENT '目标对象主键标识',
    `action` VARCHAR(64) NOT NULL COMMENT '动作代码 (如: CREATE, UPDATE, APPROVE, REJECT, FREEZE)',
    `result` VARCHAR(16) NOT NULL COMMENT '结果: SUCCESS 或 FAILURE',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '全局追踪ID',
    `client_ip` VARCHAR(45) NULL COMMENT '客户端来源IP',
    `details_json` TEXT NULL COMMENT '脱敏后的审计明细',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发生时间',
    PRIMARY KEY (`audit_id`),
    INDEX `idx_audit_target` (`target_type`, `target_id`),
    INDEX `idx_audit_actor_time` (`actor_user_id`, `occurred_at`),
    INDEX `idx_audit_trace` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统通用安全审计日志表';

-- ------------------------------------------------------------------------------
-- 2. 健康档案与体征监测 (M03)
-- ------------------------------------------------------------------------------

CREATE TABLE `vital_record` (
    `vital_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '体征记录ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `vital_type` VARCHAR(32) NOT NULL COMMENT '体征类型: BLOOD_PRESSURE(血压), BLOOD_SUGAR(血糖), HEART_RATE(心率), TEMPERATURE(体温)',
    `value_primary` DECIMAL(6,2) NOT NULL COMMENT '主测量值 (如高压/血糖值/心率)',
    `value_secondary` DECIMAL(6,2) NULL COMMENT '次测量值 (如低压/舒张压)',
    `unit` VARCHAR(16) NOT NULL COMMENT '单位 (如: mmHg, mmol/L, bpm, ℃)',
    `source_type` VARCHAR(24) NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL(网格员/管理员录入), SIMULATED(仿真设备数据)',
    `recorded_at` DATETIME(3) NOT NULL COMMENT '实际采集发生时间',
    `created_by` BIGINT NOT NULL COMMENT '录入人员账号ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '数据入库时间',
    `remark` VARCHAR(255) NULL COMMENT '异常备注或上下文说明',
    `idempotency_key` VARCHAR(80) NULL COMMENT '录入幂等键',
    PRIMARY KEY (`vital_id`),
    INDEX `idx_vital_elder_type_time` (`elder_id`, `vital_type`, `recorded_at`),
    CONSTRAINT `fk_vital_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人健康体征监测记录表';

-- ------------------------------------------------------------------------------
-- 3. 健康预警、SOS 应急求助与 IoT 仿真 (M05 / M10)
-- ------------------------------------------------------------------------------

-- (1) 预警评估规则版本表
CREATE TABLE `alert_rule_version` (
    `rule_version_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '规则版本ID',
    `rule_name` VARCHAR(64) NOT NULL COMMENT '规则名称 (如: 社区老年高血压通用三级预警规则)',
    `vital_type` VARCHAR(32) NOT NULL COMMENT '适用的体征类型',
    `rule_json` TEXT NOT NULL COMMENT '阈值与逻辑配置JSON (低/中/高预警门限)',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 生效中, SUPERSEDED: 已被新版本替代',
    `effective_from` DATETIME(3) NOT NULL COMMENT '生效起始时间',
    `effective_to` DATETIME(3) NULL COMMENT '生效截止时间',
    `created_by` BIGINT NOT NULL COMMENT '配置管理员ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`rule_version_id`),
    INDEX `idx_rule_type_status` (`vital_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康体征预警规则版本表';

-- (2) 体征评估任务表
CREATE TABLE `assessment_task` (
    `task_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评估任务ID',
    `vital_id` BIGINT NOT NULL COMMENT '关联的体征记录ID',
    `rule_version_id` BIGINT NOT NULL COMMENT '评估时固定的规则版本ID (重试不可变更版本)',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, RUNNING, SUCCEEDED, FAILED_RETRYABLE, FAILED_FINAL',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '评估重试次数',
    `assessed_at` DATETIME(3) NULL COMMENT '完成评估时间',
    `error_message` VARCHAR(500) NULL COMMENT '失败错误信息',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`task_id`),
    UNIQUE KEY `uk_assessment_vital` (`vital_id`),
    CONSTRAINT `fk_assess_vital` FOREIGN KEY (`vital_id`) REFERENCES `vital_record` (`vital_id`),
    CONSTRAINT `fk_assess_rule` FOREIGN KEY (`rule_version_id`) REFERENCES `alert_rule_version` (`rule_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='体征自动评估任务表';

-- (3) 健康异常告警表
CREATE TABLE `health_alert` (
    `alert_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '告警ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `vital_id` BIGINT NOT NULL COMMENT '触发该告警的体征ID',
    `assessment_task_id` BIGINT NOT NULL COMMENT '关联评估任务',
    `alert_category` VARCHAR(32) NOT NULL COMMENT '告警类别: HIGH_BLOOD_PRESSURE, LOW_BLOOD_SUGAR, ARRHYTHMIA 等',
    `alert_level` VARCHAR(16) NOT NULL COMMENT '等级: LOW(关注), MEDIUM(预警), HIGH(高度紧急)',
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING: 待处理, PROCESSING: 处理中, CLOSED: 已结案',
    `handled_by` BIGINT NULL COMMENT '结案处理人ID (仅社区管理员可结案)',
    `handled_at` DATETIME(3) NULL COMMENT '处理结案时间',
    `handling_result` VARCHAR(500) NULL COMMENT '处理结论说明 (结案必填)',
    `associated_work_order_id` BIGINT NULL COMMENT '管理员选择派生关联的普通工单ID (可空)',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '告警生成时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`alert_id`),
    INDEX `idx_alert_elder_status` (`elder_id`, `status`),
    INDEX `idx_alert_created_level` (`created_at`, `alert_level`),
    CONSTRAINT `fk_alert_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`),
    CONSTRAINT `fk_alert_vital` FOREIGN KEY (`vital_id`) REFERENCES `vital_record` (`vital_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人健康体征异常预警表';

-- (4) 告警触发证据快照表
CREATE TABLE `alert_trigger_evidence` (
    `evidence_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '证据ID',
    `alert_id` BIGINT NOT NULL COMMENT '告警ID',
    `assessment_task_id` BIGINT NOT NULL COMMENT '评估任务ID',
    `metric_name` VARCHAR(32) NOT NULL COMMENT '超标指标名',
    `actual_value` DECIMAL(6,2) NOT NULL COMMENT '实测值',
    `threshold_value` DECIMAL(6,2) NOT NULL COMMENT '触发阈值',
    `evidence_json` TEXT NULL COMMENT '评估上下文完整快照',
    PRIMARY KEY (`evidence_id`),
    CONSTRAINT `fk_evidence_alert` FOREIGN KEY (`alert_id`) REFERENCES `health_alert` (`alert_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='健康告警触发证据快照表';

-- (5) 老人 SOS 主动求助事实表
CREATE TABLE `sos_event` (
    `sos_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'SOS 主键',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `user_id` BIGINT NOT NULL COMMENT '老人账号ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACCEPTED' COMMENT 'ACCEPTED: 已受理, CANCELLED: 老人误触撤销, VOIDED: 管理员核实终止',
    `press_duration_ms` INT NOT NULL COMMENT '长按触控时长毫秒数 (必须 >= 2000ms)',
    `location_address` VARCHAR(255) NOT NULL COMMENT '求助发生地址 (默认老人档案现居地址)',
    `longitude` DECIMAL(10,7) NULL COMMENT '经度 (如有定位)',
    `latitude` DECIMAL(10,7) NULL COMMENT '纬度 (如有定位)',
    `initial_work_order_id` BIGINT NOT NULL COMMENT '事务内原子派生的初始紧急工单ID',
    `idempotency_key` VARCHAR(80) NOT NULL COMMENT '求助请求幂等键',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '求助触发时间',
    PRIMARY KEY (`sos_id`),
    UNIQUE KEY `uk_sos_idempotency` (`idempotency_key`),
    INDEX `idx_sos_elder_time` (`elder_id`, `occurred_at`),
    CONSTRAINT `fk_sos_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人主动紧急SOS求助事实表';

-- (6) IoT 设备体征仿真控制表 (M10)
CREATE TABLE `simulation_control` (
    `control_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '控制代次ID',
    `elder_id` BIGINT NOT NULL COMMENT '目标模拟老人ID',
    `vital_type` VARCHAR(32) NOT NULL COMMENT '模拟体征类型',
    `status` VARCHAR(16) NOT NULL DEFAULT 'STOPPED' COMMENT 'RUNNING: 运行中, STOPPED: 已停止',
    `sample_interval_sec` INT NOT NULL DEFAULT 60 COMMENT '模拟采样间隔秒数',
    `base_value_primary` DECIMAL(6,2) NOT NULL COMMENT '基准高压/基准值',
    `base_value_secondary` DECIMAL(6,2) NULL COMMENT '基准低压/辅助值',
    `volatility_range` DECIMAL(6,2) NOT NULL DEFAULT 5.00 COMMENT '随机波动幅度',
    `abnormal_injection_rate` DECIMAL(4,3) NOT NULL DEFAULT 0.05 COMMENT '异常值注入概率 (如 0.05 表示 5% 概率注入异常以触发告警)',
    `started_at` DATETIME(3) NULL COMMENT '启动时间',
    `stopped_at` DATETIME(3) NULL COMMENT '停止时间',
    PRIMARY KEY (`control_id`),
    INDEX `idx_sim_elder_status` (`elder_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='IoT设备体征数据仿真控制表';

-- ------------------------------------------------------------------------------
-- 4. 日常用药计划与打卡闭环 (M04)
-- ------------------------------------------------------------------------------

-- (1) 用药计划主表
CREATE TABLE `medication_plan` (
    `plan_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用药计划ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `medicine_name` VARCHAR(128) NOT NULL COMMENT '药品名称 (通用名)',
    `dosage` VARCHAR(64) NOT NULL COMMENT '单次服用剂量 (如: 1片, 10mg)',
    `usage_method` VARCHAR(64) NOT NULL COMMENT '服用方式: 口服, 饭前, 饭后, 睡前',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 正常执行, PAUSED: 暂停, STOPPED: 终止',
    `current_version_no` INT NOT NULL DEFAULT 1 COMMENT '当前生效的不可变版本号',
    `created_by_user_id` BIGINT NOT NULL COMMENT '创建子女/管理员账号ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`plan_id`),
    INDEX `idx_med_plan_elder_status` (`elder_id`, `status`),
    CONSTRAINT `fk_med_plan_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人用药计划总表';

-- (2) 用药计划版本表 (版本不可逆覆盖)
CREATE TABLE `medication_plan_version` (
    `version_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本表ID',
    `plan_id` BIGINT NOT NULL COMMENT '用药计划ID',
    `version_no` INT NOT NULL COMMENT '版本号 (自增 1, 2, 3...)',
    `medicine_name` VARCHAR(128) NOT NULL COMMENT '药品名称快照',
    `dosage` VARCHAR(64) NOT NULL COMMENT '单次剂量快照',
    `frequency_type` VARCHAR(32) NOT NULL DEFAULT 'DAILY' COMMENT '频次类型: DAILY(每日), INTERVAL_DAYS(隔日)',
    `start_date` DATE NOT NULL COMMENT '生效起始日期 (新建/修改默认次日生效)',
    `end_date` DATE NULL COMMENT '截止日期 (空表示长期)',
    `change_reason` VARCHAR(255) NULL COMMENT '变更/调整原因',
    `created_by_user_id` BIGINT NOT NULL COMMENT '操作人账号ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '版本创建时间',
    PRIMARY KEY (`version_id`),
    UNIQUE KEY `uk_med_plan_version` (`plan_id`, `version_no`),
    CONSTRAINT `fk_version_plan` FOREIGN KEY (`plan_id`) REFERENCES `medication_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用药计划不可变版本历史表';

-- (3) 计划提醒时刻明细表
CREATE TABLE `medication_plan_reminder_time` (
    `time_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '时刻ID',
    `version_id` BIGINT NOT NULL COMMENT '所属计划版本ID',
    `reminder_time` TIME NOT NULL COMMENT '计划服药时刻 (格式: 08:00:00, 12:30:00)',
    `time_label` VARCHAR(32) NOT NULL COMMENT '标签: 早餐后, 午餐前, 睡前 等',
    PRIMARY KEY (`time_id`),
    UNIQUE KEY `uk_version_reminder_time` (`version_id`, `reminder_time`),
    CONSTRAINT `fk_reminder_version` FOREIGN KEY (`version_id`) REFERENCES `medication_plan_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用药计划版本每日提醒时刻表';

-- (4) 每日服药任务快照表 (每天由系统定时调度生成)
CREATE TABLE `medication_task` (
    `task_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `plan_id` BIGINT NOT NULL COMMENT '所属计划ID',
    `version_no` INT NOT NULL COMMENT '生成任务时引用的不可变版本号',
    `task_date` DATE NOT NULL COMMENT '任务所属自然日 (YYYY-MM-DD)',
    `scheduled_time` TIME NOT NULL COMMENT '计划服用时间 (HH:mm:ss)',
    `medicine_name` VARCHAR(128) NOT NULL COMMENT '药品快照',
    `dosage` VARCHAR(64) NOT NULL COMMENT '剂量快照',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING: 待服用, CONFIRMED: 已按时打卡, OVER_WINDOW_UNCONFIRMED: 过窗未确认',
    `confirmed_at` DATETIME(3) NULL COMMENT '打卡确认时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务生成入库时间',
    PRIMARY KEY (`task_id`),
    UNIQUE KEY `uk_med_task_identity` (`plan_id`, `task_date`, `scheduled_time`),
    INDEX `idx_med_task_elder_date` (`elder_id`, `task_date`, `status`),
    CONSTRAINT `fk_task_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`),
    CONSTRAINT `fk_task_plan` FOREIGN KEY (`plan_id`) REFERENCES `medication_plan` (`plan_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='每日服药任务实例表';

-- (5) 老人服药打卡记录表 (一任务最多一条有效打卡)
CREATE TABLE `medication_checkin` (
    `checkin_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '打卡记录ID',
    `task_id` BIGINT NOT NULL COMMENT '服药任务ID',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `checkin_time` DATETIME(3) NOT NULL COMMENT '打卡提交时间',
    `checkin_mode` VARCHAR(24) NOT NULL DEFAULT 'SELF_CONFIRMED' COMMENT '打卡方式: SELF_CONFIRMED(老人自主点击), VOICE_CONFIRMED(语音确认)',
    `actual_status` VARCHAR(24) NOT NULL DEFAULT 'TAKEN' COMMENT 'TAKEN: 已服用',
    `idempotency_key` VARCHAR(80) NOT NULL COMMENT '打卡幂等键',
    PRIMARY KEY (`checkin_id`),
    UNIQUE KEY `uk_checkin_task` (`task_id`),
    CONSTRAINT `fk_checkin_task` FOREIGN KEY (`task_id`) REFERENCES `medication_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='老人服药打卡确认记录表';

-- ------------------------------------------------------------------------------
-- 5. 社区工单全流程与责任调度 (M06)
-- ------------------------------------------------------------------------------

-- (1) 社区助老工单主表
CREATE TABLE `work_order` (
    `work_order_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单主键ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '工单编号 (如 WO202610250001, 全局唯一)',
    `source_type` VARCHAR(24) NOT NULL COMMENT '来源类型: SOS(紧急求助), RESERVATION(助老预约), HEALTH_ALERT(体征告警派生), MANUAL(人工录入)',
    `source_reference_id` VARCHAR(64) NOT NULL COMMENT '来源实体ID (sos_id / reservation_id / alert_id)',
    `elder_id` BIGINT NOT NULL COMMENT '服务老人档案ID',
    `community_id` BIGINT NOT NULL COMMENT '归属社区ID',
    `service_type` VARCHAR(32) NOT NULL COMMENT '服务大类: EMERGENCY_RESCUE(紧急救援), DAILY_CARE(日常照料), MEDICAL_ESCORT(就医陪诊), CLEANING(保洁清洁), MEAL_DELIVERY(助餐送餐)',
    `order_title` VARCHAR(128) NOT NULL COMMENT '工单标题/摘要',
    `order_description` TEXT NULL COMMENT '详细要求或现场情况',
    `urgency_level` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '紧急程度: NORMAL(普通), URGENT(紧急, 如SOS)',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_DISPATCH' COMMENT '流转状态机: PENDING_DISPATCH, PENDING_ACCEPT, ACCEPTED, PROCESSING, PENDING_RESULT_REVIEW, CLOSED, VOIDED, TERMINATED_PRE_ACCEPTANCE, SERVICE_STOPPED',
    `current_assignee_id` BIGINT NULL COMMENT '当前负责处理的网格员/护工用户ID',
    `dispatch_time` DATETIME(3) NULL COMMENT '派单时间',
    `accept_time` DATETIME(3) NULL COMMENT '接单响应时间',
    `service_start_time` DATETIME(3) NULL COMMENT '上门服务开始时间',
    `service_finish_time` DATETIME(3) NULL COMMENT '服务执行完成时间',
    `service_result_summary` TEXT NULL COMMENT '执行人员填写的服务总结',
    `result_photo_required` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否必须上传现场照片 (1:是, 0:否)',
    `photo_exemption_reason` VARCHAR(255) NULL COMMENT '免照申请说明 (如涉及老人更衣隐私等合法免照理由)',
    `reviewer_user_id` BIGINT NULL COMMENT '审核结案管理员ID (禁止本人自审本人执行)',
    `reviewed_at` DATETIME(3) NULL COMMENT '审核时间',
    `review_comment` VARCHAR(500) NULL COMMENT '结案审核意见 / 驳回退回重办原因 (必填)',
    `row_version` BIGINT NOT NULL DEFAULT 1 COMMENT '并发控制乐观锁版本号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '建单时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`work_order_id`),
    UNIQUE KEY `uk_work_order_no` (`order_no`),
    INDEX `idx_order_community_status` (`community_id`, `status`),
    INDEX `idx_order_elder_status` (`elder_id`, `status`),
    INDEX `idx_order_assignee_status` (`current_assignee_id`, `status`),
    CONSTRAINT `fk_order_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`),
    CONSTRAINT `fk_order_community` FOREIGN KEY (`community_id`) REFERENCES `sys_community` (`community_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='社区助老服务与应急工单主表';

-- (2) 工单派单与改派历史表
CREATE TABLE `work_order_assignment_history` (
    `assignment_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '历史ID',
    `work_order_id` BIGINT NOT NULL COMMENT '工单ID',
    `action_type` VARCHAR(24) NOT NULL COMMENT '动作: DISPATCH(初始派单), REASSIGN(改派), REJECT_RETURN(拒单退回待派)',
    `operator_user_id` BIGINT NOT NULL COMMENT '操作管理员ID',
    `assigned_to_user_id` BIGINT NOT NULL COMMENT '被指派工作人员ID',
    `reason` VARCHAR(255) NULL COMMENT '派单/改派/拒单原因说明',
    `assigned_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '指派时间',
    PRIMARY KEY (`assignment_id`),
    INDEX `idx_assign_order_time` (`work_order_id`, `assigned_at`),
    CONSTRAINT `fk_assign_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单派单与改派历史记录表';

-- (3) 工单关键节点流转操作历史表
CREATE TABLE `work_order_action_history` (
    `action_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作历史ID',
    `work_order_id` BIGINT NOT NULL COMMENT '工单ID',
    `operator_user_id` BIGINT NOT NULL COMMENT '执行操作者ID',
    `operator_role` VARCHAR(32) NOT NULL COMMENT '操作者角色',
    `from_status` VARCHAR(32) NOT NULL COMMENT '变迁前状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '变迁后状态',
    `action_name` VARCHAR(64) NOT NULL COMMENT '操作名称: ACCEPT, REJECT, START_SERVICE, SUBMIT_RESULT, RETURN_REVIEW, CLOSE, VOID',
    `action_payload` TEXT NULL COMMENT '操作表单数据快照',
    `comment` VARCHAR(500) NULL COMMENT '操作说明/原因',
    `trace_id` VARCHAR(64) NOT NULL COMMENT '链路追踪ID',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间',
    PRIMARY KEY (`action_id`),
    INDEX `idx_action_order_time` (`work_order_id`, `occurred_at`),
    CONSTRAINT `fk_action_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单状态流转节点操作审计历史表';

-- (4) 工作人员工单责任交接表 (交接不推回待派单)
CREATE TABLE `work_order_handoff` (
    `handoff_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '交接ID',
    `work_order_id` BIGINT NOT NULL COMMENT '工单ID',
    `from_user_id` BIGINT NOT NULL COMMENT '原承接人',
    `to_user_id` BIGINT NOT NULL COMMENT '新承接人',
    `status` VARCHAR(24) NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED: 已发起, ACCEPTED: 已接受生效, REJECTED: 已拒绝, CANCELLED: 已取消',
    `handoff_reason` VARCHAR(255) NOT NULL COMMENT '交接原因 (如身体突发不适)',
    `requested_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发起时间',
    `responded_at` DATETIME(3) NULL COMMENT '响应时间',
    `response_comment` VARCHAR(255) NULL COMMENT '接收回复说明',
    PRIMARY KEY (`handoff_id`),
    INDEX `idx_handoff_order` (`work_order_id`),
    CONSTRAINT `fk_handoff_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工单处理责任转移交接记录表';

-- ------------------------------------------------------------------------------
-- 6. 助老服务预约与执行协调 (M07)
-- ------------------------------------------------------------------------------

-- (1) 助老服务项目目录表
CREATE TABLE `service_catalog` (
    `catalog_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '目录ID',
    `service_type` VARCHAR(32) NOT NULL COMMENT '服务大类代码: DAILY_CARE, MEDICAL_ESCORT, CLEANING, MEAL_DELIVERY',
    `service_name` VARCHAR(64) NOT NULL COMMENT '服务项名称 (如: 上门助浴、日间照料、就医陪诊、家庭保洁)',
    `description` VARCHAR(500) NOT NULL COMMENT '服务内容简介与注意事项',
    `estimated_duration_min` INT NOT NULL DEFAULT 60 COMMENT '预计服务时长 (分钟)',
    `icon_url` VARCHAR(255) NULL COMMENT '展示图标路径',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 开放预约, DISABLED: 下架',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
    PRIMARY KEY (`catalog_id`),
    UNIQUE KEY `uk_catalog_type_name` (`service_type`, `service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='助老服务项目目录表';

-- (2) 服务预约主表
CREATE TABLE `service_reservation` (
    `reservation_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '预约ID',
    `reservation_no` VARCHAR(32) NOT NULL COMMENT '预约单号 (如 RSV202610250001)',
    `elder_id` BIGINT NOT NULL COMMENT '老人档案ID',
    `applicant_user_id` BIGINT NOT NULL COMMENT '申请人账号ID (子女或老人本人)',
    `service_type` VARCHAR(32) NOT NULL COMMENT '预约服务类型',
    `service_date` DATE NOT NULL COMMENT '预约服务日期 (YYYY-MM-DD)',
    `slot_code` VARCHAR(24) NOT NULL COMMENT '时段代码: MORNING(上午09:00-11:30), AFTERNOON(下午14:00-17:00)',
    `service_address` VARCHAR(255) NOT NULL COMMENT '上门服务详细地址',
    `special_notes` VARCHAR(500) NULL COMMENT '健康状况及特殊叮嘱',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION' COMMENT 'PENDING_CONFIRMATION, CONFIRMED, REJECTED, CANCELLED, COMPLETED, SERVICE_STOPPED',
    `reject_reason` VARCHAR(255) NULL COMMENT '社区管理员拒绝预约原因说明',
    `row_version` BIGINT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`reservation_id`),
    UNIQUE KEY `uk_reservation_no` (`reservation_no`),
    UNIQUE KEY `uk_elder_service_slot` (`elder_id`, `service_type`, `service_date`, `slot_code`),
    INDEX `idx_rsv_elder_status` (`elder_id`, `status`),
    INDEX `idx_rsv_applicant` (`applicant_user_id`, `created_at`),
    CONSTRAINT `fk_rsv_elder` FOREIGN KEY (`elder_id`) REFERENCES `elder_profile` (`elder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='助老服务预约记录表';

-- (3) 预约与工单执行唯一协调关联表
CREATE TABLE `reservation_execution_link` (
    `link_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联主键',
    `reservation_id` BIGINT NOT NULL COMMENT '预约ID (唯一)',
    `work_order_id` BIGINT NOT NULL COMMENT '工单ID (唯一)',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关联建立时间 (管理员确认预约时同事务生成工单)',
    PRIMARY KEY (`link_id`),
    UNIQUE KEY `uk_exec_reservation` (`reservation_id`),
    UNIQUE KEY `uk_exec_work_order` (`work_order_id`),
    CONSTRAINT `fk_exec_rsv` FOREIGN KEY (`reservation_id`) REFERENCES `service_reservation` (`reservation_id`),
    CONSTRAINT `fk_exec_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='助老服务预约与派生工单唯一映射关联表';

-- (4) 预约确认后取消审批表 (未接单前申请取消)
CREATE TABLE `reservation_cancel_request` (
    `cancel_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '取消单ID',
    `reservation_id` BIGINT NOT NULL COMMENT '预约单ID',
    `applicant_user_id` BIGINT NOT NULL COMMENT '申请取消人ID',
    `reason` VARCHAR(255) NOT NULL COMMENT '取消原因说明',
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW, APPROVED, REJECTED',
    `reviewer_user_id` BIGINT NULL COMMENT '审批管理员ID',
    `reviewed_at` DATETIME(3) NULL COMMENT '审批时间',
    `review_reason` VARCHAR(255) NULL COMMENT '审批意见',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交取消申请时间',
    PRIMARY KEY (`cancel_id`),
    INDEX `idx_cancel_rsv` (`reservation_id`, `status`),
    CONSTRAINT `fk_cancel_rsv` FOREIGN KEY (`reservation_id`) REFERENCES `service_reservation` (`reservation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='已确认预约取消审批表';

-- (5) 服务中止申请表 (已接单/处理中工单走独立中止审批)
CREATE TABLE `service_stop_request` (
    `stop_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '中止单ID',
    `reservation_id` BIGINT NOT NULL COMMENT '预约单ID',
    `work_order_id` BIGINT NOT NULL COMMENT '工单ID',
    `applicant_user_id` BIGINT NOT NULL COMMENT '申请人ID',
    `stop_reason` VARCHAR(500) NOT NULL COMMENT '服务中止详细原因 (如老人突发情况送医)',
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT 'PENDING_REVIEW, APPROVED, REJECTED',
    `reviewer_user_id` BIGINT NULL COMMENT '审批管理员ID',
    `reviewed_at` DATETIME(3) NULL COMMENT '审批时间',
    `review_comment` VARCHAR(255) NULL COMMENT '审批意见',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    PRIMARY KEY (`stop_id`),
    INDEX `idx_stop_order` (`work_order_id`, `status`),
    CONSTRAINT `fk_stop_rsv` FOREIGN KEY (`reservation_id`) REFERENCES `service_reservation` (`reservation_id`),
    CONSTRAINT `fk_stop_order` FOREIGN KEY (`work_order_id`) REFERENCES `work_order` (`work_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务执行中止审批申请表';

-- ------------------------------------------------------------------------------
-- 7. 业务通知与文件证据管理 (M11)
-- ------------------------------------------------------------------------------

-- (1) 逻辑业务通知事件表
CREATE TABLE `notification_event` (
    `event_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知事件ID',
    `policy_scope` VARCHAR(32) NOT NULL COMMENT '策略范围: N01_BUSINESS(业务事件), FZ02_SECURITY(安全通知)',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型: MEDICATION_REMINDER, SOS_ALERT, ORDER_DISPATCHED, ORDER_FINISHED',
    `source_entity_type` VARCHAR(32) NOT NULL COMMENT '关联源类型: MEDICATION_TASK, SOS_EVENT, WORK_ORDER, PHONE_CHANGE',
    `source_entity_id` VARCHAR(64) NOT NULL COMMENT '关联源ID',
    `dedup_key` VARCHAR(128) NOT NULL COMMENT '业务去重键',
    `title` VARCHAR(128) NOT NULL COMMENT '通知标题',
    `content` TEXT NOT NULL COMMENT '通知详细文本',
    `payload_json` TEXT NULL COMMENT '额外参数',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '事件创建时间',
    PRIMARY KEY (`event_id`),
    UNIQUE KEY `uk_notify_dedup` (`dedup_key`),
    INDEX `idx_notify_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统逻辑通知事件表';

-- (2) 逐收件人通知投递记录表 (Outbox 投递队列)
CREATE TABLE `notification_delivery` (
    `delivery_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '投递流水ID',
    `event_id` BIGINT NOT NULL COMMENT '关联通知事件ID',
    `recipient_user_id` BIGINT NOT NULL COMMENT '收件人用户ID',
    `channel` VARCHAR(24) NOT NULL DEFAULT 'IN_APP' COMMENT '渠道: IN_APP(站内短轮询), SMS(短信通知)',
    `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING, SENDING, SENT, RETRY_WAIT, SKIPPED, FAILED',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `read_status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '站内信是否已读 (1:已读, 0:未读)',
    `read_at` DATETIME(3) NULL COMMENT '阅读时间',
    `last_attempt_at` DATETIME(3) NULL COMMENT '最后一次投递尝试时间',
    `error_message` VARCHAR(255) NULL COMMENT '发送失败原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`delivery_id`),
    UNIQUE KEY `uk_delivery_event_user_channel` (`event_id`, `recipient_user_id`, `channel`),
    INDEX `idx_delivery_user_read` (`recipient_user_id`, `read_status`, `created_at`),
    INDEX `idx_delivery_poll` (`status`, `retry_count`),
    CONSTRAINT `fk_delivery_event` FOREIGN KEY (`event_id`) REFERENCES `notification_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='逐收件人通知投递记录表';

-- (3) 业务文件资产表 (元数据与版本管理)
CREATE TABLE `file_asset` (
    `asset_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资产ID',
    `asset_uuid` CHAR(36) NOT NULL COMMENT '不可篡改对外标识',
    `category` VARCHAR(32) NOT NULL COMMENT '文件类别: WORK_ORDER_PHOTO(工单现场证据), PROFILE_AVATAR(头像), ATTACHMENT(文档)',
    `created_by_user_id` BIGINT NOT NULL COMMENT '上传人用户ID',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, RETIRED, FAILED',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '资产创建时间',
    PRIMARY KEY (`asset_id`),
    UNIQUE KEY `uk_asset_uuid` (`asset_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控文件资产主表';

-- (4) 文件资产物理版本表
CREATE TABLE `file_asset_version` (
    `version_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本主键',
    `asset_id` BIGINT NOT NULL COMMENT '所属资产ID',
    `version_no` INT NOT NULL DEFAULT 1 COMMENT '版本号',
    `storage_path` VARCHAR(255) NOT NULL COMMENT '本地受控相对存储路径',
    `original_filename` VARCHAR(128) NOT NULL COMMENT '原始文件名',
    `file_size_bytes` BIGINT NOT NULL COMMENT '文件字节大小',
    `mime_type` VARCHAR(64) NOT NULL COMMENT 'MIME类型 (如 image/jpeg)',
    `sha256_hash` CHAR(64) NOT NULL COMMENT '文件内容SHA-256校验摘要',
    `status` VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE: 有效, SUPERSEDED: 已替代, QUARANTINED: 隔离',
    `uploaded_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传入库时间',
    PRIMARY KEY (`version_id`),
    UNIQUE KEY `uk_asset_version` (`asset_id`, `version_no`),
    CONSTRAINT `fk_file_version_asset` FOREIGN KEY (`asset_id`) REFERENCES `file_asset` (`asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='受控文件资产物理版本与校验表';

-- (5) 业务实体与文件证据权威绑定表
CREATE TABLE `business_file_link` (
    `link_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联主键',
    `business_type` VARCHAR(32) NOT NULL COMMENT '业务类型: WORK_ORDER(工单拍照), CORRECTION_REQUEST(更正凭据)',
    `business_id` VARCHAR(64) NOT NULL COMMENT '业务实体主键ID (如 work_order_id)',
    `asset_id` BIGINT NOT NULL COMMENT '文件资产ID',
    `version_id` BIGINT NOT NULL COMMENT '固化引用的具体文件版本ID',
    `tag` VARCHAR(32) NOT NULL DEFAULT 'SERVICE_PROOF' COMMENT '标签: BEFORE_SERVICE, AFTER_SERVICE, SERVICE_PROOF',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定时间',
    PRIMARY KEY (`link_id`),
    INDEX `idx_biz_file_lookup` (`business_type`, `business_id`),
    CONSTRAINT `fk_biz_file_asset` FOREIGN KEY (`asset_id`) REFERENCES `file_asset` (`asset_id`),
    CONSTRAINT `fk_biz_file_version` FOREIGN KEY (`version_id`) REFERENCES `file_asset_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='业务事实与文件版本唯一绑定表';
