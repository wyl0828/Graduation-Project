-- ==============================================================================
-- 社区居家养老服务系统 演示环境合成种子数据 (M03 ~ M12 核心业务态覆盖)
-- 仅供本机隔离演示库 elderly_care_demo 使用，全部数据均为虚构/合成数据
-- 覆盖场景:
-- 1. 助老服务项目目录 (service_catalog: 助浴、陪诊、保洁、助餐)
-- 2. 健康预警标准规则 (alert_rule_version: 血压、血糖、心率基线)
-- 3. 老人连续健康体征 (vital_record: 包含正常波动与三级高血压异常样本)
-- 4. 自动评估任务与告警 (assessment_task, health_alert, alert_trigger_evidence)
-- 5. 紧急 SOS 求助闭环 (sos_event 触发 -> 原子生成紧急工单 work_order)
-- 6. 社区工单派发与流转 (work_order, assignment_history, action_history)
-- 7. 用药计划与每日任务 (medication_plan, reminder_time, task, checkin)
-- 8. 助老预约与工单联动 (service_reservation, reservation_execution_link)
-- 9. 站内通知与未读投递 (notification_event, notification_delivery)
-- 10. IoT 体征仿真控制器状态 (simulation_control)
-- ==============================================================================

-- 仅允许通过已校验目标库的包装脚本在首次加载时执行。
-- 哨兵行与主键冲突在所有 SQL mode 下都会报错，且发生在首条业务 INSERT 前。
CREATE TEMPORARY TABLE `DEMO_SEED_WRONG_DATABASE_M03_M12` (
    `guard_id` TINYINT NOT NULL PRIMARY KEY
);
INSERT INTO `DEMO_SEED_WRONG_DATABASE_M03_M12` (`guard_id`) VALUES (0);
INSERT INTO `DEMO_SEED_WRONG_DATABASE_M03_M12` (`guard_id`)
VALUES (IF(DATABASE() = 'elderly_care_demo' AND @demo_seed_authorized = 'first-load-synthetic-demo-v1', 1, 0));
DROP TEMPORARY TABLE `DEMO_SEED_WRONG_DATABASE_M03_M12`;

-- ------------------------------------------------------------------------------
-- 1. 助老服务项目目录 (M07)
-- ------------------------------------------------------------------------------
INSERT INTO `service_catalog` (`catalog_id`, `service_type`, `service_name`, `description`, `estimated_duration_min`, `icon_url`, `status`, `sort_order`)
VALUES
(1, 'DAILY_CARE', '上门助浴照料', '专业养老护理员上门协助老人温水擦浴与更衣，提前检查浴室防滑，全程血压心率关注。', 90, '/static/icons/bath.png', 'ACTIVE', 1),
(2, 'MEDICAL_ESCORT', '医院就医陪诊', '陪同前往社区卫生中心或三甲医院，协助挂号、排队取药、引导诊室检查并记录医嘱。', 180, '/static/icons/hospital.png', 'ACTIVE', 2),
(3, 'CLEANING', '居家适老保洁', '针对老年人卧室、卫生间、厨房的深度保洁，清理行走通道杂物消除跌倒隐患。', 120, '/static/icons/clean.png', 'ACTIVE', 3),
(4, 'MEAL_DELIVERY', '社区营养助餐送餐', '社区老年食堂少盐少油健康午餐配送上门，保温箱送达并确认老人进餐状态。', 30, '/static/icons/meal.png', 'ACTIVE', 4);

-- ------------------------------------------------------------------------------
-- 2. 健康预警标准规则基线 (M05)
-- ------------------------------------------------------------------------------
INSERT INTO `alert_rule_version` (`rule_version_id`, `rule_name`, `vital_type`, `rule_json`, `status`, `effective_from`, `effective_to`, `created_by`, `created_at`)
VALUES
(1, '社区高血压通用三级预警规则', 'BLOOD_PRESSURE', '{"systolic_high": 140, "diastolic_high": 90, "urgent_systolic": 160, "urgent_diastolic": 100}', 'ACTIVE', '2026-01-01 00:00:00.000', NULL, 1, '2026-10-25 08:00:00.000'),
(2, '社区老年空腹血糖监测规则', 'BLOOD_SUGAR', '{"fasting_low": 3.9, "fasting_high": 7.0, "urgent_high": 11.1}', 'ACTIVE', '2026-01-01 00:00:00.000', NULL, 1, '2026-10-25 08:00:00.000'),
(3, '社区老年心率异常预警规则', 'HEART_RATE', '{"bradycardia": 55, "tachycardia": 100}', 'ACTIVE', '2026-01-01 00:00:00.000', NULL, 1, '2026-10-25 08:00:00.000');

