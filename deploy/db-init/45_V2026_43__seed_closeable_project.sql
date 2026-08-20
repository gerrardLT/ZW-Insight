-- 45_V2026_43__seed_closeable_project.sql
-- 演示种子补充：可结项 COMPLETED 项目（2026-08-21 台账数据态#1）。
-- 背景：E2E a1-project A1-12/A-X3「结项全链路」要求 close-check allPassed=true 的
-- 数据前提。既有种子 90002 虽 COMPLETED 但 cumulative_output(33.5M) > total_income(31M)，
-- 预检条件②（款项结清，容差100）必然失败，无法作为结项演示数据。
--
-- 结项预检四条件（ProjectService.checkCloseConditions）：
--   ① status = COMPLETED                          → 本项目 status='COMPLETED'
--   ② |cumulative_output - total_income| ≤ 100    → 两者均 10000000，差 0
--   ③ status 非 DRAFT/FILED                       → COMPLETED 满足
--   ④ biz_project_settlement 存在 APPROVED 行     → 结算单 93302 APPROVED
--
-- 约束遵循 31_V2026_26 种子规范：
--   * tenant_id=1 持久演示数据；固定 ID 段 90xxx 未占用区
--     （项目 90004 / 成员 90709 / 结算 93302，均不与雪花及既有种子冲突）
--   * INSERT IGNORE 幂等，可重复执行
--   * 金额自洽：profit = total_income - total_expenditure = 10000000 - 8000000 = 2000000
--     profit_rate = 2000000 / 10000000 = 20.00

-- 可结项项目（已竣工，产值与收款完全结清）
INSERT IGNORE INTO biz_project (id, project_code, project_name, project_nature, project_type, owner_company_id, owner_company_name, signing_company_id, signing_company_name, project_overview, project_address, contact_name, contact_phone, need_tender, status, budget_amount, contract_amount, cumulative_output, settlement_amount, total_income, total_expense, total_other_payment, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90004, 'PRJ20250101001', '城北河道综合整治工程', '改建', '市政工程', 90502, '杭州市城南市政建设管理中心', 90301, '中正建设集团有限公司', '城北片区河道清淤、驳坎加固与滨水绿道建设，全长2.8公里', '杭州市拱墅区城北河道沿线', '周涛', '13800004444', 0, 'COMPLETED', 10000000.00, 10000000.00, 10000000.00, 10000000.00, 10000000.00, 8000000.00, 0.00, 1, 1, NOW(), NOW(), 0, 0);

-- 项目经理（结项审批链路由发起人处理，保证项目有成员）
INSERT IGNORE INTO biz_project_member (id, project_id, user_id, user_name, project_roles, join_date, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(90709, 90004, 90075, '陈刚', '["PROJECT_MANAGER"]', '2025-01-01', 1, 1, 1, NOW(), NOW(), 0, 0);

-- 项目最终结算（已审批，满足结项预检条件④）
INSERT IGNORE INTO biz_project_settlement (id, tenant_id, project_id, settlement_code, construction_contract_amount, cumulative_output, cumulative_received, cumulative_invoiced, total_income, subcontract_settled, labor_settled, material_settled, machine_settled, other_expense, cumulative_paid, total_expenditure, profit, profit_rate, status, workflow_instance_id, created_by, created_at, updated_at, deleted, version) VALUES
(93302, 1, 90004, 'JS20260820001', 10000000.00, 10000000.00, 10000000.00, 10000000.00, 10000000.00, 2000000.00, 3000000.00, 2500000.00, 500000.00, 0.00, 8000000.00, 8000000.00, 2000000.00, 20.00, 'APPROVED', NULL, 1, NOW(), NOW(), 0, 0);
