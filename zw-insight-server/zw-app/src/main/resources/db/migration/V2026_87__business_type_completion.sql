-- ============================================================
-- V2026_87__business_type_completion.sql
-- 业务类型目录补全：把代码里实际发起审批的业务类型补进 wf_business_type（租户 1）
--
-- 背景：演示种子 V2026_26 只登记了 5 项（付款/开票/劳务结算/分包结算/项目报销），
--       而 startProcess 实际使用的业务类型有三十余个。该表目前仅作目录展示，
--       审批流转不依赖它，补全不改变任何业务行为。
-- ID 段：99231-99269（探针确认全库种子无占用）。
-- 幂等：每条 INSERT 带「同 id 或 同 type_code+租户 未删除」守卫，可重复执行；
--       不触碰既有 5 项，不覆盖已手工新增的同编码记录。
-- 回滚：DELETE FROM wf_business_type WHERE id BETWEEN 99231 AND 99269 AND tenant_id = 1;
-- ============================================================

INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99231, '个人报销', 'PERSONAL_REIMBURSEMENT', 0, 6, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99231 OR (type_code = 'PERSONAL_REIMBURSEMENT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99232, '竣工结算', 'FINAL_SETTLEMENT', 0, 7, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99232 OR (type_code = 'FINAL_SETTLEMENT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99233, '资金调度', 'FUND_TRANSFER', 0, 8, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99233 OR (type_code = 'FUND_TRANSFER' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99234, '备用金申请', 'RESERVE_FUND_APPLY', 0, 9, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99234 OR (type_code = 'RESERVE_FUND_APPLY' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99235, '质保金退还', 'RETENTION_RETURN', 0, 10, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99235 OR (type_code = 'RETENTION_RETURN' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99236, '项目结算', 'PROJECT_SETTLEMENT', 0, 11, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99236 OR (type_code = 'PROJECT_SETTLEMENT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99237, '产值上报', 'OUTPUT_REPORT', 0, 12, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99237 OR (type_code = 'OUTPUT_REPORT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99238, '施工合同', 'CONSTRUCTION_CONTRACT', 0, 13, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99238 OR (type_code = 'CONSTRUCTION_CONTRACT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99239, '变更签证', 'CHANGE_VISA', 0, 14, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99239 OR (type_code = 'CHANGE_VISA' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99240, '竣工验收', 'COMPLETION_ACCEPTANCE', 0, 15, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99240 OR (type_code = 'COMPLETION_ACCEPTANCE' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99241, '预算变更', 'BUDGET_CHANGE', 0, 16, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99241 OR (type_code = 'BUDGET_CHANGE' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99242, '保证金申请', 'DEPOSIT_APPLY', 0, 17, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99242 OR (type_code = 'DEPOSIT_APPLY' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99243, '采购合同', 'PURCHASE_CONTRACT', 0, 18, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99243 OR (type_code = 'PURCHASE_CONTRACT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99244, '采购结算', 'PURCHASE_SETTLEMENT', 0, 19, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99244 OR (type_code = 'PURCHASE_SETTLEMENT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99245, '劳务合同', 'LABOR_CONTRACT', 0, 20, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99245 OR (type_code = 'LABOR_CONTRACT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99246, '劳务产值', 'LABOR_OUTPUT', 0, 21, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99246 OR (type_code = 'LABOR_OUTPUT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99247, '劳务工资单', 'LABOR_PAYROLL', 0, 22, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99247 OR (type_code = 'LABOR_PAYROLL' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99248, '劳务奖惩', 'LABOR_REWARD_PUNISH', 0, 23, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99248 OR (type_code = 'LABOR_REWARD_PUNISH' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99249, '机械合同', 'MACHINE_CONTRACT', 0, 24, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99249 OR (type_code = 'MACHINE_CONTRACT' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99250, '机械结算', 'machine_settlement', 0, 25, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99250 OR (type_code = 'machine_settlement' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99251, '材料调拨', 'MATERIAL_TRANSFER', 0, 26, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99251 OR (type_code = 'MATERIAL_TRANSFER' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99252, '材料退货', 'MATERIAL_REFUND', 0, 27, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99252 OR (type_code = 'MATERIAL_REFUND' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99253, '项目报备', 'PROJECT_FILING', 0, 28, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99253 OR (type_code = 'PROJECT_FILING' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99254, '项目终止', 'PROJECT_TERMINATE', 0, 29, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99254 OR (type_code = 'PROJECT_TERMINATE' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99255, '项目结项', 'PROJECT_CLOSE', 0, 30, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99255 OR (type_code = 'PROJECT_CLOSE' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99256, '入职申请', 'ENTRY_APPLY', 0, 31, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99256 OR (type_code = 'ENTRY_APPLY' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99257, '转正申请', 'REGULAR_APPLY', 0, 32, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99257 OR (type_code = 'REGULAR_APPLY' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99258, '调动申请', 'TRANSFER_APPLY', 0, 33, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99258 OR (type_code = 'TRANSFER_APPLY' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99259, '离职申请', 'RESIGN_APPLY', 0, 34, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99259 OR (type_code = 'RESIGN_APPLY' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99260, '用章申请', 'SEAL_APPLY', 0, 35, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99260 OR (type_code = 'SEAL_APPLY' AND tenant_id = 1 AND deleted = 0));
INSERT INTO wf_business_type (id, type_name, type_code, parent_id, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version)
SELECT 99261, '用车申请', 'VEHICLE_APPLY', 0, 36, 1, 1, NOW(), NOW(), 0, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM wf_business_type WHERE id = 99261 OR (type_code = 'VEHICLE_APPLY' AND tenant_id = 1 AND deleted = 0));