-- ------------------------------------------------------------------------------
-- 3. 健康体征记录 (M03)
-- 针对已激活老人 张建国 (elder_id=1) 录入连续体征样本，呈现体征曲线
-- ------------------------------------------------------------------------------
INSERT INTO `vital_record` (`vital_id`, `elder_id`, `vital_type`, `value_primary`, `value_secondary`, `unit`, `source_type`, `recorded_at`, `created_by`, `created_at`, `remark`, `idempotency_key`)
VALUES
(1, 1, 'BLOOD_PRESSURE', 128.00, 82.00, 'mmHg', 'MANUAL', '2026-10-21 08:30:00.000', 2, '2026-10-21 08:32:00.000', '早起晨检正常', 'VITAL_SEED_001'),
(2, 1, 'BLOOD_PRESSURE', 132.00, 85.00, 'mmHg', 'MANUAL', '2026-10-22 08:30:00.000', 2, '2026-10-22 08:31:00.000', '状态平稳', 'VITAL_SEED_002'),
(3, 1, 'BLOOD_PRESSURE', 135.00, 86.00, 'mmHg', 'MANUAL', '2026-10-23 08:30:00.000', 2, '2026-10-23 08:33:00.000', '轻度偏高，嘱咐按时服药', 'VITAL_SEED_003'),
(4, 1, 'BLOOD_PRESSURE', 165.00, 98.00, 'mmHg', 'SIMULATED', '2026-10-24 09:15:00.000', 2, '2026-10-24 09:15:10.000', '模拟血压仪自动上传，显著超标', 'VITAL_SEED_004'),
(5, 1, 'BLOOD_SUGAR', 5.60, NULL, 'mmol/L', 'MANUAL', '2026-10-25 07:45:00.000', 2, '2026-10-25 07:50:00.000', '晨起空腹血糖正常', 'VITAL_SEED_005');

-- ------------------------------------------------------------------------------
-- 4. 自动评估任务、健康告警与触发证据快照 (M05)
-- ------------------------------------------------------------------------------
-- 体征 4 经评估任务触发三级高血压告警
INSERT INTO `assessment_task` (`task_id`, `vital_id`, `rule_version_id`, `status`, `attempt_count`, `assessed_at`, `error_message`, `created_at`)
VALUES
(1, 4, 1, 'SUCCEEDED', 1, '2026-10-24 09:15:15.000', NULL, '2026-10-24 09:15:10.000');

INSERT INTO `health_alert` (`alert_id`, `elder_id`, `vital_id`, `assessment_task_id`, `alert_category`, `alert_level`, `status`, `handled_by`, `handled_at`, `handling_result`, `associated_work_order_id`, `created_at`, `updated_at`)
VALUES
(1, 1, 4, 1, 'HIGH_BLOOD_PRESSURE', 'HIGH', 'PENDING', NULL, NULL, NULL, NULL, '2026-10-24 09:15:15.000', '2026-10-24 09:15:15.000');

INSERT INTO `alert_trigger_evidence` (`evidence_id`, `alert_id`, `assessment_task_id`, `metric_name`, `actual_value`, `threshold_value`, `evidence_json`)
VALUES
(1, 1, 1, 'SYSTOLIC', 165.00, 160.00, '{"vital_id": 4, "elder_id": 1, "systolic": 165, "diastolic": 98, "rule_threshold_urgent": 160, "trigger_reason": "收缩压超过紧急阈值 160mmHg"}');

