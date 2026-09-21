-- ============================================================================
-- 55_V2026_53__p2_cash_position_daily.sql
-- 资金管理 P2：多级账户分组 + 账户余额登记 + 银行流水导入 + 余额调节表
--              + 资金日报快照（纯内部数据聚合，不含银企直联，幂等可重复执行）。
--
-- 依据（docs/资金流转深度调研报告.md 第八节 P2；银企直联已降级为 P3 待办）：
--   1) 多级账户分组：基本户/一般户/专户按公司/项目维度树形归集（司库集中管控）。
--   2) 账户余额登记：银行余额手工/文件导入维护，为日报头寸提供真实数据源。
--   3) 银行流水：网银导出流水落库 + 与内部单据勾稽匹配（对账能力）。
--   4) 余额调节表：银行对账单余额 vs 企业账面余额，未达账项双向调节。
--   5) 资金日报：每日头寸（可用余额）、当日收支、大额支出、预计到账（老板视角）。
--
-- 金额纪律延续：本组表均为「资金形态/头寸」维度，不回写项目 total_income/total_expense。
--   日报「当日收支」来源于 biz_bank_flow 流水，与付款口径累计相互独立。
-- ============================================================================

-- ============ 1. 银行账户分组（多级树，参照 sys_menu 树形结构） ============

