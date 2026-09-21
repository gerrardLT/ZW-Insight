-- ============================================================================
-- 53_V2026_51__fund_classification_schema.sql
-- 资金分类体系 P0：收支科目树 + 四类保证金台账 + 农民工工资专户
--                  + 资金计划三层（年度/月度/滚动）（幂等可重复执行）。
--
-- 依据（docs/资金流转深度调研报告.md，2026-09-18）：
--   1) 资金分类维度缺失：付款/回款单据无收支分类科目（维度1/2）。
--   2) 四类保证金（投标≤2%/履约≤10%/质量≤3%/工资1%-3%）无全生命周期台账
--      （招标投标法实施条例第26/57/58条、建质〔2017〕138号、人社部〔2021〕65号）。
--   3) 农民工工资专户为国务院令第724号强制要求（第26/29/31/33条）。
--   4) 资金计划三层联动（先计划后支付，对标广联达PMCore）。
--   5) 老板看板：垫资 = 累计产值 - 累计收款（三口径理论）。
--
-- 金额纪律延续：项目 total_expense 仍仅由 PaymentApplyService.onApproved 回写，
-- 本脚本新增字段不改变付款口径。
-- ============================================================================

-- ============ 1. 资金收支分类科目（单表树，3级） ============

CREATE TABLE IF NOT EXISTS biz_fund_category (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    code VARCHAR(50) NOT NULL COMMENT '科目编码（如 EXP-MAT）',
    name VARCHAR(100) NOT NULL COMMENT '科目名称',
    direction VARCHAR(10) NOT NULL COMMENT '方向（INCOME-收入/EXPENSE-支出）',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID（0为顶级）',
    level TINYINT DEFAULT 1 COMMENT '层级（1-3）',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status VARCHAR(20) DEFAULT 'ENABLED' COMMENT '状态（ENABLED/DISABLED）',
    is_system TINYINT DEFAULT 0 COMMENT '系统内置（1-是，不可删除）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_direction (direction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资金收支分类科目表';

-- ============ 2. 保证金台账（四类全生命周期） ============

CREATE TABLE IF NOT EXISTS biz_security_bond (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_id BIGINT COMMENT '关联合同ID（履约/质量类）',
    tender_id BIGINT COMMENT '关联投标报名ID（投标类）',
    bond_type VARCHAR(20) NOT NULL COMMENT '保证金类型（TENDER-投标/PERFORMANCE-履约/QUALITY-质量/WAGE-工资）',
    amount DECIMAL(18,2) NOT NULL COMMENT '保证金金额',
    contract_amount DECIMAL(18,2) COMMENT '关联合同金额（比例校验基数）',
    deposit_date DATE NOT NULL COMMENT '缴存日期',
    due_date DATE COMMENT '到期日期',
    refund_status VARCHAR(20) DEFAULT 'DEPOSITED' COMMENT '状态（DEPOSITED-已缴存/REFUND_APPLY-退还申请中/REFUNDED-已退还/USED-已动用）',
    refund_apply_date DATE COMMENT '退还申请日期',
    refund_actual_date DATE COMMENT '实际退还日期',
    refund_amount DECIMAL(18,2) COMMENT '实际退还金额',
    bond_form VARCHAR(20) DEFAULT 'CASH' COMMENT '形式（CASH-现金/BANK_GUARANTEE-银行保函/INSURANCE-保证保险）',
    guarantee_file VARCHAR(500) COMMENT '保函/保险文件路径',
    exempt_reason VARCHAR(500) COMMENT '免存/降比理由（如连续3年无拖欠）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_bond_type (bond_type),
    KEY idx_refund_status (refund_status),
    KEY idx_due_date (due_date),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='保证金台账表（四类）';

-- ============ 3. 农民工工资专用账户 ============

CREATE TABLE IF NOT EXISTS biz_wage_special_account (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    account_no VARCHAR(100) NOT NULL COMMENT '专户账号',
    account_name VARCHAR(200) COMMENT '专户户名',
    bank_name VARCHAR(200) NOT NULL COMMENT '开户银行',
    bank_branch VARCHAR(200) COMMENT '开户支行',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE-正常/FROZEN-冻结/CANCELLED-销户）',
    wage_budget DECIMAL(18,2) DEFAULT 0 COMMENT '人工费总预算（合同口径）',
    total_received DECIMAL(18,2) DEFAULT 0 COMMENT '专户累计到账',
    total_paid DECIMAL(18,2) DEFAULT 0 COMMENT '专户累计代发工资',
    current_balance DECIMAL(18,2) DEFAULT 0 COMMENT '当前余额',
    worker_count INT DEFAULT 0 COMMENT '在册农民工人数',
    last_deposit_date DATE COMMENT '最近一次人工费到账日期',
    compliance_flag VARCHAR(20) DEFAULT 'COMPLIANT' COMMENT '合规状态（COMPLIANT-合规/INSUFFICIENT-拨付不足/OVERDUE-拨付逾期）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_status (status),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='农民工工资专用账户表';

-- ============ 4. 工资专户拨付记录（建设单位人工费拨付流水） ============

CREATE TABLE IF NOT EXISTS biz_wage_deposit (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    account_id BIGINT NOT NULL COMMENT '工资专户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    deposit_date DATE NOT NULL COMMENT '到账日期',
    amount DECIMAL(18,2) NOT NULL COMMENT '到账金额',
    payer_name VARCHAR(200) COMMENT '拨付方（建设单位）名称',
    voucher_no VARCHAR(100) COMMENT '银行凭证号',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_account_id (account_id),
    KEY idx_project_id (project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工资专户拨付记录表';

-- ============ 5. 年度资金预算 ============

CREATE TABLE IF NOT EXISTS biz_fund_annual_budget (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    budget_year INT NOT NULL COMMENT '预算年度',
    project_id BIGINT COMMENT '项目ID（NULL为公司整体）',
    income_plan DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '年度预计收款计划',
    expense_plan DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '年度预计付款计划',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/SUBMITTED/APPROVED/REJECTED）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_year (budget_year),
    KEY idx_project_id (project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='年度资金预算表';

-- ============ 6. 月度资金计划 ============

CREATE TABLE IF NOT EXISTS biz_fund_monthly_plan (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    plan_year INT NOT NULL COMMENT '计划年度',
    plan_month INT NOT NULL COMMENT '计划月份（1-12）',
    project_id BIGINT COMMENT '项目ID（NULL为公司整体）',
    income_plan DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '当月预计收款',
    expense_plan DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '当月预计付款',
    actual_income DECIMAL(18,2) DEFAULT 0 COMMENT '实际收款（月末统计回填）',
    actual_expense DECIMAL(18,2) DEFAULT 0 COMMENT '实际付款（月末统计回填）',
    remark VARCHAR(500) COMMENT '备注',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_year_month (plan_year, plan_month),
    KEY idx_project_id (project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='月度资金计划表';

-- ============ 7. 滚动预测快照（每周更新） ============

CREATE TABLE IF NOT EXISTS biz_fund_rolling_forecast (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT COMMENT '项目ID（NULL为公司整体）',
    forecast_month VARCHAR(7) NOT NULL COMMENT '预测月份（YYYY-MM）',
    expected_receipts DECIMAL(18,2) DEFAULT 0 COMMENT '预计收款（已结算未收+预计确权）',
    expected_payments DECIMAL(18,2) DEFAULT 0 COMMENT '预计付款（已审批未付+必付项）',
    net_gap DECIMAL(18,2) DEFAULT 0 COMMENT '净缺口（付款-收款，负数为盈余）',
    risk_level VARCHAR(10) COMMENT '风险等级（LOW/MEDIUM/HIGH）',
    snapshot_date DATE NOT NULL COMMENT '快照生成日期',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_forecast_month (forecast_month),
    KEY idx_project_id (project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资金滚动预测快照表';

-- ============ 8. 幂等 ALTER：biz_payment_apply 增加支出科目与计划关联 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'payment_category') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_apply` ADD COLUMN `payment_category` VARCHAR(50) NULL COMMENT ''支出分类科目编码（biz_fund_category.code，维度1+2）'' AFTER payment_amount'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'fund_plan_id') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_apply` ADD COLUMN `fund_plan_id` BIGINT NULL COMMENT ''关联月度资金计划ID（先计划后支付）'' AFTER payment_category'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND COLUMN_NAME = 'payment_category') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_apply' AND INDEX_NAME = 'idx_payment_category') = 0,
    'ALTER TABLE `biz_payment_apply` ADD INDEX `idx_payment_category` (`payment_category`)',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 9. 幂等 ALTER：biz_payment_received 增加收入科目 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_received' AND COLUMN_NAME = 'receive_category') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_received` ADD COLUMN `receive_category` VARCHAR(50) NULL COMMENT ''收入分类科目编码（biz_fund_category.code）'' AFTER payment_amount'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 10. 幂等 ALTER：biz_project 增加应收账款字段（垫资=累计产值-累计收款按需实时计算，此处存结算口径应收） ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_project' AND COLUMN_NAME = 'receivable_amount') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_project` ADD COLUMN `receivable_amount` DECIMAL(18,2) DEFAULT 0 COMMENT ''应收账款（已结算未收口径，结算审批通过时回写）'' AFTER cumulative_output'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 11. 科目种子数据（系统内置，租户1，INSERT IGNORE 幂等；ID段 900100-900135 为本脚本保留） ============

INSERT IGNORE INTO biz_fund_category (id, tenant_id, code, name, direction, parent_id, level, sort_order, is_system) VALUES
-- 一级：收入
(900100, 1, 'IN-CONTRACT',   '合同工程款',   'INCOME',  0, 1, 1, 1),
(900101, 1, 'IN-SPECIAL',    '专项资金到账', 'INCOME',  0, 1, 2, 1),
(900102, 1, 'IN-BOND',       '保证金退还',   'INCOME',  0, 1, 3, 1),
(900103, 1, 'IN-OTHER',      '其他收入',     'INCOME',  0, 1, 9, 1),
-- 二级：合同工程款
(900110, 1, 'IN-CONTRACT-PROGRESS', '工程进度款', 'INCOME', 900100, 2, 1, 1),
(900111, 1, 'IN-CONTRACT-FINAL',    '竣工结算款', 'INCOME', 900100, 2, 2, 1),
(900112, 1, 'IN-CONTRACT-CHANGE',   '变更签证款', 'INCOME', 900100, 2, 3, 1),
-- 二级：专项资金到账
(900113, 1, 'IN-SPECIAL-WAGE', '人工费专户拨付', 'INCOME', 900101, 2, 1, 1),
-- 一级：支出
(900120, 1, 'EXP-DIRECT',  '直接工程成本', 'EXPENSE', 0, 1, 1, 1),
(900121, 1, 'EXP-INDIRECT','间接费用',     'EXPENSE', 0, 1, 2, 1),
(900122, 1, 'EXP-TAX',     '税费',         'EXPENSE', 0, 1, 3, 1),
(900123, 1, 'EXP-BOND',    '保证金缴存',   'EXPENSE', 0, 1, 4, 1),
(900124, 1, 'EXP-FINANCE', '融资费用',     'EXPENSE', 0, 1, 5, 1),
(900125, 1, 'EXP-OTHER',   '其他支出',     'EXPENSE', 0, 1, 9, 1),
-- 二级：直接工程成本（与支出合同/预算科目对齐）
(900130, 1, 'EXP-DIRECT-MATERIAL', '材料款',   'EXPENSE', 900120, 2, 1, 1),
(900131, 1, 'EXP-DIRECT-SUBCON',   '分包工程款','EXPENSE', 900120, 2, 2, 1),
(900132, 1, 'EXP-DIRECT-LABOR',   '劳务款',    'EXPENSE', 900120, 2, 3, 1),
(900133, 1, 'EXP-DIRECT-MACHINE', '机械款',    'EXPENSE', 900120, 2, 4, 1),
-- 二级：间接费用
(900134, 1, 'EXP-INDIRECT-OFFICE', '办公费',   'EXPENSE', 900121, 2, 1, 1),
(900135, 1, 'EXP-INDIRECT-TRAVEL', '差旅费',   'EXPENSE', 900121, 2, 2, 1);

INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(910050, '资金分类科目', 'MENU', 5, 'fund-category',   'views/finance/fund-category/index',   'Collection', 51, 1, 0),
(910051, '保证金台账',   'MENU', 5, 'security-bond',   'views/finance/security-bond/index',   'Lock',       52, 1, 0),
(910052, '工资专户',     'MENU', 5, 'wage-account',    'views/finance/wage-account/index',    'Wallet',     53, 1, 0),
(910053, '资金计划',     'MENU', 5, 'fund-plan',       'views/finance/fund-plan/index',       'Calendar',   54, 1, 0),
(910054, '资金看板',     'MENU', 5, 'fund-dashboard',  'views/finance/fund-dashboard/index',  'DataLine',   55, 1, 0);
