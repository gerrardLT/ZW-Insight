-- ============================================================
-- V2026_66__seed_cbs_entertainment.sql
-- 演示种子补齐：CBS 成本账户 + WBS 节点 + 间接费子类字典 + 招待费报销链路 + 月度资金计划
--
-- 【为什么需要本脚本】2026-09-24 线上探针实证：
--   biz_cost_account 0 行 / biz_project_wbs_node 0 行 / biz_entertainment_detail 0 行
--   biz_reimbursement_detail 仅 2 行且 category_code 全为 NULL
--   biz_fund_monthly_plan 0 行 / biz_fund_plan_detail 0 行
--   → 成本中心（含 §7.2 七类结构）、招待费分析（§6.3/§8.1）、§11 第 8 类「超月度限额」
--     预警均无数据可验；驾驶舱预计利润的 costBasis 恒为 FALLBACK_TOTAL_EXPENSE
--     （「预计」退化为「已实现支出」，不具备预测意义）。
--   根因：31_V2026_26 的成本账户种子挂在 project_id=92001，而真实演示项目是 90001-90004，
--        该批数据从未入库。本脚本按真实项目重建。
--
-- 【口径自洽（不得凭空写数，AGENTS.md「累计值必须有单据支撑」）】
--   Σ CBS actual_amount 按类别精确等于对应五类支出合同的 cumulative_settlement：
--     90001：材料 1200万=purchase 1200万 / 人工 500万=labor 500万 / 机械 300万=machine 300万
--            分包 400万=subcontract 400万 / 其他 30万=other 30万；间接费 220.63万来自报销
--     90002：材料 1200万 / 人工 800万 / 机械 350万 / 分包 500万 / 其他 100万（=结算 2950万）
--     90004：材料 250万 / 人工 300万 / 机械 50万 / 分包 200万（=结算 800万）
--   Σ CBS baseline_amount = 项目 budget_amount（90001=4500万 / 90002=3000万 / 90004=1000万）
--   招待费账户 actual = 该项目招待费报销明细合计（90001=40700 元，含既有 99062 的 20000 元）
--   注：CBS actual 为「已结算成本」口径，与 biz_project.total_expense 的「付款」口径不同，
--       差额即应付未付（90001 结算 2430万 − 已付 2110万 = 320万，与线上 payableOutstanding 一致）。
--
-- 【ID 段】92309-92320（子类字典）/ 99601-99609（WBS）/ 99611-99657（成本账户）
--          99661-99667（资金计划）/ 99671-99675（报销单）/ 99681-99689（报销明细）
--          99691-99700（招待费专项）。均为探针确认的空闲段（99600-99899 各表 0 行）。
-- 【租户】全部 tenant_id=1（演示租户）；created_by=1（admin）
-- 【幂等】INSERT IGNORE（主键/唯一键冲突静默跳过）+ UPDATE 按主键定位，可重复执行
--
-- 【与 CostRollUpTask 的关系（必读）】
--   baseline_amount（目标成本）/ current_amount（含变更预算）/ forecast_amount（EAC 预测）
--   属人工编制值，归集任务不改；**actual_amount / commitment_amount 由 CostRollUpTask
--   每日 02:30 按真实单据汇总做「目标绝对值对账」**（CostRollUpService.syncAccount：
--   delta = 单据汇总值 − 当前值，幂等键 ROLLUP:{accountId}:{amountType}:{from}->{to}）。
--   本脚本写入的 actual 取「五类支出合同 cumulative_settlement + 报销」口径作为初始快照，
--   若与归集任务的单据口径存在差异，次日会被**自动校正为单据汇总值**并留下 txn 流水——
--   这是设计行为（累计值必须有单据支撑），不是数据错误。部署后应比对校正前后的差额，
--   并把校正后的值记为演示基线。
-- 【审计安全】不触碰 biz_project.total_* / 合同 cumulative_* / 付款申请，
--            故 audit-data-round7 Section 3 金额勾稽与 R7 基线 PASS=65 不受影响；
--            Section 2 对 biz_cost_account 的四项检查（孤儿 parent / 孤儿 wbs / 编码重复 / 负值）
--            本脚本均满足：parent_id 全 NULL、wbs_node_id 均指向本脚本插入的节点、
--            account_code 按 (tenant,project) 唯一、金额全为正。
-- ============================================================

