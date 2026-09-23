-- ============================================================================
-- V2026_59__profit_snapshot_risk_register.sql
-- 资金闭环阶段二 2A/2B：预计利润月度快照 + 统一风险台账
--
-- 背景（docs/工程老板经营驾驶舱_UI原型结构_V1.md §5.1/§11-12；audit-reports/cockpit-fund-gap-analysis-2026-09-22.md）：
--   1) 系统无「预计利润」口径（现有 company-overview.profit 为历史已实现收支差），
--      利润趋势归因需要月度快照做环比差额分解；
--   2) 预警散落 5 处（预算/质保金/工资专户/票据/库存），无统一风险实体、
--      无六要素（影响金额/责任人/下一步/处理状态），风险中心页面无数据源。
--
-- 预计利润口径（写入快照，权威定义）：
--   forecast_profit = contract_income − forecast_total_cost
--   contract_income 取生效施工合同金额合计（无施工合同时回退项目 contract_amount，
--   income_basis 字段如实标记来源，不静默混用口径）；
--   forecast_total_cost 取 CBS 根成本账户 forecastAmount(EAC) 合计（无成本账户时
--   回退项目 total_expense，cost_basis 标记 FALLBACK_TOTAL_EXPENSE）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================================

-- ============ 1. 预计利润月度快照 ============

CREATE TABLE IF NOT EXISTS biz_profit_snapshot (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    snapshot_month VARCHAR(7) NOT NULL COMMENT '快照月份（yyyy-MM）',
    project_id BIGINT COMMENT '项目ID（NULL=公司级聚合）',
    contract_income DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同收入（预计利润计算基数）',
    income_basis VARCHAR(30) NOT NULL DEFAULT 'CONSTRUCTION_CONTRACT' COMMENT '收入口径（CONSTRUCTION_CONTRACT-施工合同合计/PROJECT_CONTRACT_AMOUNT-回退项目合同额）',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值（快照时点）',
    actual_cost DECIMAL(18,2) DEFAULT 0 COMMENT '已发生成本（CBS actual 合计；回退口径为 total_expense）',
    forecast_total_cost DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预计最终总成本（CBS forecast/EAC 合计）',
    cost_basis VARCHAR(30) NOT NULL DEFAULT 'CBS_FORECAST' COMMENT '成本口径（CBS_FORECAST-成本账户完工预测/FALLBACK_TOTAL_EXPENSE-回退已实现支出）',
    forecast_profit DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '预计利润 = 合同收入 − 预计最终总成本',
    profit_delta DECIMAL(18,2) COMMENT '较上期快照利润变化（归因基数）',
    category_breakdown JSON COMMENT '成本类别分解 {category: {baseline,current,actual,forecast}}，归因差额由此计算',
    snapshot_date DATE NOT NULL COMMENT '快照生成日期',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_month_project (snapshot_month, project_id, tenant_id),
    KEY idx_project_id (project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预计利润月度快照（趋势与归因数据源）';

-- ============ 2. 统一风险台账 ============

CREATE TABLE IF NOT EXISTS biz_risk_register (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    risk_code VARCHAR(64) NOT NULL COMMENT '风险唯一键（ruleType:projectId:bizRefId，扫描幂等去重）',
    risk_type VARCHAR(30) NOT NULL COMMENT '风险类型（PROFIT_LOSS/BUDGET_OVER/FUND_GAP/RECEIVABLE_OVERDUE/RETENTION_OVERDUE/WAGE_COMPLIANCE）',
    project_id BIGINT COMMENT '项目ID（公司级风险可空）',
    severity VARCHAR(10) NOT NULL COMMENT '严重级别（RED-严重/YELLOW-关注/INFO-提醒；规则自动判定，禁止人工改级）',
    title VARCHAR(200) NOT NULL COMMENT '发生了什么（一句话）',
    impact_amount DECIMAL(18,2) DEFAULT 0 COMMENT '影响金额（元）',
    reason_detail JSON COMMENT '为什么发生（结构化归因，如成本类别差额分解）',
    owner_id BIGINT COMMENT '责任人用户ID（项目经理）',
    owner_name VARCHAR(50) COMMENT '责任人姓名（快照冗余）',
    next_action VARCHAR(500) COMMENT '下一步动作建议',
    handle_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '处理状态（OPEN-待处理/PROCESSING-处理中/RESOLVED-已解决/IGNORED-已忽略）',
    handled_by BIGINT COMMENT '处理人ID',
    handled_at DATETIME COMMENT '处理时间',
    handle_note VARCHAR(500) COMMENT '处理备注',
    biz_ref_type VARCHAR(30) COMMENT '关联单据类型（穿透跳转用：PROJECT/COST_ACCOUNT/FORECAST/RECEIVABLE/RETENTION/WAGE_ACCOUNT）',
    biz_ref_id BIGINT COMMENT '关联单据ID',
    rule_params JSON COMMENT '触发规则参数快照（阈值/执行率等，可追溯判定依据）',
    last_scan_at DATETIME COMMENT '最近扫描确认时间（风险仍存在时刷新）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_code (risk_code, tenant_id),
    KEY idx_type_status (risk_type, handle_status),
    KEY idx_severity (severity),
    KEY idx_project_id (project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一风险台账（风险中心/老板待处理事项数据源）';
