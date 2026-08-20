-- 44_V2026_42__seed_negative_profit_settlement.sql
-- 演示种子补充：亏损（负利润）项目最终结算单，解除 E2E finance-write C-12
-- 「利润负值红色渲染」条件 skip 的数据态缺口（tasks.md 受阻登记 2026-08-18 DATA，
-- 处置决策：种子补充对应数据态后翻转恢复断言）。
--
-- 约束遵循 31_V2026_26 种子规范：
--   * tenant_id=1 持久演示数据；固定 ID 段 99xxx（99361，不与雪花/既有种子冲突）
--   * INSERT IGNORE 幂等，可重复执行
--   * 金额自洽：profit = total_income - total_expenditure = 800000 - 850000 = -50000
--     profit_rate = -50000 / 800000 = -6.25
--   * 挂 90002（城南市政道路改造，已竣工）——与既有 93301 盈利结算单并存，
--     呈现同项目不同口径结算的演示差异
INSERT IGNORE INTO biz_project_settlement (id, tenant_id, project_id, settlement_code, construction_contract_amount, cumulative_output, cumulative_received, cumulative_invoiced, total_income, subcontract_settled, labor_settled, material_settled, machine_settled, other_expense, cumulative_paid, total_expenditure, profit, profit_rate, status, workflow_instance_id, created_by, created_at, updated_at, deleted, version) VALUES
(99361, 1, 90002, 'JS20260620NEG', 800000.00, 800000.00, 700000.00, 800000.00, 800000.00, 200000.00, 300000.00, 250000.00, 50000.00, 50000.00, 750000.00, 850000.00, -50000.00, -6.25, 'APPROVED', NULL, 1, NOW(), NOW(), 0, 0);
