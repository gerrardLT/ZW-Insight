-- 验证包装（后置）：在事务内检查 V2026_66 的效果，最后 ROLLBACK 回滚，零副作用
-- 用法：cat pre.sql V2026_66.sql post.sql | mysql ...

SELECT '=== 1. 各表新增行数 ===' AS x;
SELECT 'cost_subcategory(92309-92320)' AS t, COUNT(*) AS n FROM biz_cost_subcategory WHERE id BETWEEN 92309 AND 92320
UNION ALL SELECT 'wbs_node(99601-99609)', COUNT(*) FROM biz_project_wbs_node WHERE id BETWEEN 99601 AND 99609
UNION ALL SELECT 'cost_account(99611-99657)', COUNT(*) FROM biz_cost_account WHERE id BETWEEN 99611 AND 99657
UNION ALL SELECT 'fund_monthly_plan', COUNT(*) FROM biz_fund_monthly_plan WHERE id IN (99661,99662)
UNION ALL SELECT 'fund_plan_detail', COUNT(*) FROM biz_fund_plan_detail WHERE id BETWEEN 99663 AND 99667
UNION ALL SELECT 'proj_reimbursement', COUNT(*) FROM biz_project_reimbursement WHERE id BETWEEN 99671 AND 99675
UNION ALL SELECT 'reimb_detail', COUNT(*) FROM biz_reimbursement_detail WHERE id BETWEEN 99681 AND 99689
UNION ALL SELECT 'entertainment_detail', COUNT(*) FROM biz_entertainment_detail WHERE id BETWEEN 99691 AND 99700;

SELECT '=== 2. 审计 Section 2 对 biz_cost_account 的四项不变量（必须全 0）===' AS x;
SELECT 'orphan_parent' AS chk, COUNT(*) AS n FROM biz_cost_account c LEFT JOIN biz_cost_account p ON c.parent_id=p.id WHERE c.parent_id IS NOT NULL AND c.deleted=0 AND p.id IS NULL
UNION ALL SELECT 'orphan_wbs', COUNT(*) FROM biz_cost_account c LEFT JOIN biz_project_wbs_node w ON c.wbs_node_id=w.id WHERE c.wbs_node_id IS NOT NULL AND c.deleted=0 AND w.id IS NULL
UNION ALL SELECT 'dup_code', COUNT(*) FROM (SELECT tenant_id, project_id, account_code FROM biz_cost_account WHERE deleted=0 GROUP BY tenant_id, project_id, account_code HAVING COUNT(*)>1) t
UNION ALL SELECT 'negative_amount', COUNT(*) FROM biz_cost_account WHERE deleted=0 AND (baseline_amount<0 OR current_amount<0 OR commitment_amount<0 OR actual_amount<0 OR forecast_amount<0);

SELECT '=== 3. CBS 合计对齐（万元）：baseline 应等于项目 budget_amount ===' AS x;
SELECT p.id, ROUND(p.budget_amount/10000,1) AS budget_w,
       ROUND(SUM(a.baseline_amount)/10000,1) AS baseline_w,
       ROUND(SUM(a.actual_amount)/10000,1) AS actual_w,
       ROUND(SUM(a.forecast_amount)/10000,1) AS forecast_w,
       ROUND((SUM(a.forecast_amount)-SUM(a.baseline_amount))/10000,1) AS overrun_w,
       COUNT(*) AS accounts
  FROM biz_project p JOIN biz_cost_account a ON a.project_id=p.id AND a.deleted=0
 WHERE p.deleted=0 AND p.id BETWEEN 90001 AND 99999
 GROUP BY p.id, p.budget_amount ORDER BY p.id;

SELECT '=== 4. CBS actual 按类别 vs 五类合同结算（万元，应逐类相等）===' AS x;
SELECT a.project_id, a.cost_category, ROUND(SUM(a.actual_amount)/10000,1) AS cbs_actual_w
  FROM biz_cost_account a WHERE a.deleted=0 AND a.id BETWEEN 99611 AND 99657
 GROUP BY a.project_id, a.cost_category ORDER BY a.project_id, a.cost_category;

SELECT '=== 5. 七类归类：间接费子类名是否含关键词（措施/管理/商务）===' AS x;
SELECT project_id, cost_subcategory, account_name,
       CASE
         WHEN cost_category IN ('MATERIAL','LABOR','MACHINE','SUBCONTRACT') THEN 'CBS_CATEGORY'
         WHEN cost_subcategory REGEXP '措施|临设|临时设施|安全|文明|防护|脚手架|检测|试验|排水|围挡' THEN 'MEASURE'
         WHEN cost_subcategory REGEXP '招待|差旅|车辆|会议|办公|商务|投标|经营|报价' THEN 'BUSINESS'
         WHEN cost_subcategory REGEXP '管理|工资|审计|咨询|专家|房租|水电|生活|保险|税费' THEN 'ADMIN'
         ELSE 'UNCLASSIFIED'
       END AS doc_category
  FROM biz_cost_account WHERE deleted=0 AND id BETWEEN 99611 AND 99657
 ORDER BY project_id, id;