CREATE TABLE IF NOT EXISTS biz_bank_account_group (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    group_name VARCHAR(100) NOT NULL COMMENT '分组名称（如 集团公司/XX项目部）',
    group_code VARCHAR(50) NOT NULL COMMENT '分组编码',
    parent_id BIGINT DEFAULT 0 COMMENT '父级ID（0为顶级）',
    level TINYINT DEFAULT 1 COMMENT '层级（1-4）',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    status VARCHAR(20) DEFAULT 'ENABLED' COMMENT '状态（ENABLED/DISABLED）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='银行账户分组表（多级树）';

-- ============ 2. 银行账户余额登记（流水落库前的余额来源，供日报头寸） ============

CREATE TABLE IF NOT EXISTS biz_bank_balance (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    account_id BIGINT NOT NULL COMMENT '银行账户ID',
    snapshot_date DATE NOT NULL COMMENT '余额日期',
    balance DECIMAL(18,2) NOT NULL COMMENT '账户余额',
    source VARCHAR(20) DEFAULT 'MANUAL' COMMENT '来源（MANUAL-手工录入/IMPORT-流水导入推算）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_date (account_id, snapshot_date, deleted),
    KEY idx_snapshot_date (snapshot_date),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='银行账户余额登记表';

-- ============ 3. 银行流水（网银导出落库 + 勾稽） ============

CREATE TABLE IF NOT EXISTS biz_bank_flow (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    account_id BIGINT NOT NULL COMMENT '银行账户ID',
    flow_date DATE NOT NULL COMMENT '交易日期',
    direction VARCHAR(10) NOT NULL COMMENT '方向（IN-收入/OUT-支出）',
    amount DECIMAL(18,2) NOT NULL COMMENT '交易金额',
    balance_after DECIMAL(18,2) COMMENT '交易后余额',
    transaction_no VARCHAR(100) COMMENT '银行流水号（导入去重键）',
    description VARCHAR(500) COMMENT '摘要/用途',
    counterparty_name VARCHAR(200) COMMENT '对方单位',
    reconciled INT DEFAULT 0 COMMENT '是否已勾稽（0-未 1-已）',
    matched_type VARCHAR(30) COMMENT '匹配单据类型（PAYMENT_APPLY/PAYMENT_RECEIVED/...）',
    matched_id BIGINT COMMENT '匹配单据ID',
    source VARCHAR(20) DEFAULT 'MANUAL' COMMENT '来源（MANUAL-手工/IMPORT-文件导入）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_account_id (account_id),
    KEY idx_flow_date (flow_date),
    KEY idx_reconciled (reconciled),
    KEY idx_transaction_no (transaction_no),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='银行流水表';

-- ============ 4. 余额调节表（银行对账单 vs 企业账面，未达账项双向调节） ============

CREATE TABLE IF NOT EXISTS biz_balance_reconciliation (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    account_id BIGINT NOT NULL COMMENT '银行账户ID',
    reconciliation_date DATE NOT NULL COMMENT '调节基准日',
    bank_statement_balance DECIMAL(18,2) NOT NULL COMMENT '银行对账单余额',
    book_balance DECIMAL(18,2) NOT NULL COMMENT '企业账面余额',
    enterprise_deposit_bank_not INT DEFAULT 0 COMMENT '企业已收银行未收（金额）',
    enterprise_payment_bank_not INT DEFAULT 0 COMMENT '企业已付银行未付（金额）',
    bank_deposit_enterprise_not INT DEFAULT 0 COMMENT '银行收企业未收（金额）',
    bank_payment_enterprise_not INT DEFAULT 0 COMMENT '银行付企业未付（金额）',
    adjusted_bank_balance DECIMAL(18,2) COMMENT '调节后银行余额（=银行对账单+银行收企业未收-银行付企业未付）',
    adjusted_book_balance DECIMAL(18,2) COMMENT '调节后账面余额（=账面+企业已收银行未收-企业已付银行未付）',
    balanced INT DEFAULT 0 COMMENT '是否调平（两调节后余额相等，1-是 0-否）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_date (account_id, reconciliation_date, deleted),
    KEY idx_reconciliation_date (reconciliation_date),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='银行存款余额调节表';

-- ============ 5. 资金日报快照（每日头寸，老板视角） ============

CREATE TABLE IF NOT EXISTS biz_daily_cash_report (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    report_date DATE NOT NULL COMMENT '报告日期',
    total_balance DECIMAL(18,2) DEFAULT 0 COMMENT '全部账户余额合计（可用头寸）',
    basic_balance DECIMAL(18,2) DEFAULT 0 COMMENT '基本户余额',
    general_balance DECIMAL(18,2) DEFAULT 0 COMMENT '一般户余额',
    special_balance DECIMAL(18,2) DEFAULT 0 COMMENT '专户户余额',
    inflow_amount DECIMAL(18,2) DEFAULT 0 COMMENT '当日资金流入（流水 IN 汇总）',
    outflow_amount DECIMAL(18,2) DEFAULT 0 COMMENT '当日资金流出（流水 OUT 汇总）',
    net_position DECIMAL(18,2) DEFAULT 0 COMMENT '当日净头寸（流入-流出）',
    large_outflow_count INT DEFAULT 0 COMMENT '当日大额支出笔数（超阈值）',
    large_outflow_amount DECIMAL(18,2) DEFAULT 0 COMMENT '当日大额支出金额',
    account_count INT DEFAULT 0 COMMENT '统计账户数',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_date (report_date, deleted, tenant_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资金日报快照表';

-- ============ 6. 幂等 ALTER：biz_bank_account 增加分组归属 ============

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_bank_account' AND COLUMN_NAME = 'group_id') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_bank_account` ADD COLUMN `group_id` BIGINT NULL COMMENT ''所属账户分组ID（biz_bank_account_group.id）'' AFTER project_id'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_bank_account' AND COLUMN_NAME = 'group_id') > 0
    AND (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_bank_account' AND INDEX_NAME = 'idx_group_id') = 0,
    'ALTER TABLE `biz_bank_account` ADD INDEX `idx_group_id` (`group_id`)',
    'SELECT 1'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- ============ 7. 菜单种子（财务管理目录 id=5 下新增） ============

INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(910058, '账户分组',   'MENU', 5, 'bank-account-group', 'views/finance/bank-account-group/index', 'Grid',      58, 1, 0),
(910059, '银行流水',   'MENU', 5, 'bank-flow',          'views/finance/bank-flow/index',          'Sort',      59, 1, 0),
(910060, '余额调节表', 'MENU', 5, 'balance-reconciliation', 'views/finance/balance-reconciliation/index', 'Operation', 60, 1, 0),
(910061, '资金日报',   'MENU', 5, 'daily-cash-report',  'views/finance/daily-cash-report/index',  'Calendar',  61, 1, 0);
