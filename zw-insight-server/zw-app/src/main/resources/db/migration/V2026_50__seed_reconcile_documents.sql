-- ============================================================
-- 52_V2026_50__seed_reconcile_documents.sql
-- R7-03 修复：补齐演示种子单据，使「累计值 = 单据汇总」全部勾稽对齐
--
-- 背景（2026-09-18 数据审计 Round 7，PASS=50 FAIL=11 WARN=5）：
--   31_V2026_26__seed_demo_data.sql 只写了合同的 cumulative_* 汇总字段，
--   却没有配套写出支撑这些汇总的单据，导致审计 3.1/3.2/3.3/3.4 大面积 MISMATCH。
--   45_V2026_43__seed_closeable_project.sql 补的 90004 更是只有项目+成员+结算 3 行，
--   连施工合同都没有，cumulative_output/total_income/total_expense 三项全部无单据支撑。
--
-- 本迁移的策略（用户 2026-09-18 决策：全量补齐）：
--   **补单据，不改叙事** —— 以既有 cumulative_* 与 biz_project_settlement 声明的
--   构成为「事实源」，反向补齐支撑单据。所有金额均由既有种子/结算单推导，非任意捏造：
--     * 90001 支出侧：按 7 份合同的 cumulative_paid / cumulative_settlement 补付款单与结算单
--     * 90002 收入侧：按 91002 的 cumulative_invoice_amount(32M) / cumulative_received_amount(31M)
--                     与项目 cumulative_output(33.5M) 补开票/收款/产值单
--     * 90002 支出侧：按结算单 93301 声明的构成
--                     （分包5M + 劳务8M + 材料12M + 机械3.5M + 其他1M = 29.5M）建整套合同链
--     * 90004 全链：  按结算单 93302 声明的构成
--                     （分包2M + 劳务3M + 材料2.5M + 机械0.5M = 8M，收入侧 10M）建整套合同链
--
-- 两处必须「改累计值」而非「补单据」的例外（已在下方标注理由）：
--   1. 污染回滚：91501/91801/90001 被 API 测试脚本反复提交 1 元单据抬高了累计值
--   2. 91601/91801 的 cumulative_paid 低于既有付款单金额——种子 L543 注释明写
--      「付款申请（材料600万+劳务400万+分包300万=1300万）」，即作者本意就是
--      91601 付 4M、91801 付 3M，是 cumulative_paid 写错了（3M/2M），不是单据写错了
--
-- 执行前提：
--   必须先执行 keys/cleanup-garbage-data.sh --execute 清掉演示项目下的垃圾单据，
--   否则污染回滚的守卫条件不满足，相关 UPDATE 会被跳过（见文末校验块）。
--
-- 幂等性：
--   * 全部 INSERT 用 INSERT IGNORE + 固定 ID（99500-99999 段，已由探针确认全表空闲）
--   * 全部 UPDATE 带「当前值 = 已知旧值」守卫，重复执行第二次影响 0 行
--   * 新增合同编号已预检唯一（LW20250601001 / FB20250701001 / CG20250501001 /
--     JX20250515001 / HT20250101001 / LW20250101001 / FB20250201001 /
--     CG20250115001 / JX20250120001 当前占用数均为 0）
--
-- 租户：1（演示数据固定租户）
-- ============================================================

-- ============================================================
-- Section 1: 污染累计值回滚（R7-03 阶段 C，已并入本迁移）
-- ============================================================
-- 每条 UPDATE 都带双重守卫：① 当前值等于已知污染值；② 对应的垃圾单据确实已清除。
-- 守卫 ② 保证「先跑本迁移、后跑清理脚本」这种错误顺序不会制造反向 MISMATCH。

-- 91501 采购合同：35 条 1 元 APPROVED 付款单把 cumulative_paid 抬高了 35 元
UPDATE biz_purchase_contract c
   SET c.cumulative_paid = 6000000.00
 WHERE c.id = 91501 AND c.cumulative_paid = 6000035.00
   AND NOT EXISTS (SELECT 1 FROM biz_payment_apply pa
                    WHERE pa.contract_id = 91501 AND pa.deleted = 0 AND pa.status = 'APPROVED'
                      AND pa.id NOT BETWEEN 90001 AND 99999);

