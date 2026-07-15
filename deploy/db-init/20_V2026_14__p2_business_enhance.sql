-- ============================================================
-- P2 业务增强 - 数据库迁移脚本
-- 版本: V2026_14
-- 功能: 财务封账管理、税率字典、移动端快捷入口自定义
-- ============================================================

-- ============ 1. 财务封账记录表 ============

CREATE TABLE IF NOT EXISTS biz_finance_lock (
    id BIGINT NOT NULL COMMENT '主键',
    period VARCHAR(7) NOT NULL COMMENT '封账期间，格式 YYYY-MM',
    lock_type VARCHAR(20) NOT NULL COMMENT '封账类型：MONTHLY-月度 / QUARTERLY-季度',
    status VARCHAR(20) NOT NULL DEFAULT 'LOCKED' COMMENT '状态：LOCKED-已封账 / UNLOCKED-已解封',
    project_id BIGINT COMMENT '项目ID（NULL表示全局封账）',
    lock_by BIGINT COMMENT '封账操作人ID',
    lock_time DATETIME COMMENT '封账时间',
    unlock_by BIGINT COMMENT '解封操作人ID',
    unlock_time DATETIME COMMENT '解封时间',
    tenant_id BIGINT COMMENT '租户ID',
    tenant_code VARCHAR(50) COMMENT '租户编码',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_period_project (period, project_id, tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='财务封账记录表';

-- ============ 2. 税率字典表 ============

CREATE TABLE IF NOT EXISTS biz_tax_rate (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(30) NOT NULL COMMENT '税率名称',
    rate_value DECIMAL(5,2) NOT NULL COMMENT '税率数值（如 13.00 表示 13%）',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用 / DISABLED-停用',
    tenant_id BIGINT COMMENT '租户ID',
    tenant_code VARCHAR(50) COMMENT '租户编码',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name_tenant (name, tenant_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='税率字典表';

-- ============ 3. 可选快捷功能定义表 ============

CREATE TABLE IF NOT EXISTS msg_available_shortcut (
    id BIGINT NOT NULL COMMENT '主键',
    name VARCHAR(50) NOT NULL COMMENT '功能名称',
    icon VARCHAR(100) COMMENT '图标标识',
    route_path VARCHAR(200) COMMENT '路由路径',
    sort_order INT DEFAULT 0 COMMENT '默认排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用 / DISABLED-停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='可选快捷功能定义表';

-- ============ 4. 用户快捷入口表新增字段 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'msg_user_shortcut' AND COLUMN_NAME = 'shortcut_id') > 0,
    'SELECT 1',
    'ALTER TABLE `msg_user_shortcut` ADD COLUMN `shortcut_id` BIGINT COMMENT ''关联可选快捷功能ID（msg_available_shortcut.id）'' AFTER `menu_icon`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 5. 初始化可选快捷功能数据 ============

INSERT IGNORE INTO msg_available_shortcut (id, name, icon, route_path, sort_order, status, create_time) VALUES
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

-- ============ 6. 开票申请/收票登记表新增税率字段 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_invoice_apply' AND COLUMN_NAME = 'tax_rate') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_invoice_apply` ADD COLUMN `tax_rate` DECIMAL(5,2) COMMENT ''税率(%)，引用税率字典或手动输入'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_invoice_received' AND COLUMN_NAME = 'tax_rate') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_invoice_received` ADD COLUMN `tax_rate` DECIMAL(5,2) COMMENT ''税率(%)，引用税率字典或手动输入'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
