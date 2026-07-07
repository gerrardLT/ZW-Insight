-- ============================================================
-- 字典/配置类种子表 UTF-8 重灌（清表 + 重新插入）
-- 源：deploy/db-init/10_*(sys_config)、11_*(sys_template)、20_*(msg_available_shortcut)
-- 仅含 INSERT 数据，不含 DDL；以 --default-character-set=utf8mb4 导入
-- ============================================================

TRUNCATE TABLE sys_config;
TRUNCATE TABLE sys_template;
TRUNCATE TABLE msg_available_shortcut;

-- ---------- sys_config（系统配置字典） ----------
INSERT INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1001, 'password_min_length', '8', '密码最小长度', 'security', 'NUMBER', '8', '6-20', '用户密码最少字符数', NOW(), NOW()),
(1002, 'password_complexity', 'UPPER_LOWER_NUMBER', '密码复杂度', 'security', 'STRING', 'UPPER_LOWER_NUMBER', NULL, '密码需包含大写字母、小写字母和数字', NOW(), NOW()),
(1003, 'login_fail_lock_count', '5', '登录失败锁定次数', 'security', 'NUMBER', '5', '3-10', '连续登录失败达此次数后锁定账户', NOW(), NOW()),
(1004, 'account_lock_duration', '30', '账户锁定时长(分钟)', 'security', 'NUMBER', '30', '5-1440', '账户锁定后自动解锁时间，单位分钟', NOW(), NOW()),
(1005, 'captcha_enabled', 'true', '启用验证码', 'security', 'BOOLEAN', 'true', NULL, '登录时是否启用图形验证码', NOW(), NOW());

INSERT INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1011, 'approval_timeout_reminder', '24', '审批超时提醒(小时)', 'approval', 'NUMBER', '24', '1-168', '审批任务超过此时间未处理则发送提醒', NOW(), NOW()),
(1012, 'approval_auto_urge_interval', '48', '自动催办间隔(小时)', 'approval', 'NUMBER', '48', '24-720', '自动发送催办通知的间隔时间', NOW(), NOW()),
(1013, 'approval_auto_transfer_enabled', 'false', '启用自动转办', 'approval', 'BOOLEAN', 'false', NULL, '超时后是否自动将审批任务转办给上级', NOW(), NOW());

INSERT INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1021, 'file_max_size', '20', '文件最大大小(MB)', 'file', 'NUMBER', '20', '1-100', '单个上传文件的最大大小，单位MB', NOW(), NOW()),
(1022, 'file_allowed_types', '.doc,.docx,.xls,.xlsx,.pdf,.jpg,.png,.zip', '允许上传文件类型', 'file', 'STRING', '.doc,.docx,.xls,.xlsx,.pdf,.jpg,.png,.zip', NULL, '允许上传的文件扩展名列表', NOW(), NOW()),
(1023, 'attachment_storage_limit', '10', '附件存储上限(GB)', 'file', 'NUMBER', '10', '1-100', '租户附件存储空间上限，单位GB', NOW(), NOW());

INSERT INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1031, 'message_retention_days', '90', '消息保留天数', 'notification', 'NUMBER', '90', '30-365', '站内消息自动清理的保留天数', NOW(), NOW()),
(1032, 'sms_notification_enabled', 'false', '启用短信通知', 'notification', 'BOOLEAN', 'false', NULL, '是否开启短信通知渠道', NOW(), NOW()),
(1033, 'wechat_notification_enabled', 'false', '启用微信通知', 'notification', 'BOOLEAN', 'false', NULL, '是否开启微信公众号/企微通知', NOW(), NOW());

-- ---------- sys_template（导入模板） ----------
INSERT INTO sys_template (id, template_name, template_type, module_code, is_default, created_at, updated_at) VALUES
(2001, '机械台账导入模板', 'IMPORT', 'MACHINE_LEDGER', 1, NOW(), NOW()),
(2002, '劳务花名册导入模板', 'IMPORT', 'LABOR_ROSTER', 1, NOW(), NOW()),
(2003, '人员信息导入模板', 'IMPORT', 'SYS_USER', 1, NOW(), NOW()),
(2004, '供应商导入模板', 'IMPORT', 'SUPPLIER', 1, NOW(), NOW()),
(2005, '材料字典导入模板', 'IMPORT', 'MATERIAL', 1, NOW(), NOW());

-- ---------- msg_available_shortcut（移动端快捷功能） ----------
INSERT INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
(1, '材料入库', 'icon-material-inbound', '/pages/material/inbound', 1, 'ENABLED', NOW()),
(2, '材料出库', 'icon-material-outbound', '/pages/material/outbound', 2, 'ENABLED', NOW()),
(3, '施工日志', 'icon-construction-log', '/pages/site/construction-log', 3, 'ENABLED', NOW()),
(4, '进度反馈', 'icon-progress-feedback', '/pages/site/progress-feedback', 4, 'ENABLED', NOW()),
(5, '质量检查', 'icon-quality-check', '/pages/site/quality-check', 5, 'ENABLED', NOW()),
(6, '安全检查', 'icon-safety-check', '/pages/site/safety-check', 6, 'ENABLED', NOW()),
(7, '开票申请', 'icon-invoice-apply', '/pages/finance/invoice-apply', 7, 'ENABLED', NOW()),
(8, '回款登记', 'icon-payment-received', '/pages/finance/payment-received', 8, 'ENABLED', NOW()),
(9, '付款申请', 'icon-payment-apply', '/pages/finance/payment-apply', 9, 'ENABLED', NOW()),
(10, '项目报销', 'icon-reimbursement', '/pages/finance/reimbursement', 10, 'ENABLED', NOW()),
(11, '个人报销', 'icon-personal-reimbursement', '/pages/finance/personal-reimbursement', 11, 'ENABLED', NOW()),
(12, '收票登记', 'icon-invoice-received', '/pages/finance/invoice-received', 12, 'ENABLED', NOW()),
(13, '其他付款', 'icon-other-payment', '/pages/finance/other-payment', 13, 'ENABLED', NOW()),
(14, '备用金申请', 'icon-reserve-fund-apply', '/pages/finance/reserve-fund-apply', 14, 'ENABLED', NOW()),
(15, '备用金归还', 'icon-reserve-fund-return', '/pages/finance/reserve-fund-return', 15, 'ENABLED', NOW()),
(16, '项目档案', 'icon-project-archive', '/pages/project/archive', 16, 'ENABLED', NOW()),
(17, '审批中心', 'icon-approval', '/pages/approval/index', 17, 'ENABLED', NOW()),
(18, '定位签到', 'icon-sign', '/pages/mine/sign', 18, 'ENABLED', NOW());
