-- ============================================================================
-- V2026_58__fund_plan_detail.sql
-- 资金闭环阶段一 1C：月度资金计划科目明细
--
-- 背景（docs/资金流转流程.md §9 月度资金分析；驾驶舱 V1 §9.3 未来大额支出）：
--   现状 biz_fund_monthly_plan 仅有 income_plan/expense_plan 两个总额，
--   无科目维度 → "未来30天最大支出（分包/材料/工资/税款）"与月度经营分析矩阵无数据源。
--
-- 本脚本：新表 biz_fund_plan_detail（计划 × 方向 × 科目 × 金额，唯一键防重）。
-- 应用约束（FundPlanService）：明细合计必须与主表总额一致（不一致抛异常，不静默）；
-- 明细可选（存量计划无明细仍按总额运作，向后兼容）。
--
-- 幂等：CREATE TABLE IF NOT EXISTS。
-- ============================================================================

CREATE TABLE IF NOT EXISTS biz_fund_plan_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    plan_id BIGINT NOT NULL COMMENT '月度资金计划ID（biz_fund_monthly_plan.id）',
    direction VARCHAR(10) NOT NULL COMMENT '方向（INCOME-收款/EXPENSE-付款）',
    category_code VARCHAR(50) NOT NULL COMMENT '资金科目编码（biz_fund_category.code）',
    amount DECIMAL(18,2) NOT NULL COMMENT '计划金额',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_plan_direction (plan_id, direction),
    KEY idx_category_code (category_code),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='月度资金计划科目明细（先计划后支付的科目维度）';
