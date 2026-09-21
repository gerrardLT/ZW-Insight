-- ============================================================================
-- 54_V2026_52__p1_bill_financing_tier.sql
-- 资金管理 P1：票据台账（承兑汇票背书/贴现/兑现） + 融资借贷与还款计划
--              + 金额分级审批配置（幂等可重复执行）。
--
-- 依据（docs/资金流转深度调研报告.md 第八节 P1 + 配套方案）：
--   1) 票据管理：应收/应付票据台账 + 背书/贴现（广联达 PMCore 与司库体系标配）。
--   2) 融资借贷：借款合同 + 利息计算 + 还本付息计划（等额本息/等额本金/到期还本付息）。
--   3) 金额分级审批：金额区间 → 审批档位（>50 万强制财务负责人），档位等级作为
--      Flowable 流程变量 approvalTier 传入 payment_apply_approval，由 BPMN 条件网关路由。
--
-- 金额纪律延续：票据/融资均不回写项目 total_income/total_expense（非经营性收支口径），
-- 贴现利息与融资利息归集于各自台账，供看板"融资费用"科目汇总。
-- ============================================================================

-- ============ 1. 票据台账（应收/应付承兑汇票） ============

CREATE TABLE IF NOT EXISTS biz_bill (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT COMMENT '关联项目ID',
    contract_id BIGINT COMMENT '关联合同ID',
    bill_no VARCHAR(100) NOT NULL COMMENT '票据号码',
    direction VARCHAR(20) NOT NULL COMMENT '方向（RECEIVABLE-应收票据/PAYABLE-应付票据）',
    bill_type VARCHAR(30) NOT NULL COMMENT '票据类型（BANK_ACCEPTANCE-银行承兑/COMMERCIAL_ACCEPTANCE-商业承兑）',
    face_amount DECIMAL(18,2) NOT NULL COMMENT '票面金额',
    issue_date DATE NOT NULL COMMENT '出票日期',
    due_date DATE NOT NULL COMMENT '到期日期',
    drawer_name VARCHAR(200) COMMENT '出票人（应付票据=我方；应收票据=对方）',
    payee_name VARCHAR(200) COMMENT '收款人（应收票据=我方；应付票据=对方）',
    acceptor_name VARCHAR(200) COMMENT '承兑人（银行/企业）',
    status VARCHAR(20) DEFAULT 'HELD' COMMENT '状态（HELD-持有/ENDORSED-已背书/DISCOUNTED-已贴现/REDEEMED-已兑现/PAID_OUT-已兑付）',
    -- 背书信息
    endorsee_name VARCHAR(200) COMMENT '被背书人（背书转让对象）',
    endorse_date DATE COMMENT '背书日期',
    -- 贴现信息
    discount_date DATE COMMENT '贴现日期',
    discount_rate DECIMAL(8,6) COMMENT '贴现年利率（小数，如0.048）',
    discount_interest DECIMAL(18,2) COMMENT '贴现利息 = 面值×贴现率×剩余天数/360',
    discount_net_amount DECIMAL(18,2) COMMENT '贴现净额（实收金额）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_direction (direction),
    KEY idx_status (status),
    KEY idx_due_date (due_date),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='票据台账表（应收/应付承兑汇票）';

-- ============ 2. 融资借贷台账 ============

CREATE TABLE IF NOT EXISTS biz_financing (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    financing_type VARCHAR(20) NOT NULL COMMENT '融资类型（BANK_LOAN-银行贷款/OTHER-其他借款）',
    contract_no VARCHAR(100) NOT NULL COMMENT '借款合同编号',
    lender_name VARCHAR(200) NOT NULL COMMENT '出借方名称（银行/机构）',
    principal DECIMAL(18,2) NOT NULL COMMENT '借款本金',
    annual_rate DECIMAL(8,6) NOT NULL COMMENT '年利率（小数，如0.045）',
    start_date DATE NOT NULL COMMENT '放款日期',
    end_date DATE NOT NULL COMMENT '到期日期',
    term_months INT NOT NULL COMMENT '期限（月）',
    repayment_method VARCHAR(30) NOT NULL COMMENT '还款方式（EQUAL_INSTALLMENT-等额本息/EQUAL_PRINCIPAL-等额本金/BULLET-到期还本付息）',
    total_interest DECIMAL(18,2) DEFAULT 0 COMMENT '计划总利息（生成计划时汇总）',
    total_repaid DECIMAL(18,2) DEFAULT 0 COMMENT '累计已还本息',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态（ACTIVE-在借/SETTLED-已结清/OVERDUE-逾期）',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_status (status),
    KEY idx_contract_no (contract_no),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='融资借贷台账表';

-- ============ 3. 还款计划（按期生成） ============

CREATE TABLE IF NOT EXISTS biz_financing_repayment (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    financing_id BIGINT NOT NULL COMMENT '融资台账ID',
    period_no INT NOT NULL COMMENT '期数（从1开始）',
    due_date DATE NOT NULL COMMENT '应还日期',
    principal_due DECIMAL(18,2) NOT NULL COMMENT '应还本金',
    interest_due DECIMAL(18,2) NOT NULL COMMENT '应还利息',
    principal_paid DECIMAL(18,2) DEFAULT 0 COMMENT '实还本金',
    interest_paid DECIMAL(18,2) DEFAULT 0 COMMENT '实还利息',
    paid_date DATE COMMENT '实还日期',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态（PENDING-待还/PARTIAL-部分还款/PAID-已还清）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_financing_id (financing_id),
    KEY idx_due_date (due_date),
    KEY idx_status (status),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='融资还款计划表';

-- ============ 4. 金额分级审批配置（参照 sys_budget_control_config 先例） ============

CREATE TABLE IF NOT EXISTS sys_amount_tier_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    module VARCHAR(50) NOT NULL COMMENT '业务模块（PAYMENT_APPLY-付款申请）',
    tier_level INT NOT NULL COMMENT '审批档位等级（1-普通/2-部门负责人/3-财务负责人/4-老板）',
    tier_name VARCHAR(100) NOT NULL COMMENT '档位名称（如 财务负责人审批）',
    min_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '金额下限（含）',
    max_amount DECIMAL(18,2) COMMENT '金额上限（不含；NULL表示无上限）',
    enabled INT DEFAULT 1 COMMENT '是否启用（1-是 0-否）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_module (module),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='金额分级审批配置表';

-- ============ 5. 种子数据：付款申请分级档位（行业惯例：50万财务负责人红线） ============

INSERT IGNORE INTO sys_amount_tier_config (id, tenant_id, module, tier_level, tier_name, min_amount, max_amount, enabled) VALUES
(910100, 1, 'PAYMENT_APPLY', 1, '普通审批',           0,          100000,   1),
(910101, 1, 'PAYMENT_APPLY', 2, '部门负责人审批',     100000,     500000,   1),
(910102, 1, 'PAYMENT_APPLY', 3, '财务负责人审批',     500000,     5000000,  1),
(910103, 1, 'PAYMENT_APPLY', 4, '老板审批',           5000000,    NULL,     1);

INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden) VALUES
(910055, '票据台账',   'MENU', 5, 'bill',       'views/finance/bill/index',       'Tickets', 56, 1, 0),
(910056, '融资借贷',   'MENU', 5, 'financing',  'views/finance/financing/index',  'CreditCard', 57, 1, 0),
(910057, '审批分级配置','MENU', 2, 'amount-tier','views/system/amount-tier/index','Sort',      88, 1, 0);