SELECT '=== 6. 招待费分析口径核对（当月实际 vs 计划限额）===' AS x;
SELECT DATE_FORMAT(COALESCE(pr.reimbursement_date, DATE(d.created_at)), '%Y-%m') AS m,
       COUNT(*) AS cnt, SUM(d.amount) AS amount
  FROM biz_reimbursement_detail d
  JOIN biz_project_reimbursement pr ON pr.id=d.reimbursement_id AND pr.deleted=0 AND pr.status='APPROVED'
 WHERE d.deleted=0 AND d.category_code='EXP-INDIRECT-ENTERTAIN' AND d.source_type='PROJECT'
 GROUP BY m ORDER BY m;

SELECT '当月计划限额' AS k, fd.amount FROM biz_fund_plan_detail fd
  JOIN biz_fund_monthly_plan p ON p.id=fd.plan_id AND p.deleted=0 AND p.status='APPROVED'
 WHERE fd.deleted=0 AND fd.category_code='EXP-INDIRECT-ENTERTAIN' AND p.project_id=90001
   AND p.plan_year=YEAR(CURDATE()) AND p.plan_month=MONTH(CURDATE());

SELECT '=== 7. 预警样本核对（同人同日 / 高频 / 无发票 / 无审批 / 无事由 / 单笔超限）===' AS x;
SELECT 'same_handler_same_day' AS chk, COUNT(*) AS n FROM (
  SELECT e.handler_id, e.entertain_date FROM biz_reimbursement_detail d
    JOIN biz_entertainment_detail e ON e.reimbursement_detail_id=d.id AND e.deleted=0
   WHERE d.deleted=0 AND d.category_code='EXP-INDIRECT-ENTERTAIN'
   GROUP BY e.handler_id, e.entertain_date HAVING COUNT(*)>=2) t
UNION ALL SELECT 'frequent_handler_this_month', COUNT(*) FROM (
  SELECT e.handler_id FROM biz_reimbursement_detail d
    JOIN biz_entertainment_detail e ON e.reimbursement_detail_id=d.id AND e.deleted=0
   WHERE d.deleted=0 AND d.category_code='EXP-INDIRECT-ENTERTAIN'
     AND DATE_FORMAT(e.entertain_date,'%Y-%m')=DATE_FORMAT(CURDATE(),'%Y-%m')
   GROUP BY e.handler_id HAVING COUNT(*)>5) t
UNION ALL SELECT 'no_invoice', COUNT(*) FROM biz_reimbursement_detail d
    JOIN biz_entertainment_detail e ON e.reimbursement_detail_id=d.id AND e.deleted=0
   WHERE d.deleted=0 AND d.category_code='EXP-INDIRECT-ENTERTAIN' AND COALESCE(e.has_invoice,0)=0
UNION ALL SELECT 'no_pre_approval', COUNT(*) FROM biz_reimbursement_detail d
    JOIN biz_entertainment_detail e ON e.reimbursement_detail_id=d.id AND e.deleted=0
   WHERE d.deleted=0 AND d.category_code='EXP-INDIRECT-ENTERTAIN' AND COALESCE(e.pre_approved,0)=0
UNION ALL SELECT 'no_reason', COUNT(*) FROM biz_reimbursement_detail d
    JOIN biz_entertainment_detail e ON e.reimbursement_detail_id=d.id AND e.deleted=0
   WHERE d.deleted=0 AND d.category_code='EXP-INDIRECT-ENTERTAIN' AND (e.entertain_reason IS NULL OR e.entertain_reason='')
UNION ALL SELECT 'single_over_3000', COUNT(*) FROM biz_reimbursement_detail d
   WHERE d.deleted=0 AND d.category_code='EXP-INDIRECT-ENTERTAIN' AND d.amount>3000;

SELECT '=== 8. 主表金额 = 明细合计（前端强校验规则）===' AS x;
SELECT r.id, r.total_amount, SUM(d.amount) AS detail_sum,
       CASE WHEN ABS(r.total_amount - SUM(d.amount))<0.01 THEN 'MATCH' ELSE 'MISMATCH' END AS chk
  FROM biz_project_reimbursement r JOIN biz_reimbursement_detail d ON d.reimbursement_id=r.id AND d.deleted=0
 WHERE r.deleted=0 AND r.id BETWEEN 99671 AND 99675 GROUP BY r.id, r.total_amount ORDER BY r.id;

SELECT '=== 9. 既有明细科目修复结果 ===' AS x;
SELECT id, category_code, expense_type, amount FROM biz_reimbursement_detail WHERE id IN (99061,99062);

SELECT '=== 10. 招待费专项与明细一一对应（uk_reimbursement_detail 无孤儿）===' AS x;
SELECT 'ent_orphan_detail' AS chk, COUNT(*) AS n
  FROM biz_entertainment_detail e LEFT JOIN biz_reimbursement_detail d ON d.id=e.reimbursement_detail_id AND d.deleted=0
 WHERE e.deleted=0 AND d.id IS NULL;

-- 全部检查完毕，回滚（零副作用）
ROLLBACK;
SELECT '=== ROLLED BACK（以上均为事务内验证，未写库）===' AS x;