-- ------------------------------------------------------------------------------
-- 5. 紧急 SOS 求助与原子紧急救援工单 (M05 / M06 / M10)
-- ------------------------------------------------------------------------------
-- 工单 1: 由老人长按 SOS 触发派生的紧急救援工单 (当前处于网格员处理中 PROCESSING)
INSERT INTO `work_order` (
    `work_order_id`, `order_no`, `source_type`, `source_reference_id`, `elder_id`, `community_id`,
    `service_type`, `order_title`, `order_description`, `urgency_level`, `status`,
    `current_assignee_id`, `dispatch_time`, `accept_time`, `service_start_time`, `service_finish_time`,
    `service_result_summary`, `result_photo_required`, `reviewer_user_id`, `reviewed_at`, `review_comment`,
    `row_version`, `created_at`, `updated_at`
)
VALUES
(
    1, 'WO202610250001', 'SOS', '1', 1, 1,
    'EMERGENCY_RESCUE', '【紧急求助】张建国长按SOS求助', '老人于家中触发SOS，已自动通知常住网格员李明上门核实，伴随高血压警报。',
    'URGENT', 'PROCESSING',
    2, '2026-10-25 09:00:05.000', '2026-10-25 09:01:20.000', '2026-10-25 09:10:00.000', NULL,
    NULL, 0, NULL, NULL, NULL,
    3, '2026-10-25 09:00:00.000', '2026-10-25 09:10:00.000'
);

-- SOS 求助事实表 (绑定工单 1)
INSERT INTO `sos_event` (`sos_id`, `elder_id`, `user_id`, `status`, `press_duration_ms`, `location_address`, `longitude`, `latitude`, `initial_work_order_id`, `idempotency_key`, `occurred_at`)
VALUES
(1, 1, 3, 'ACCEPTED', 2600, '北京市朝阳区朝阳北路100号院1号楼2单元301室', 116.4800000, 39.9200000, 1, 'SOS_SEED_001', '2026-10-25 09:00:00.000');

-- 工单 1 派发历史与状态流转历史
INSERT INTO `work_order_assignment_history` (`assignment_id`, `work_order_id`, `action_type`, `operator_user_id`, `assigned_to_user_id`, `reason`, `assigned_at`)
VALUES
(1, 1, 'DISPATCH', 1, 2, '系统根据所属社区网格常驻人员自动派发', '2026-10-25 09:00:05.000');

INSERT INTO `work_order_action_history` (`action_id`, `work_order_id`, `operator_user_id`, `operator_role`, `from_status`, `to_status`, `action_name`, `action_payload`, `comment`, `trace_id`, `occurred_at`)
VALUES
(1, 1, 1, 'COMMUNITY_ADMIN', 'PENDING_DISPATCH', 'PENDING_ACCEPT', 'DISPATCH', '{"assignee_id": 2}', '派单至网格员李明', 'TRACE_WO_001', '2026-10-25 09:00:05.000'),
(2, 1, 2, 'GRID_WORKER', 'PENDING_ACCEPT', 'ACCEPTED', 'ACCEPT', NULL, '网格员李明已接单并立即前往', 'TRACE_WO_002', '2026-10-25 09:01:20.000'),
(3, 1, 2, 'GRID_WORKER', 'ACCEPTED', 'PROCESSING', 'START_SERVICE', NULL, '网格员已到达现场，老人神志清醒，正在协助测量血压', 'TRACE_WO_003', '2026-10-25 09:10:00.000');

-- ------------------------------------------------------------------------------
-- 6. 日常用药计划与每日服药打卡 (M04)
-- ------------------------------------------------------------------------------
-- 计划 1: 降压药 络活喜 (苯磺酸氨氯地平片)
INSERT INTO `medication_plan` (`plan_id`, `elder_id`, `medicine_name`, `dosage`, `usage_method`, `status`, `current_version_no`, `created_by_user_id`, `created_at`, `updated_at`)
VALUES
(1, 1, '苯磺酸氨氯地平片 (络活喜)', '5mg (1片)', '口服', 'ACTIVE', 1, 4, '2026-10-01 10:00:00.000', '2026-10-01 10:00:00.000');

INSERT INTO `medication_plan_version` (`version_id`, `plan_id`, `version_no`, `medicine_name`, `dosage`, `frequency_type`, `start_date`, `end_date`, `change_reason`, `created_by_user_id`, `created_at`)
VALUES
(1, 1, 1, '苯磺酸氨氯地平片 (络活喜)', '5mg (1片)', 'DAILY', '2026-10-01', NULL, '初始用药建档', 4, '2026-10-01 10:00:00.000');

INSERT INTO `medication_plan_reminder_time` (`time_id`, `version_id`, `reminder_time`, `time_label`)
VALUES
(1, 1, '08:00:00', '早餐后');