-- 91501 采购合同：1 条 1 元 APPROVED 采购结算把 cumulative_settlement 抬高了 1 元
UPDATE biz_purchase_contract c
   SET c.cumulative_settlement = 7000000.00
 WHERE c.id = 91501 AND c.cumulative_settlement = 7000001.00
   AND NOT EXISTS (SELECT 1 FROM biz_purchase_settlement ps
                    WHERE ps.contract_id = 91501 AND ps.deleted = 0 AND ps.status = 'APPROVED'
                      AND ps.id NOT BETWEEN 90001 AND 99999);

-- 91801 分包合同：25 条 10 元 APPROVED 分包结算把 cumulative_settlement 抬高了 250 元
-- （那 25 条本身已是 deleted=1，不参与聚合，故此处只需修字段、无需守卫单据）
UPDATE biz_subcontract
   SET cumulative_settlement = 3000000.00
 WHERE id = 91801 AND cumulative_settlement = 3000250.00;

-- 90001 项目总支出：被上述 35 元付款抬高
UPDATE biz_project
   SET total_expense = 15000000.00
 WHERE id = 90001 AND total_expense = 15000035.00
   AND NOT EXISTS (SELECT 1 FROM biz_payment_apply pa
                    WHERE pa.project_id = 90001 AND pa.deleted = 0 AND pa.status = 'APPROVED'
                      AND pa.id NOT BETWEEN 90001 AND 99999);

-- ============================================================
-- Section 2: 90001 滨江花园一期 —— 支出侧补单
-- ============================================================
-- 现状：7 份合同的 cumulative_paid 合计 19M，但只有 3 张付款单（13M）。
-- 补齐 4 张付款单（8M）后单据合计 21M；同时把 91601/91801 的 cumulative_paid
-- 上调到与既有单据一致（4M/3M），使「合同累计 = 单据汇总」逐条成立，
-- 于是 90001.total_expense 的正确值为 21M（见 Section 2.3）。