-- ------------------------------------------------------------
-- 1) 间接费子类字典补齐（UI §7.2「措施/管理/商务」三类的命名依据）
--    既有字典仅 MATERIAL/LABOR/MACHINE/SUBCONTRACT 四类共 8 行（92301-92308），
--    INDIRECT 无任何子类 → 间接费账户无法归类，七类结构会全部落「未归类」。
--    子类名与 ProjectCostControlService.DOC_SUBCATEGORY_RULES 关键词表对应。
-- ------------------------------------------------------------
INSERT IGNORE INTO biz_cost_subcategory (id, cost_category, subcategory_name, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(92309, 'INDIRECT', '措施费',         1, 1, 1, NOW(), NOW(), 0, 0),
(92310, 'INDIRECT', '临设费',         2, 1, 1, NOW(), NOW(), 0, 0),
(92311, 'INDIRECT', '安全文明施工费',  3, 1, 1, NOW(), NOW(), 0, 0),
(92312, 'INDIRECT', '检测试验费',      4, 1, 1, NOW(), NOW(), 0, 0),
(92313, 'INDIRECT', '管理费',         5, 1, 1, NOW(), NOW(), 0, 0),
(92314, 'INDIRECT', '管理人员工资',    6, 1, 1, NOW(), NOW(), 0, 0),
(92315, 'INDIRECT', '审计咨询费',      7, 1, 1, NOW(), NOW(), 0, 0),
(92316, 'INDIRECT', '招待费',         8, 1, 1, NOW(), NOW(), 0, 0),
(92317, 'INDIRECT', '差旅费',         9, 1, 1, NOW(), NOW(), 0, 0),
(92318, 'INDIRECT', '车辆费',        10, 1, 1, NOW(), NOW(), 0, 0),
(92319, 'INDIRECT', '会议费',        11, 1, 1, NOW(), NOW(), 0, 0),
(92320, 'INDIRECT', '办公费',        12, 1, 1, NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 2) WBS 节点（成本中心左侧树的真实数据源；90003 已报备未开工故不建）
-- ------------------------------------------------------------
INSERT IGNORE INTO biz_project_wbs_node (id, project_id, parent_id, node_level, node_code, node_name, description, status, sort_order, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99601, 90001, NULL, 1, 'WBS-90001-01', '地基与基础', '桩基、土方、基础承台', 'ACTIVE', 1, 1, 1, NOW(), NOW(), 0, 0),
(99602, 90001, NULL, 1, 'WBS-90001-02', '主体结构',   '钢筋混凝土主体框架',   'ACTIVE', 2, 1, 1, NOW(), NOW(), 0, 0),
(99603, 90001, NULL, 1, 'WBS-90001-03', '装饰装修',   '内外墙装饰与精装',     'ACTIVE', 3, 1, 1, NOW(), NOW(), 0, 0),
(99604, 90002, NULL, 1, 'WBS-90002-01', '道路工程',   '路基路面与沥青摊铺',   'ACTIVE', 1, 1, 1, NOW(), NOW(), 0, 0),
(99605, 90002, NULL, 1, 'WBS-90002-02', '排水工程',   '雨污水管道敷设',       'ACTIVE', 2, 1, 1, NOW(), NOW(), 0, 0),
(99606, 90002, NULL, 1, 'WBS-90002-03', '交通设施',   '标志标线与信号灯',     'ACTIVE', 3, 1, 1, NOW(), NOW(), 0, 0),
(99607, 90004, NULL, 1, 'WBS-90004-01', '河道清淤',   '淤泥清运与河床整形',   'ACTIVE', 1, 1, 1, NOW(), NOW(), 0, 0),
(99608, 90004, NULL, 1, 'WBS-90004-02', '护岸工程',   '生态护岸与挡墙',       'ACTIVE', 2, 1, 1, NOW(), NOW(), 0, 0),
(99609, 90004, NULL, 1, 'WBS-90004-03', '景观绿化',   '滨水景观与绿化种植',   'ACTIVE', 3, 1, 1, NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 3) CBS 成本账户 —— 项目 90001 滨江花园一期（施工中）
--    Σbaseline=4500万(=budget_amount) Σactual=2650.63万 Σforecast=4931万 → 预计超支 431万
--    覆盖七类：材料/人工/机械/分包（直接映射）+ 措施/管理/商务（子类关键词）+ 未归类（OTHER）
-- ------------------------------------------------------------
INSERT IGNORE INTO biz_cost_account (id, project_id, parent_id, wbs_node_id, account_code, account_name, cost_category, cost_subcategory, baseline_amount, current_amount, commitment_amount, actual_amount, forecast_amount, status, remark, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99611, 90001, NULL, 99601, 'CBS-90001-01', '钢材采购',     'MATERIAL',    '主材',   19000000.00, 19800000.00, 12000000.00,  9000000.00, 22500000.00, 'ACTIVE', '预计超支 270万（涨价与损耗），七类中材料为 RED', 1, 1, NOW(), NOW(), 0, 0),
(99612, 90001, NULL, 99601, 'CBS-90001-02', '水泥砂石',     'MATERIAL',    '辅材',    6500000.00,  6500000.00,  3000000.00,  3000000.00,  6200000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99613, 90001, NULL, 99602, 'CBS-90001-03', '结构劳务',     'LABOR',       '主体劳务', 5000000.00,  5200000.00,  5000000.00,  4800000.00,  5400000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99614, 90001, NULL, 99603, 'CBS-90001-04', '装修劳务',     'LABOR',       '装饰劳务', 1000000.00,  1000000.00,        0.00,   200000.00,  1000000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99615, 90001, NULL, 99602, 'CBS-90001-05', '塔吊租赁',     'MACHINE',     '大型机械', 2800000.00,  2900000.00,  2600000.00,  2600000.00,  3200000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99616, 90001, NULL, NULL,  'CBS-90001-06', '小型机械',     'MACHINE',     '小型机械',  600000.00,   600000.00,   400000.00,   400000.00,   550000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99617, 90001, NULL, 99603, 'CBS-90001-07', '精装修分包',   'SUBCONTRACT', '装饰分包', 4000000.00,  4200000.00,  4000000.00,  4000000.00,  4800000.00, 'ACTIVE', '预计超支 60万（14.3%）→ RED', 1, 1, NOW(), NOW(), 0, 0),
(99618, 90001, NULL, NULL,  'CBS-90001-08', '临时设施费',   'INDIRECT',    '临设费',   2000000.00,  2000000.00,  1000000.00,   950000.00,  2150000.00, 'ACTIVE', '七类归「措施」（子类含"临设"）', 1, 1, NOW(), NOW(), 0, 0),
(99619, 90001, NULL, NULL,  'CBS-90001-09', '项目管理费',   'INDIRECT',    '管理费',   2500000.00,  2500000.00,        0.00,  1200000.00,  2400000.00, 'ACTIVE', '七类归「管理」', 1, 1, NOW(), NOW(), 0, 0),
(99620, 90001, NULL, NULL,  'CBS-90001-10', '业务招待费',   'INDIRECT',    '招待费',    600000.00,   600000.00,        0.00,    40700.00,   480000.00, 'ACTIVE', 'actual=招待费报销明细合计（含既有 99062 的 20000 元）；七类归「商务」', 1, 1, NOW(), NOW(), 0, 0),
(99621, 90001, NULL, NULL,  'CBS-90001-11', '差旅费',       'INDIRECT',    '差旅费',    200000.00,   200000.00,        0.00,    15600.00,   180000.00, 'ACTIVE', 'actual=既有报销明细 99061；七类归「商务」', 1, 1, NOW(), NOW(), 0, 0),
(99622, 90001, NULL, NULL,  'CBS-90001-12', '其他间接支出', 'OTHER',        NULL,       800000.00,   800000.00,   300000.00,   300000.00,   750000.00, 'ACTIVE', '子类为空 → 七类落「未归类」，用于演示 UNCLASSIFIED 告知（不静默并入他类）', 1, 1, NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 4) CBS 成本账户 —— 项目 90002 城南市政道路改造（已竣工）
--    Σbaseline=3000万 Σactual=3060万 Σforecast=3090万 → 预计超支 90万
-- ------------------------------------------------------------
INSERT IGNORE INTO biz_cost_account (id, project_id, parent_id, wbs_node_id, account_code, account_name, cost_category, cost_subcategory, baseline_amount, current_amount, commitment_amount, actual_amount, forecast_amount, status, remark, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99631, 90002, NULL, 99604, 'CBS-90002-01', '钢材',         'MATERIAL',    '主材',   9000000.00, 9000000.00, 8800000.00, 8800000.00, 8900000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99632, 90002, NULL, 99604, 'CBS-90002-02', '沥青与辅材',   'MATERIAL',    '辅材',   3000000.00, 3000000.00, 3200000.00, 3200000.00, 3200000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99633, 90002, NULL, 99604, 'CBS-90002-03', '结构劳务',     'LABOR',       '主体劳务', 6500000.00, 7000000.00, 7000000.00, 7000000.00, 7000000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99634, 90002, NULL, 99606, 'CBS-90002-04', '装饰劳务',     'LABOR',       '装饰劳务',  800000.00, 1000000.00, 1000000.00, 1000000.00, 1000000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99635, 90002, NULL, 99605, 'CBS-90002-05', '大型机械',     'MACHINE',     '大型机械', 2800000.00, 3000000.00, 3000000.00, 3000000.00, 3000000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99636, 90002, NULL, NULL,  'CBS-90002-06', '小型机械',     'MACHINE',     '小型机械',  400000.00,  500000.00,  500000.00,  500000.00,  500000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99637, 90002, NULL, 99605, 'CBS-90002-07', '专业分包',     'SUBCONTRACT', '专业分包', 4500000.00, 5000000.00, 5000000.00, 5000000.00, 5000000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99638, 90002, NULL, NULL,  'CBS-90002-08', '安全文明施工费','INDIRECT',   '安全文明施工费', 700000.00, 700000.00, 500000.00, 500000.00, 550000.00, 'ACTIVE', '七类归「措施」（子类含"安全""文明"）', 1, 1, NOW(), NOW(), 0, 0),
(99639, 90002, NULL, NULL,  'CBS-90002-09', '项目管理费',   'INDIRECT',    '管理费',  1100000.00, 1100000.00,  600000.00,  600000.00,  700000.00, 'ACTIVE', '七类归「管理」', 1, 1, NOW(), NOW(), 0, 0),
(99640, 90002, NULL, NULL,  'CBS-90002-10', '业务招待费',   'INDIRECT',    '招待费',   300000.00,  300000.00,       0.00,       0.00,   50000.00, 'ACTIVE', '七类归「商务」', 1, 1, NOW(), NOW(), 0, 0),
(99641, 90002, NULL, NULL,  'CBS-90002-11', '其他费用',     'OTHER',       '其他间接支出', 900000.00, 900000.00, 1000000.00, 1000000.00, 1000000.00, 'ACTIVE', '子类无关键词 → 七类落「未归类」', 1, 1, NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 5) CBS 成本账户 —— 项目 90004 城北河道综合整治（已竣工可结项）
--    Σbaseline=1000万 Σactual=830万 Σforecast=837万 → 预计超支 −163万（未超支，GREEN）
-- ------------------------------------------------------------
INSERT IGNORE INTO biz_cost_account (id, project_id, parent_id, wbs_node_id, account_code, account_name, cost_category, cost_subcategory, baseline_amount, current_amount, commitment_amount, actual_amount, forecast_amount, status, remark, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99651, 90004, NULL, 99608, 'CBS-90004-01', '主材',       'MATERIAL',    '主材',     3000000.00, 3000000.00, 2500000.00, 2500000.00, 2500000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99652, 90004, NULL, 99608, 'CBS-90004-02', '结构劳务',   'LABOR',       '主体劳务', 3000000.00, 3000000.00, 3000000.00, 3000000.00, 3000000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99653, 90004, NULL, 99607, 'CBS-90004-03', '大型机械',   'MACHINE',     '大型机械',  600000.00,  600000.00,  500000.00,  500000.00,  500000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99654, 90004, NULL, 99609, 'CBS-90004-04', '专业分包',   'SUBCONTRACT', '专业分包', 2000000.00, 2000000.00, 2000000.00, 2000000.00, 2000000.00, 'ACTIVE', NULL, 1, 1, NOW(), NOW(), 0, 0),
(99655, 90004, NULL, NULL,  'CBS-90004-05', '措施费',     'INDIRECT',    '措施费',    400000.00,  400000.00,       0.00,       0.00,       0.00, 'ACTIVE', '七类归「措施」', 1, 1, NOW(), NOW(), 0, 0),
(99656, 90004, NULL, NULL,  'CBS-90004-06', '项目管理费', 'INDIRECT',    '管理费',    800000.00,  800000.00,  300000.00,  300000.00,  350000.00, 'ACTIVE', '七类归「管理」', 1, 1, NOW(), NOW(), 0, 0),
(99657, 90004, NULL, NULL,  'CBS-90004-07', '业务招待费', 'INDIRECT',    '招待费',    200000.00,  200000.00,       0.00,       0.00,    20000.00, 'ACTIVE', '七类归「商务」', 1, 1, NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 6) 月度资金计划 + 科目明细（§11 第 8 类「超月度限额」的分母来源）
--    plan_year/plan_month 用 CURDATE() 动态取当月，保证脚本执行后当月即可验证预警；
--    ⚠ 演示夹具的固有限制：跨月后需重跑本脚本所在环境或手工调整月份（Flyway 只执行一次，
--      故线上跨月后该项预警会因「无当月计划」而不判定——这是如实行为，不是缺陷）。
--    expense_plan = Σ 本科目明细（1328000），不凭空写总额。
-- ------------------------------------------------------------
INSERT IGNORE INTO biz_fund_monthly_plan (id, tenant_id, plan_year, plan_month, project_id, income_plan, expense_plan, actual_income, actual_expense, remark, status, created_by, created_at, updated_at, deleted, version)
SELECT 99661, 1, YEAR(CURDATE()), MONTH(CURDATE()), 90001, 2000000.00, 1328000.00, 0.00, 0.00,
       '演示：滨江花园一期当月资金计划（招待费限额 8000 元，用于验证超月度限额预警）',
       'APPROVED', 1, NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM biz_fund_monthly_plan WHERE id = 99661);