-- 计划 2: 降糖药 盐酸二甲双胍片
INSERT INTO `medication_plan` (`plan_id`, `elder_id`, `medicine_name`, `dosage`, `usage_method`, `status`, `current_version_no`, `created_by_user_id`, `created_at`, `updated_at`)
VALUES
(2, 1, '盐酸二甲双胍缓释片', '0.5g (1片)', '随餐温水送服', 'ACTIVE', 1, 4, '2026-10-01 10:05:00.000', '2026-10-01 10:05:00.000');

INSERT INTO `medication_plan_version` (`version_id`, `plan_id`, `version_no`, `medicine_name`, `dosage`, `frequency_type`, `start_date`, `end_date`, `change_reason`, `created_by_user_id`, `created_at`)
VALUES
(2, 2, 1, '盐酸二甲双胍缓释片', '0.5g (1片)', 'DAILY', '2026-10-01', NULL, '初始用药建档', 4, '2026-10-01 10:05:00.000');

INSERT INTO `medication_plan_reminder_time` (`time_id`, `version_id`, `reminder_time`, `time_label`)
VALUES
(2, 2, '18:00:00', '晚餐后');

-- 今日任务实例 (当前日期 2026-10-25)
-- 任务 1: 早晨降压药 (已按时打卡 CONFIRMED)
INSERT INTO `medication_task` (`task_id`, `elder_id`, `plan_id`, `version_no`, `task_date`, `scheduled_time`, `medicine_name`, `dosage`, `status`, `confirmed_at`, `created_at`)
VALUES
(1, 1, 1, 1, '2026-10-25', '08:00:00', '苯磺酸氨氯地平片 (络活喜)', '5mg (1片)', 'CONFIRMED', '2026-10-25 08:05:30.000', '2026-10-25 00:00:00.000');

INSERT INTO `medication_checkin` (`checkin_id`, `task_id`, `elder_id`, `checkin_time`, `checkin_mode`, `actual_status`, `idempotency_key`)
VALUES
(1, 1, 1, '2026-10-25 08:05:30.000', 'SELF_CONFIRMED', 'TAKEN', 'MED_CHECKIN_SEED_001');

-- 任务 2: 晚间降糖药 (待打卡 PENDING, 供老人端药卡展示与一键服药交互)
INSERT INTO `medication_task` (`task_id`, `elder_id`, `plan_id`, `version_no`, `task_date`, `scheduled_time`, `medicine_name`, `dosage`, `status`, `confirmed_at`, `created_at`)
VALUES
(2, 1, 2, 1, '2026-10-25', '18:00:00', '盐酸二甲双胍缓释片', '0.5g (1片)', 'PENDING', NULL, '2026-10-25 00:00:00.000');

-- ------------------------------------------------------------------------------
-- 7. 助老预约与派生服务工单 (M07 / M06)
-- 子女张伟 (user_id=4) 为老人张建国预约明日居家保洁服务
-- ------------------------------------------------------------------------------
INSERT INTO `service_reservation` (
    `reservation_id`, `reservation_no`, `elder_id`, `applicant_user_id`, `service_type`,
    `service_date`, `slot_code`, `service_address`, `special_notes`, `status`, `reject_reason`,
    `row_version`, `created_at`, `updated_at`
)
VALUES
(
    1, 'RSV202610250001', 1, 4, 'CLEANING',
    '2026-10-26', 'MORNING', '北京市朝阳区朝阳北路100号院1号楼2单元301室', '老人腰腿不便，请重点清洁厨房与卫生间地面，避免滑倒。',
    'CONFIRMED', NULL,
    1, '2026-10-25 10:00:00.000', '2026-10-25 10:30:00.000'
);

-- 派生日常保洁工单 (工单 2: 待派单 PENDING_DISPATCH，供管理员在派单台执行派发)
INSERT INTO `work_order` (
    `work_order_id`, `order_no`, `source_type`, `source_reference_id`, `elder_id`, `community_id`,
    `service_type`, `order_title`, `order_description`, `urgency_level`, `status`,
    `current_assignee_id`, `dispatch_time`, `accept_time`, `service_start_time`, `service_finish_time`,
    `service_result_summary`, `result_photo_required`, `reviewer_user_id`, `reviewed_at`, `review_comment`,
    `row_version`, `created_at`, `updated_at`
)
VALUES
(
    2, 'WO202610250002', 'RESERVATION', '1', 1, 1,
    'CLEANING', '【预约服务】张建国 - 居家适老保洁 (2026-10-26 上午)', '老人腰腿不便，重点清洁厨房与卫生间地面。预约单号 RSV202610250001。',
    'NORMAL', 'PENDING_DISPATCH',
    NULL, NULL, NULL, NULL, NULL,
    NULL, 1, NULL, NULL, NULL,
    1, '2026-10-25 10:30:00.000', '2026-10-25 10:30:00.000'
);