-- 2.1 付款申请（4 张，合计 8,000,000）
INSERT IGNORE INTO biz_payment_apply (id, project_id, contract_id, contract_category, supplier_id, supplier_name, payment_amount, payment_date, cumulative_settlement_snapshot, unpaid_amount_snapshot, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99501, 90001, 91502, 'PURCHASE',    90402, '浙江宏远钢铁贸易有限公司',   4000000.00, '2026-04-15', 5000000.00, 1000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99502, 90001, 91602, 'LABOR',       90403, '杭州顺安劳务有限公司',       1000000.00, '2026-05-20', 1500000.00,  500000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99503, 90001, 91701, 'MACHINE',     90404, '浙江力源机械租赁有限公司',   2000000.00, '2026-05-15', 3000000.00, 1000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99504, 90001, 91802, 'SUBCONTRACT', 90406, '浙江中天幕墙工程有限公司',   1000000.00, '2026-06-15', 1000000.00,       0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 2.2 结算单（2 张：91602 劳务 1.5M、91802 分包 1M；其余合同的结算单种子已写）
INSERT IGNORE INTO biz_labor_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99511, 90001, 91602, 1500000.00, 1500000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_subcontract_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99512, 90001, 91802, 1000000.00, 1000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 2.3 cumulative_paid 校正（种子写错的两处）+ 项目总支出对齐
-- 91601 劳务：既有付款单 99032 = 4M，种子却写 cumulative_paid = 3M
UPDATE biz_labor_contract SET cumulative_paid = 4000000.00 WHERE id = 91601 AND cumulative_paid = 3000000.00;
-- 91801 分包：既有付款单 99033 = 3M，种子却写 cumulative_paid = 2M
UPDATE biz_subcontract    SET cumulative_paid = 3000000.00 WHERE id = 91801 AND cumulative_paid = 2000000.00;
-- 90001 总支出 = 各合同 cumulative_paid 汇总 = 6+4+4+1+2+3+1 = 21M
-- 兼容两种前序状态：D-0 已回滚（15000000）或尚未回滚（15000035）
UPDATE biz_project SET total_expense = 21000000.00
 WHERE id = 90001 AND total_expense IN (15000000.00, 15000035.00);

-- ============================================================
-- Section 3: 90002 城南市政道路改造 —— 收入侧补单（施工合同 91002）
-- ============================================================
-- 91002 声明 cumulative_invoice_amount=32M、cumulative_received_amount=31M，
-- 项目 cumulative_output=33.5M、total_income=31M，但一张单据都没有。

-- 3.1 开票申请（2 张 × 16M = 32M）
INSERT IGNORE INTO biz_invoice_apply (id, project_id, contract_id, invoice_type, invoice_amount, invoice_title, taxpayer_id, bank_account, bank_name, contract_amount_snapshot, settlement_amount_snapshot, historical_invoiced_snapshot, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99521, 90002, 91002, 'SPECIAL', 16000000.00, '杭州市城南市政建设管理中心', '91330100MABBBBBB2', '3301009002002002', '中国建设银行杭州分行', 32000000.00, 33500000.00,         0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99522, 90002, 91002, 'SPECIAL', 16000000.00, '杭州市城南市政建设管理中心', '91330100MABBBBBB2', '3301009002002002', '中国建设银行杭州分行', 32000000.00, 33500000.00, 16000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 3.2 收款登记（10M + 10M + 11M = 31M）
INSERT IGNORE INTO biz_payment_received (id, project_id, contract_id, receive_date, receive_amount, receiver, receive_type, receive_bank_account, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99523, 90002, 91002, '2025-09-15', 10000000.00, '杭州市城南市政建设管理中心', '银行转账', '3301009002002002', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99524, 90002, 91002, '2025-12-20', 10000000.00, '杭州市城南市政建设管理中心', '银行转账', '3301009002002002', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99525, 90002, 91002, '2026-03-15', 11000000.00, '杭州市城南市政建设管理中心', '银行转账', '3301009002002002', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 3.3 产值报告（12M + 12M + 9.5M = 33.5M，期间落在合同工期 2025-06-20~2026-05-31 内）
INSERT IGNORE INTO biz_output_report (id, project_id, contract_id, report_period, current_output, cumulative_output, confirm_date, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99526, 90002, 91002, '2025-09', 12000000.00, 12000000.00, '2025-10-05', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99527, 90002, 91002, '2025-12', 12000000.00, 24000000.00, '2026-01-05', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99528, 90002, 91002, '2026-03',  9500000.00, 33500000.00, '2026-04-05', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- ============================================================
-- Section 4: 90002 城南市政道路改造 —— 支出链（按结算单 93301 构成）
-- ============================================================
-- 93301 声明：分包 5M + 劳务 8M + 材料 12M + 机械 3.5M + 其他 1M = 29.5M
--            = cumulative_paid = total_expenditure = biz_project.total_expense
-- 审计 3.3 口径：total_expense 仅与 SUM(biz_payment_apply WHERE APPROVED) 比对
-- （SubcontractSettlementService L225-231 明确 total_expense 为「付款口径」），
-- 故 5 张付款单必须合计 29.5M —— 「其他 1M」走 biz_other_contract + OTHER_EXPENSE
-- 类别付款（PaymentApplyService.addCumulativePaid 对非模块类别路由到 otherContractMapper）。

-- 4.1 分包合同（绿化分包，编号沿用 93313 早已声明的 FB20250701001）
INSERT IGNORE INTO biz_subcontract (id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99531, 90002, 'FB20250701001', 90405, '杭州恒基装饰工程有限公司', '2025-07-01', NULL, 5000000.00, '按进度支付，月结', 5000000.00, 5000000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- 4.2 劳务合同（道路施工劳务，编号沿用 93312 早已声明的 LW20250601001）
INSERT IGNORE INTO biz_labor_contract (id, project_id, contract_code, contract_name, party_a_name, party_b_id, party_b_name, team_name, signing_date, start_date, end_date, budget_id, contract_amount, payment_terms, cumulative_output, cumulative_settlement, cumulative_paid, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99532, 90002, 'LW20250601001', '道路施工劳务分包合同', '中正市政工程有限公司', 90403, '杭州顺安劳务有限公司', '顺安市政劳务一队', '2025-06-01', '2025-06-20', '2026-05-31', NULL, 8000000.00, '月结，次月20日前支付', 8000000.00, 8000000.00, 8000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 4.3 采购合同（材料 12M）
INSERT IGNORE INTO biz_purchase_contract (id, project_id, contract_code, contract_name, party_a_id, party_a_name, party_b_id, party_b_name, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_inbound, cumulative_settlement, cumulative_paid, cumulative_invoice_received, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99533, 90002, 'CG20250501001', '道路材料采购合同', 90302, '中正市政工程有限公司', 90401, '杭州鑫达建材有限公司', '杭州鑫达建材有限公司', '2025-05-01', NULL, 12000000.00, '月结，次月15日前支付', 12000000.00, 12000000.00, 12000000.00, 12000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 4.4 机械合同（3.5M）
INSERT IGNORE INTO biz_machine_contract (id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99534, 90002, 'JX20250515001', 90404, '浙江力源机械租赁有限公司', '2025-05-15', NULL, 3500000.00, '月结，次月10日前支付', 3500000.00, 3500000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- 4.5 其他支出合同（1M；biz_other_contract 无 contract_code 列）
INSERT IGNORE INTO biz_other_contract (id, project_id, contract_name, contract_category, party_a_name, party_b_name, contract_amount, tax_rate, amount_without_tax, tax_amount, signing_date, payment_terms, cooperation_content, cumulative_invoice, cumulative_received, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99535, 90002, '市政配套检测服务协议', 'OTHER_EXPENSE', '中正市政工程有限公司', '杭州正信检测有限公司', 1000000.00, 6.00, 943396.23, 56603.77, '2025-08-01', '检测完成后30日内支付', '道路竣工检测+雨污分流闭水试验', 0.00, 0.00, 1000000.00, 1000000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- 4.6 四类结算单（金额与合同 cumulative_settlement 一致）
INSERT IGNORE INTO biz_subcontract_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99536, 90002, 99531, 5000000.00, 5000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_labor_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99537, 90002, 99532, 8000000.00, 8000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_purchase_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99538, 90002, 99533, 12000000.00, 12000000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_machine_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99539, 90002, 99534, 3500000.00, 3500000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 4.7 付款申请（5 张，合计 29,500,000 = biz_project.total_expense）
INSERT IGNORE INTO biz_payment_apply (id, project_id, contract_id, contract_category, supplier_id, supplier_name, payment_amount, payment_date, cumulative_settlement_snapshot, unpaid_amount_snapshot, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99541, 90002, 99531, 'SUBCONTRACT',   90405, '杭州恒基装饰工程有限公司',   5000000.00, '2025-10-20',  5000000.00,       0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99542, 90002, 99532, 'LABOR',         90403, '杭州顺安劳务有限公司',       8000000.00, '2025-11-20',  8000000.00,       0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99543, 90002, 99533, 'PURCHASE',      90401, '杭州鑫达建材有限公司',      12000000.00, '2025-12-15', 12000000.00,       0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99544, 90002, 99534, 'MACHINE',       90404, '浙江力源机械租赁有限公司',   3500000.00, '2026-01-10',  3500000.00,       0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99545, 90002, 99535, 'OTHER_EXPENSE', NULL,  '杭州正信检测有限公司',       1000000.00, '2026-02-28',  1000000.00,       0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- ============================================================
-- Section 5: 修正 93301 结算单的跨项目引用缺陷
-- ============================================================
-- 取证：93311-93313 属于结算单 93301（project_id=90002），contract_id 却指向
-- 90001 的合同 91501/91601/91801；且 93312/93313 的 contract_code
-- （LW20250601001 / FB20250701001）与 91601/91801 的真实编号
-- （LW20260201001 / FB20260301001）不符 —— 证明种子作者本意就是给 90002
-- 建自己的合同（编号是 2025 年，契合 90002 的 2025 工期），只是漏建了。
-- 现按 Section 4 补出的合同把引用接回本项目。

UPDATE biz_settlement_contract_detail
   SET contract_id = 99533, contract_code = 'CG20250501001', contract_name = '道路材料采购'
 WHERE id = 93311 AND settlement_id = 93301 AND contract_id = 91501;

UPDATE biz_settlement_contract_detail
   SET contract_id = 99532
 WHERE id = 93312 AND settlement_id = 93301 AND contract_id = 91601;

UPDATE biz_settlement_contract_detail
   SET contract_id = 99531
 WHERE id = 93313 AND settlement_id = 93301 AND contract_id = 91801;

-- 93301 还声明了机械 3.5M 与其他 1M，但明细表里缺这两行，补齐
INSERT IGNORE INTO biz_settlement_contract_detail (id, tenant_id, settlement_id, contract_type, contract_id, contract_code, contract_name, contract_amount, settled_amount, paid_amount, unsettled_amount, settlement_status, created_by, created_at, updated_at, deleted, version) VALUES
(99546, 1, 93301, 'MACHINE', 99534, 'JX20250515001', '机械租赁',       3500000.00, 3500000.00, 3500000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0),
(99547, 1, 93301, 'OTHER',   99535, NULL,            '市政配套检测服务', 1000000.00, 1000000.00, 1000000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0);

-- ============================================================
-- Section 6: 90004 城北河道综合整治工程 —— 全链补齐（按结算单 93302 构成）
-- ============================================================
-- 93302 声明：收入侧 10M（产值/开票/收款均 10M）；
--            支出侧 分包 2M + 劳务 3M + 材料 2.5M + 机械 0.5M = 8M
--            = cumulative_paid = total_expenditure = biz_project.total_expense
-- 注意：90004 是 E2E 结项链路夹具（45_V2026_43），ProjectService.checkCloseConditions
-- 的四项预检只读 biz_project 字段 + 结算单存在性，本 Section 不改 status /
-- cumulative_output / total_income，故结项预检结果不受影响（②仍为 |10M-10M|=0）。

-- 6.1 施工合同（90004 原先连施工合同都没有，而产值报告 contract_id 为 NOT NULL）
INSERT IGNORE INTO biz_construction_contract (id, project_id, project_name, contract_code, contract_type, parent_contract_id, party_a_name, party_a_id, signing_date, start_date, end_date, contract_amount, tax_rate, amount_without_tax, tax_amount, cumulative_change_amount, cumulative_output, cumulative_invoice_amount, cumulative_received_amount, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99551, 90004, '城北河道综合整治工程', 'HT20250101001', 'REGISTER', NULL, '杭州市城南市政建设管理中心', 90502, '2025-01-05', '2025-01-15', '2025-12-31', 10000000.00, 9.00, 9174311.93, 825688.07, 0.00, 10000000.00, 10000000.00, 10000000.00, 'SETTLED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 6.2 收入侧单据（产值 10M / 开票 10M / 收款 10M）
INSERT IGNORE INTO biz_output_report (id, project_id, contract_id, report_period, current_output, cumulative_output, confirm_date, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99552, 90004, 99551, '2025-11', 10000000.00, 10000000.00, '2025-12-05', 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_invoice_apply (id, project_id, contract_id, invoice_type, invoice_amount, invoice_title, taxpayer_id, bank_account, bank_name, contract_amount_snapshot, settlement_amount_snapshot, historical_invoiced_snapshot, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99553, 90004, 99551, 'SPECIAL', 10000000.00, '杭州市城南市政建设管理中心', '91330100MABBBBBB2', '3301009004004004', '中国建设银行杭州分行', 10000000.00, 10000000.00, 0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_payment_received (id, project_id, contract_id, receive_date, receive_amount, receiver, receive_type, receive_bank_account, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99554, 90004, 99551, '2025-12-28', 10000000.00, '杭州市城南市政建设管理中心', '银行转账', '3301009004004004', 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 6.3 支出侧四份合同
INSERT IGNORE INTO biz_subcontract (id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99555, 90004, 'FB20250201001', 90406, '浙江中天幕墙工程有限公司', '2025-02-01', NULL, 2000000.00, '按进度支付，月结', 2000000.00, 2000000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_labor_contract (id, project_id, contract_code, contract_name, party_a_name, party_b_id, party_b_name, team_name, signing_date, start_date, end_date, budget_id, contract_amount, payment_terms, cumulative_output, cumulative_settlement, cumulative_paid, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99556, 90004, 'LW20250101001', '河道清淤劳务分包合同', '中正建设集团有限公司', 90403, '杭州顺安劳务有限公司', '顺安市政劳务三队', '2025-01-01', '2025-01-15', '2025-12-31', NULL, 3000000.00, '月结，次月20日前支付', 3000000.00, 3000000.00, 3000000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_purchase_contract (id, project_id, contract_code, contract_name, party_a_id, party_a_name, party_b_id, party_b_name, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_inbound, cumulative_settlement, cumulative_paid, cumulative_invoice_received, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99557, 90004, 'CG20250115001', '驳坎石材采购合同', 90301, '中正建设集团有限公司', 90401, '杭州鑫达建材有限公司', '杭州鑫达建材有限公司', '2025-01-15', NULL, 2500000.00, '货到付款，7日内支付', 2500000.00, 2500000.00, 2500000.00, 2500000.00, 'EFFECTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_machine_contract (id, project_id, contract_code, supplier_id, supplier_name, signing_date, budget_id, contract_amount, payment_terms, cumulative_settlement, cumulative_paid, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99558, 90004, 'JX20250120001', 90404, '浙江力源机械租赁有限公司', '2025-01-20', NULL, 500000.00, '月结，次月10日前支付', 500000.00, 500000.00, 'EFFECTIVE', 1, 1, NOW(), NOW(), 0, 0);

-- 6.4 四类结算单
INSERT IGNORE INTO biz_subcontract_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99559, 90004, 99555, 2000000.00, 2000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_labor_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99560, 90004, 99556, 3000000.00, 3000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_purchase_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99561, 90004, 99557, 2500000.00, 2500000.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_machine_settlement (id, project_id, contract_id, settlement_amount, cumulative_settlement, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99562, 90004, 99558, 500000.00, 500000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 6.5 付款申请（4 张，合计 8,000,000 = biz_project.total_expense）
INSERT IGNORE INTO biz_payment_apply (id, project_id, contract_id, contract_category, supplier_id, supplier_name, payment_amount, payment_date, cumulative_settlement_snapshot, unpaid_amount_snapshot, status, workflow_instance_id, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99563, 90004, 99555, 'SUBCONTRACT', 90406, '浙江中天幕墙工程有限公司',   2000000.00, '2025-06-20', 2000000.00, 0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99564, 90004, 99556, 'LABOR',       90403, '杭州顺安劳务有限公司',       3000000.00, '2025-07-20', 3000000.00, 0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99565, 90004, 99557, 'PURCHASE',    90401, '杭州鑫达建材有限公司',       2500000.00, '2025-08-15', 2500000.00, 0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99566, 90004, 99558, 'MACHINE',     90404, '浙江力源机械租赁有限公司',    500000.00, '2025-09-10',  500000.00, 0.00, 'APPROVED', NULL, 1, 1, NOW(), NOW(), 0, 0);

-- 6.6 93302 结算单明细（原先一行都没有，按声明构成补齐 4 行）
INSERT IGNORE INTO biz_settlement_contract_detail (id, tenant_id, settlement_id, contract_type, contract_id, contract_code, contract_name, contract_amount, settled_amount, paid_amount, unsettled_amount, settlement_status, created_by, created_at, updated_at, deleted, version) VALUES
(99567, 1, 93302, 'SUBCONTRACT', 99555, 'FB20250201001', '滨水绿道铺装分包', 2000000.00, 2000000.00, 2000000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0),
(99568, 1, 93302, 'LABOR',       99556, 'LW20250101001', '河道清淤劳务',     3000000.00, 3000000.00, 3000000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0),
(99569, 1, 93302, 'MATERIAL',    99557, 'CG20250115001', '驳坎石材采购',     2500000.00, 2500000.00, 2500000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0),
(99570, 1, 93302, 'MACHINE',     99558, 'JX20250120001', '机械租赁',          500000.00,  500000.00,  500000.00, 0.00, 'SETTLED', 1, NOW(), NOW(), 0, 0);

-- ============================================================
-- Section 7: 执行后自校验（输出到客户端，便于人工确认）
-- ============================================================
-- 每行 diff 应为 0.00；非 0 说明前序清理未执行或存在其他污染，需人工介入。

SELECT '7.1 支出合同 cumulative_paid 勾稽' AS chk, c.id, c.cumulative_paid AS contract_val,
       COALESCE(SUM(pa.payment_amount),0) AS doc_sum,
       ABS(COALESCE(c.cumulative_paid,0) - COALESCE(SUM(pa.payment_amount),0)) AS diff
  FROM (SELECT id, cumulative_paid, 'L' k FROM biz_labor_contract   WHERE deleted=0 AND project_id BETWEEN 90001 AND 90004
        UNION ALL SELECT id, cumulative_paid, 'M' FROM biz_machine_contract  WHERE deleted=0 AND project_id BETWEEN 90001 AND 90004
        UNION ALL SELECT id, cumulative_paid, 'S' FROM biz_subcontract       WHERE deleted=0 AND project_id BETWEEN 90001 AND 90004
        UNION ALL SELECT id, cumulative_paid, 'P' FROM biz_purchase_contract WHERE deleted=0 AND project_id BETWEEN 90001 AND 90004) c
  LEFT JOIN biz_payment_apply pa ON pa.contract_id = c.id AND pa.status='APPROVED' AND pa.deleted=0
 GROUP BY c.id, c.cumulative_paid
HAVING diff > 0.01
 ORDER BY c.id;

SELECT '7.2 项目 total_expense 勾稽' AS chk, p.id, p.total_expense,
       COALESCE(SUM(pa.payment_amount),0) AS doc_sum,
       ABS(COALESCE(p.total_expense,0) - COALESCE(SUM(pa.payment_amount),0)) AS diff
  FROM biz_project p
  LEFT JOIN biz_payment_apply pa ON pa.project_id = p.id AND pa.status='APPROVED' AND pa.deleted=0
 WHERE p.deleted=0 AND p.id BETWEEN 90001 AND 90004
 GROUP BY p.id, p.total_expense
 ORDER BY p.id;

SELECT '7.3 项目 total_income / cumulative_output 勾稽' AS chk, p.id, p.total_income,
       (SELECT COALESCE(SUM(r.receive_amount),0) FROM biz_payment_received r
         WHERE r.project_id=p.id AND r.deleted=0) AS receipts,
       p.cumulative_output,
       (SELECT COALESCE(SUM(o.current_output),0) FROM biz_output_report o
         WHERE o.project_id=p.id AND o.deleted=0 AND o.status='APPROVED') AS outputs
  FROM biz_project p WHERE p.deleted=0 AND p.id BETWEEN 90001 AND 90004 ORDER BY p.id;

SELECT '7.4 施工合同开票/收款勾稽' AS chk, c.id, c.cumulative_invoice_amount,
       (SELECT COALESCE(SUM(i.invoice_amount),0) FROM biz_invoice_apply i
         WHERE i.contract_id=c.id AND i.deleted=0 AND i.status='APPROVED') AS invoiced,
       c.cumulative_received_amount,
       (SELECT COALESCE(SUM(r.receive_amount),0) FROM biz_payment_received r
         WHERE r.contract_id=c.id AND r.deleted=0 AND r.status='APPROVED') AS received
  FROM biz_construction_contract c
 WHERE c.deleted=0 AND c.project_id BETWEEN 90001 AND 90004 ORDER BY c.id;

SELECT '7.5 结算单明细跨项目引用自检（应为 0 行）' AS chk, d.id, d.settlement_id,
       s.project_id AS settlement_project, d.contract_id
  FROM biz_settlement_contract_detail d
  JOIN biz_project_settlement s ON s.id = d.settlement_id
 WHERE d.deleted=0 AND d.settlement_id IN (93301, 93302)
   AND NOT EXISTS (
     SELECT 1 FROM biz_labor_contract   x WHERE x.id=d.contract_id AND x.project_id=s.project_id
     UNION ALL SELECT 1 FROM biz_subcontract       x WHERE x.id=d.contract_id AND x.project_id=s.project_id
     UNION ALL SELECT 1 FROM biz_purchase_contract x WHERE x.id=d.contract_id AND x.project_id=s.project_id
     UNION ALL SELECT 1 FROM biz_machine_contract  x WHERE x.id=d.contract_id AND x.project_id=s.project_id
     UNION ALL SELECT 1 FROM biz_other_contract    x WHERE x.id=d.contract_id AND x.project_id=s.project_id);