INSERT IGNORE INTO biz_fund_monthly_plan (id, tenant_id, plan_year, plan_month, project_id, income_plan, expense_plan, actual_income, actual_expense, remark, status, created_by, created_at, updated_at, deleted, version)
SELECT 99662, 1, YEAR(CURDATE()), MONTH(CURDATE()), NULL, 8000000.00, 5200000.00, 0.00, 0.00,
       '演示：公司级当月资金计划', 'APPROVED', 1, NOW(), NOW(), 0, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM biz_fund_monthly_plan WHERE id = 99662);

INSERT IGNORE INTO biz_fund_plan_detail (id, tenant_id, plan_id, direction, category_code, amount, remark, created_by, created_at, updated_at, deleted, version) VALUES
(99663, 1, 99661, 'EXPENSE', 'EXP-INDIRECT-ENTERTAIN',    8000.00, '招待费月度限额（当月实际 13100 元 → 触发超月度限额预警）', 1, NOW(), NOW(), 0, 0),
(99664, 1, 99661, 'EXPENSE', 'EXP-INDIRECT-TRAVEL',      20000.00, '差旅费计划', 1, NOW(), NOW(), 0, 0),
(99665, 1, 99661, 'EXPENSE', 'EXP-DIRECT-MATERIAL',     800000.00, '材料款计划', 1, NOW(), NOW(), 0, 0),
(99666, 1, 99661, 'EXPENSE', 'EXP-DIRECT-LABOR',        500000.00, '劳务款计划', 1, NOW(), NOW(), 0, 0),
(99667, 1, 99662, 'EXPENSE', 'EXP-INDIRECT-ENTERTAIN',   30000.00, '公司级招待费限额', 1, NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 7) 修正既有报销明细的科目缺失（真实数据修复，非新增）
--    99061 差旅费 / 99062 招待费 的 category_code 原为 NULL，
--    导致招待费分析（按 category_code='EXP-INDIRECT-ENTERTAIN' 匹配）命中 0 行。
-- ------------------------------------------------------------
UPDATE biz_reimbursement_detail SET category_code = 'EXP-INDIRECT-TRAVEL',    expense_type = '差旅费', updated_at = NOW()
 WHERE id = 99061 AND deleted = 0 AND category_code IS NULL;
