-- ============================================
-- P1 系统完整度增强 - 数据库迁移脚本
-- 版本: V2026_07
-- 功能: 数据权限隔离、项目成员管理、系统配置、
--       机械工作量结算、审批数据快照与回滚、多租户增强
-- ============================================

-- ============ 1. 角色表增加数据范围字段 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_role' AND COLUMN_NAME = 'data_scope') > 0,
    'SELECT 1',
    'ALTER TABLE `sys_role` ADD COLUMN `data_scope` VARCHAR(30) DEFAULT ''SELF'' COMMENT ''数据范围：ALL/DEPT_AND_CHILDREN/DEPT/PROJECT/SELF'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 2. 用户-项目关联表 ============

CREATE TABLE IF NOT EXISTS sys_user_project (
    id BIGINT NOT NULL COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_project (user_id, project_id),
    INDEX idx_project_id (project_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户-项目关联表';

-- ============ 3. 项目成员表 ============

CREATE TABLE IF NOT EXISTS biz_project_member (
    id BIGINT NOT NULL COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    user_id BIGINT NOT NULL COMMENT '系统用户ID',
    project_roles JSON NOT NULL COMMENT '项目角色列表',
    join_date DATE NOT NULL COMMENT '加入日期',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-正常 2-已失效',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_user (project_id, user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目成员表';

-- ============ 4. 系统配置表 ============

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT NOT NULL COMMENT '主键',
    config_key VARCHAR(100) NOT NULL COMMENT '参数键',
    config_value VARCHAR(2000) COMMENT '参数值',
    config_name VARCHAR(100) NOT NULL COMMENT '参数名称',
    config_group VARCHAR(50) NOT NULL COMMENT '参数分组',
    value_type VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING/NUMBER/BOOLEAN/JSON',
    default_value VARCHAR(2000) COMMENT '默认值',
    value_range VARCHAR(200) COMMENT '值范围描述',
    remark VARCHAR(500) COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key),
    INDEX idx_config_group (config_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置表';

-- ============ 5. 系统配置变更日志表 ============

CREATE TABLE IF NOT EXISTS sys_config_change_log (
    id BIGINT NOT NULL COMMENT '主键',
    config_key VARCHAR(100) NOT NULL COMMENT '参数键',
    old_value VARCHAR(2000) COMMENT '修改前值',
    new_value VARCHAR(2000) COMMENT '修改后值',
    operator_id BIGINT NOT NULL COMMENT '修改人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_config_key (config_key),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置变更日志';

-- ============ 6. 机械工作量结算单 ============

CREATE TABLE IF NOT EXISTS biz_machine_work_settlement (
    id BIGINT NOT NULL COMMENT '主键',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    settlement_code VARCHAR(50) NOT NULL COMMENT '结算单编号',
    period_start DATE NOT NULL COMMENT '结算周期开始日期',
    period_end DATE NOT NULL COMMENT '结算周期结束日期',
    total_amount DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '合计费用',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-审批中 2-已审批 3-已驳回',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    created_by BIGINT COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    version INT DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlement_code (settlement_code),
    INDEX idx_project_id (project_id),
    INDEX idx_period (period_start, period_end),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械工作量结算单';

-- ============ 7. 机械工作量结算明细 ============

CREATE TABLE IF NOT EXISTS biz_machine_work_settlement_detail (
    id BIGINT NOT NULL COMMENT '主键',
    settlement_id BIGINT NOT NULL COMMENT '结算单ID',
    ledger_id BIGINT NOT NULL COMMENT '机械台账ID',
    work_log_ids JSON COMMENT '关联工作量记录ID列表',
    shift_count DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '台班数',
    work_volume DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '工作量',
    unit_price DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '单价',
    subtotal DECIMAL(16,2) NOT NULL DEFAULT 0 COMMENT '费用小计',
    pricing_type VARCHAR(20) NOT NULL COMMENT '计价方式：SHIFT/VOLUME',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_settlement_id (settlement_id),
    INDEX idx_ledger_id (ledger_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='机械工作量结算明细';

-- ============ 8. 审批数据快照表 ============

CREATE TABLE IF NOT EXISTS biz_approval_snapshot (
    id BIGINT NOT NULL COMMENT '主键',
    workflow_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    biz_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    biz_id BIGINT NOT NULL COMMENT '业务单据ID',
    field_name VARCHAR(100) NOT NULL COMMENT '快照字段名',
    original_value TEXT COMMENT '变更前值(JSON)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    PRIMARY KEY (id),
    INDEX idx_workflow_instance (workflow_instance_id),
    INDEX idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批数据快照表';

-- ============ 9. 审批回滚记录表 ============

CREATE TABLE IF NOT EXISTS biz_approval_rollback_log (
    id BIGINT NOT NULL COMMENT '主键',
    workflow_instance_id VARCHAR(64) NOT NULL COMMENT '流程实例ID',
    biz_type VARCHAR(50) NOT NULL COMMENT '业务类型',
    biz_id BIGINT NOT NULL COMMENT '业务单据ID',
    rollback_fields JSON COMMENT '回滚字段详情',
    rollback_status TINYINT NOT NULL COMMENT '回滚状态：1-成功 2-失败 3-冲突待确认',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_msg VARCHAR(500) COMMENT '错误信息',
    operator_id BIGINT COMMENT '操作人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    PRIMARY KEY (id),
    INDEX idx_workflow_instance (workflow_instance_id),
    INDEX idx_biz (biz_type, biz_id),
    INDEX idx_status (rollback_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='审批回滚记录表';

-- ============ 10. 租户表增强 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'user_type') > 0,
    'SELECT 1',
    'ALTER TABLE `sys_tenant` ADD COLUMN `user_type` VARCHAR(20) DEFAULT ''STANDARD'' COMMENT ''用户类型：TRIAL/STANDARD/ENTERPRISE'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'start_date') > 0,
    'SELECT 1',
    'ALTER TABLE `sys_tenant` ADD COLUMN `start_date` DATE COMMENT ''有效期开始日期'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'end_date') > 0,
    'SELECT 1',
    'ALTER TABLE `sys_tenant` ADD COLUMN `end_date` DATE COMMENT ''有效期结束日期'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'max_users') > 0,
    'SELECT 1',
    'ALTER TABLE `sys_tenant` ADD COLUMN `max_users` INT DEFAULT 50 COMMENT ''用户数上限'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_tenant' AND COLUMN_NAME = 'modules') > 0,
    'SELECT 1',
    'ALTER TABLE `sys_tenant` ADD COLUMN `modules` JSON COMMENT ''已授权功能模块列表'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

ALTER TABLE sys_tenant MODIFY COLUMN status TINYINT DEFAULT 1
    COMMENT '状态：1-正常 2-已停用 3-已过期';

-- ============ 11. 初始化系统配置数据 ============

-- 安全设置
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1001, 'password_min_length', '8', '密码最小长度', 'security', 'NUMBER', '8', '6-20', '用户密码最少字符数', NOW(), NOW()),
(1002, 'password_complexity', 'UPPER_LOWER_NUMBER', '密码复杂度', 'security', 'STRING', 'UPPER_LOWER_NUMBER', NULL, '密码需包含大写字母、小写字母和数字', NOW(), NOW()),
(1003, 'login_fail_lock_count', '5', '登录失败锁定次数', 'security', 'NUMBER', '5', '3-10', '连续登录失败达此次数后锁定账户', NOW(), NOW()),
(1004, 'account_lock_duration', '30', '账户锁定时长(分钟)', 'security', 'NUMBER', '30', '5-1440', '账户锁定后自动解锁时间，单位分钟', NOW(), NOW()),
(1005, 'captcha_enabled', 'true', '启用验证码', 'security', 'BOOLEAN', 'true', NULL, '登录时是否启用图形验证码', NOW(), NOW());

-- 审批设置
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1011, 'approval_timeout_reminder', '24', '审批超时提醒(小时)', 'approval', 'NUMBER', '24', '1-168', '审批任务超过此时间未处理则发送提醒', NOW(), NOW()),
(1012, 'approval_auto_urge_interval', '48', '自动催办间隔(小时)', 'approval', 'NUMBER', '48', '24-720', '自动发送催办通知的间隔时间', NOW(), NOW()),
(1013, 'approval_auto_transfer_enabled', 'false', '启用自动转办', 'approval', 'BOOLEAN', 'false', NULL, '超时后是否自动将审批任务转办给上级', NOW(), NOW());

-- 文件设置
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1021, 'file_max_size', '20', '文件最大大小(MB)', 'file', 'NUMBER', '20', '1-100', '单个上传文件的最大大小，单位MB', NOW(), NOW()),
(1022, 'file_allowed_types', '.doc,.docx,.xls,.xlsx,.pdf,.jpg,.png,.zip', '允许上传文件类型', 'file', 'STRING', '.doc,.docx,.xls,.xlsx,.pdf,.jpg,.png,.zip', NULL, '允许上传的文件扩展名列表', NOW(), NOW()),
(1023, 'attachment_storage_limit', '10', '附件存储上限(GB)', 'file', 'NUMBER', '10', '1-100', '租户附件存储空间上限，单位GB', NOW(), NOW());

-- 通知设置
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1031, 'message_retention_days', '90', '消息保留天数', 'notification', 'NUMBER', '90', '30-365', '站内消息自动清理的保留天数', NOW(), NOW()),
(1032, 'sms_notification_enabled', 'false', '启用短信通知', 'notification', 'BOOLEAN', 'false', NULL, '是否开启短信通知渠道', NOW(), NOW()),
(1033, 'wechat_notification_enabled', 'false', '启用微信通知', 'notification', 'BOOLEAN', 'false', NULL, '是否开启微信公众号/企微通知', NOW(), NOW());