-- 预约与工单执行协调映射
INSERT INTO `reservation_execution_link` (`link_id`, `reservation_id`, `work_order_id`, `created_at`)
VALUES
(1, 1, 2, '2026-10-25 10:30:00.000');

-- ------------------------------------------------------------------------------
-- 8. 站内通知与投递流 (M11)
-- ------------------------------------------------------------------------------
-- 事件 1: 高血压预警通知
INSERT INTO `notification_event` (`event_id`, `policy_scope`, `event_type`, `source_entity_type`, `source_entity_id`, `dedup_key`, `title`, `content`, `payload_json`, `created_at`)
VALUES
(1, 'N01_BUSINESS', 'HEALTH_ALERT_TRIGGERED', 'HEALTH_ALERT', '1', 'NOTIF_DEDUP_001', '【健康预警】张建国血压异常偏高提醒', '系统监测到老人张建国最新收缩压达 165mmHg，已触发三级预警，请及时关注。', '{"alert_id": 1, "elder_id": 1}', '2026-10-24 09:15:16.000');

-- 投递给子女张伟 (未读 0，可在子女端渲染未读提示与红点)
INSERT INTO `notification_delivery` (`delivery_id`, `event_id`, `recipient_user_id`, `channel`, `status`, `retry_count`, `read_status`, `read_at`, `last_attempt_at`, `error_message`, `created_at`)
VALUES
(1, 1, 4, 'IN_APP', 'SENT', 0, 0, NULL, '2026-10-24 09:15:17.000', NULL, '2026-10-24 09:15:16.000');

-- 事件 2: 预约受理成功通知
INSERT INTO `notification_event` (`event_id`, `policy_scope`, `event_type`, `source_entity_type`, `source_entity_id`, `dedup_key`, `title`, `content`, `payload_json`, `created_at`)
VALUES
(2, 'N01_BUSINESS', 'RESERVATION_CONFIRMED', 'SERVICE_RESERVATION', '1', 'NOTIF_DEDUP_002', '【服务预约】居家适老保洁预约已受理', '您为张建国预约的 2026-10-26 上午居家保洁已确认受理，社区即将安排服务人员上门。', '{"reservation_id": 1}', '2026-10-25 10:30:00.000');

-- 投递给子女张伟 (已读 1)
INSERT INTO `notification_delivery` (`delivery_id`, `event_id`, `recipient_user_id`, `channel`, `status`, `retry_count`, `read_status`, `read_at`, `last_attempt_at`, `error_message`, `created_at`)
VALUES
(2, 2, 4, 'IN_APP', 'SENT', 0, 1, '2026-10-25 10:35:00.000', '2026-10-25 10:30:01.000', NULL, '2026-10-25 10:30:00.000');

-- ------------------------------------------------------------------------------
-- 9. IoT 设备体征仿真控制表 (M10)
-- ------------------------------------------------------------------------------
INSERT INTO `simulation_control` (
    `control_id`, `elder_id`, `vital_type`, `status`, `sample_interval_sec`,
    `base_value_primary`, `base_value_secondary`, `volatility_range`, `abnormal_injection_rate`,
    `started_at`, `stopped_at`
)
VALUES
(1, 1, 'BLOOD_PRESSURE', 'STOPPED', 60, 130.00, 82.00, 5.00, 0.050, NULL, NULL);

-- ------------------------------------------------------------------------------
-- 10. 核心安全审计日志表 (M12)
-- ------------------------------------------------------------------------------
INSERT INTO `audit_log` (`audit_id`, `actor_user_id`, `actor_role`, `community_id`, `target_type`, `target_id`, `action`, `result`, `trace_id`, `client_ip`, `details_json`, `occurred_at`)
VALUES
(1, 1, 'COMMUNITY_ADMIN', 1, 'SERVICE_RESERVATION', '1', 'APPROVE_RESERVATION', 'SUCCESS', 'TRACE_AUDIT_001', '127.0.0.1', '{"reservation_no": "RSV202610250001", "action": "审核通过并生成工单 WO202610250002"}', '2026-10-25 10:30:00.000');