UPDATE biz_reimbursement_detail SET category_code = 'EXP-INDIRECT-ENTERTAIN', expense_type = '招待费', updated_at = NOW()
 WHERE id = 99062 AND deleted = 0 AND category_code IS NULL;

-- 为既有招待费明细 99062 补专项记录（uk_reimbursement_detail 保证一条明细只有一条专项）
INSERT IGNORE INTO biz_entertainment_detail (id, tenant_id, reimbursement_detail_id, project_id, entertain_date, host_company, host_persons, entertain_reason, host_count, guest_count, location, handler_id, handler_name, has_invoice, pay_method, pre_approved, remark, created_by, created_at, updated_at, deleted, version) VALUES
(99700, 1, 99062, 90001, '2026-06-20', '甲方滨江房产开发公司', '王总、李工', '甲方及监理工作协调会议用餐', 3, 4, '杭州滨江某酒店', 90071, '张伟', 1, 'CORPORATE', 1, '历史数据补齐专项记录', 1, NOW(), NOW(), 0, 0);

-- ------------------------------------------------------------
-- 8) 新增招待费报销链路（覆盖近 4 个月，使月度趋势有多月数据）
--    主表 status='APPROVED'（分析口径仅统计已生效报销单）；
--    主表 total_amount = 本单明细合计（与前端强校验规则一致）。
--    异常样本按 §11 逐类布置，使八类预警中可静态构造的均有命中：
--      99681 单笔 5800 > 3000 限额（RED）
--      99682 has_invoice=0 发票不完整（RED）
--      99688 entertain_reason='' 无事由（RED）
--      99683 pre_approved=0 无事前审批（YELLOW）
--      99683/99684 同人同日多笔（YELLOW）
--      99681-99686 同一经办人当月 6 笔 > 5 次上限（YELLOW）
--      当月合计 13100 > 计划限额 8000（YELLOW，第 8 类）
-- ------------------------------------------------------------
INSERT IGNORE INTO biz_project_reimbursement (id, project_id, total_amount, reimbursement_date, offset_reserve, offset_amount, status, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99671, 90001,  8600.00, DATE_FORMAT(CURDATE(), '%Y-%m-05'), 0, 0.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99672, 90001,  4500.00, DATE_FORMAT(CURDATE(), '%Y-%m-12'), 0, 0.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99673, 90001,  2600.00, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-18'), 0, 0.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99674, 90001,  3200.00, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '%Y-%m-20'), 0, 0.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
(99675, 90001,  1800.00, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 MONTH), '%Y-%m-15'), 0, 0.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_reimbursement_detail (id, reimbursement_id, source_type, expense_type, category_code, amount, remark, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES
(99681, 99671, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN', 5800.00, '单笔超限额样本（>3000）', 1, 1, NOW(), NOW(), 0, 0),
(99682, 99671, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN', 2800.00, '发票缺失样本', 1, 1, NOW(), NOW(), 0, 0),
(99683, 99672, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN', 1600.00, '无事前审批 + 同人同日样本', 1, 1, NOW(), NOW(), 0, 0),
(99684, 99672, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN', 1200.00, '同人同日第二笔（疑似拆单）', 1, 1, NOW(), NOW(), 0, 0),
(99685, 99672, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN',  900.00, '高频经办人样本', 1, 1, NOW(), NOW(), 0, 0),
(99686, 99672, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN',  800.00, '高频经办人样本', 1, 1, NOW(), NOW(), 0, 0),
(99687, 99673, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN', 2600.00, '上月正常样本', 1, 1, NOW(), NOW(), 0, 0),
(99688, 99674, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN', 3200.00, '无事由样本', 1, 1, NOW(), NOW(), 0, 0),
(99689, 99675, 'PROJECT', '招待费', 'EXP-INDIRECT-ENTERTAIN', 1800.00, '三个月前正常样本', 1, 1, NOW(), NOW(), 0, 0);

INSERT IGNORE INTO biz_entertainment_detail (id, tenant_id, reimbursement_detail_id, project_id, entertain_date, host_company, host_persons, entertain_reason, host_count, guest_count, location, handler_id, handler_name, has_invoice, pay_method, pre_approved, remark, created_by, created_at, updated_at, deleted, version) VALUES
(99691, 1, 99681, 90001, DATE_FORMAT(CURDATE(), '%Y-%m-05'), '甲方滨江房产开发公司', '王总等 3 人', '主体结构验收协调用餐', 2, 3, '滨江某酒楼', 90071, '张伟', 1, 'CORPORATE', 1, NULL, 1, NOW(), NOW(), 0, 0),
(99692, 1, 99682, 90001, DATE_FORMAT(CURDATE(), '%Y-%m-06'), '监理单位', '李工等 2 人', '监理例会工作餐', 2, 2, '项目部食堂', 90071, '张伟', 0, 'CASH', 1, '发票缺失（has_invoice=0）', 1, NOW(), NOW(), 0, 0),
(99693, 1, 99683, 90001, DATE_FORMAT(CURDATE(), '%Y-%m-12'), '分包单位', '陈经理', '分包进度协调', 1, 1, '城西餐厅', 90071, '张伟', 1, 'CORPORATE', 0, '无事前审批（pre_approved=0）', 1, NOW(), NOW(), 0, 0),
(99694, 1, 99684, 90001, DATE_FORMAT(CURDATE(), '%Y-%m-12'), '材料供应商', '赵总', '材料供货洽谈', 1, 2, '城西餐厅', 90071, '张伟', 1, 'CORPORATE', 1, '与 99683 同人同日（拆单嫌疑）', 1, NOW(), NOW(), 0, 0),
(99695, 1, 99685, 90001, DATE_FORMAT(CURDATE(), '%Y-%m-13'), '设计院', '孙工', '图纸会审工作餐', 2, 2, '高新区餐厅', 90071, '张伟', 1, 'CORPORATE', 1, NULL, 1, NOW(), NOW(), 0, 0),
(99696, 1, 99686, 90001, DATE_FORMAT(CURDATE(), '%Y-%m-14'), '甲方滨江房产开发公司', '王总', '进度款沟通', 1, 1, '滨江某茶室', 90071, '张伟', 1, 'CORPORATE', 1, '当月第 6 笔（超高频上限 5）', 1, NOW(), NOW(), 0, 0),
(99697, 1, 99687, 90001, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-18'), '监理单位', '李工等 2 人', '月度监理例会', 2, 3, '项目部附近餐厅', 90072, '李娜', 1, 'CORPORATE', 1, '正常样本（无任何异常）', 1, NOW(), NOW(), 0, 0),
(99698, 1, 99688, 90001, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 2 MONTH), '%Y-%m-20'), '某供应商', '未登记', '', 1, 2, '未登记', 90073, '王强', 1, 'CASH', 1, '无事由样本（entertain_reason 为空串）', 1, NOW(), NOW(), 0, 0),
(99699, 1, 99689, 90001, DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 3 MONTH), '%Y-%m-15'), '甲方滨江房产开发公司', '王总、李工', '开工协调会用餐', 3, 3, '滨江某酒店', 90072, '李娜', 1, 'CORPORATE', 1, '正常样本', 1, NOW(), NOW(), 0, 0);
