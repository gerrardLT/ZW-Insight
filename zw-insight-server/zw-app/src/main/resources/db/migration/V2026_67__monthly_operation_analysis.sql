-- ============================================================
-- V2026_67__monthly_operation_analysis.sql
-- 月度经营分析表（资金流转流程 §9「每月必须形成一个项目经营表」）
--
-- 表结构：10 类费用 × 6 列（预算 / 本月发生 / 累计发生 / 累计支付 / 应付未付 / 预计最终）
-- 唯一键：(tenant_id, project_id, analysis_month, category_code) → 同月重复生成走覆盖更新（幂等）
--
-- 【为什么「本月发生」不落单据聚合而落「时点差值」——2026-09-24 线上数据源普查结论】
--   ① biz_cost_account_txn（CBS 流水，有 occurred_at 可做月度归集）当前 **0 行**，
--      需 CostRollUpTask 运行后才有数据；
--   ② 四类结算单中仅 biz_purchase_settlement / biz_final_settlement 有 settlement_date 列，
--      且 purchase 的 4 行该列**全为 NULL**；biz_labor_settlement / biz_subcontract_settlement
--      根本没有 settlement_date；biz_machine_work_settlement 连 settlement_amount 列名都不同；
--   ③ 用 created_at 冒充业务发生日期不可靠（补录单据会落错月份），属伪造口径，不采用。
--   → 故 current_month_occurred = 本月末 cumulative_occurred − 上月末 cumulative_occurred
--     （取本表上月行，两个真实时点快照之差）。**首次生成（无上月行）时为 NULL**，
--     occurred_basis='NO_BASELINE'，不用 0 冒充「本月无发生」。
--
-- 【为什么间接费六类的 cumulative_paid / payable_outstanding 允许为 NULL】
--   直接费四类（人工/材料/机械/分包）的累计支付有权威来源：对应合同的 cumulative_paid
--   （由付款申请审批通过回写）。间接费（措施/管理/招待/差旅车辆/专业服务/财税）的支出
--   走报销与其他费用付款，**无法按本表十类细分归集**（付款申请只到 OTHER_EXPENSE 粒度）。
--   此时置 NULL 并标 paid_basis='NO_PAYMENT_SOURCE'，不拿 0 或估算值填充。
-- ============================================================

CREATE TABLE IF NOT EXISTS biz_monthly_operation_analysis (
    id                     BIGINT       NOT NULL COMMENT '主键ID',
    tenant_id              BIGINT                COMMENT '租户ID',
    project_id             BIGINT       NOT NULL COMMENT '项目ID',
    analysis_month         VARCHAR(7)   NOT NULL COMMENT '分析月份 yyyy-MM',
    category_code          VARCHAR(30)  NOT NULL COMMENT '费用类别码（§9 十类：LABOR/MATERIAL/MACHINE/SUBCONTRACT/MEASURE/ADMIN/ENTERTAIN/TRAVEL_VEHICLE/PROFESSIONAL/TAX）',
    category_name          VARCHAR(50)  NOT NULL COMMENT '类别中文名（人工/材料/机械/分包/措施/管理/招待/差旅车辆/专业服务/财税）',
    budget_amount          DECIMAL(18,2) DEFAULT 0 COMMENT '预算（CBS baseline_amount 按类聚合，即目标成本口径）',
    current_month_occurred DECIMAL(18,2) DEFAULT NULL COMMENT '本月发生 = 本月末累计 − 上月末累计；首次生成无上月行时为 NULL（不用 0 冒充）',
    cumulative_occurred    DECIMAL(18,2) DEFAULT 0 COMMENT '累计发生（CBS actual_amount 按类聚合）',
    cumulative_paid        DECIMAL(18,2) DEFAULT NULL COMMENT '累计支付（直接费取合同 cumulative_paid 审批口径；间接费无细分数据源时为 NULL）',
    payable_outstanding    DECIMAL(18,2) DEFAULT NULL COMMENT '应付未付 = 累计发生 − 累计支付；累计支付为 NULL 时同样为 NULL',
    forecast_final         DECIMAL(18,2) DEFAULT 0 COMMENT '预计最终（CBS forecast_amount 按类聚合，EAC 口径）',
    occurred_basis         VARCHAR(30)           COMMENT '本月发生口径：VS_LAST_MONTH（与上月行差值）/ NO_BASELINE（首次生成）/ NO_DATA_SOURCE（无 CBS 账户）',
    paid_basis             VARCHAR(30)           COMMENT '累计支付口径：APPROVAL_WRITEBACK（合同审批回写）/ NO_PAYMENT_SOURCE（无细分付款数据源）',
    account_count          INT          DEFAULT 0 COMMENT '归入本类的 CBS 成本账户数（0 表示该类未建账户，金额均为 0 而非数据缺失）',
    generated_at           DATETIME              COMMENT '本次生成时间（重跑覆盖时更新）',
    created_by             BIGINT                COMMENT '创建人ID',
    created_at             DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at             DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted                INT          DEFAULT 0 COMMENT '逻辑删除',
    version                INT          DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_month_category (tenant_id, project_id, analysis_month, category_code),
    KEY idx_project_month (project_id, analysis_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='月度经营分析表（资金流转 §9：10 类费用 × 6 列）';
