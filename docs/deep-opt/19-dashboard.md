# 19-驾驶舱 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 仅蓝图，此前无同模块文件；不改聚合业务/迁移/测试/总控/GOAL，不运行Java/mvn与生产写。无本轮生产取证，金额/性能不作实测结论。path:line按1-based。

## 1. 现状真相表

|能力|判定|path:line|
|---|---|---|
|经营总览/风险/下钻|已实现真实Service链，不是mock；Controller有forecast与realized区分、gapBasis、unsupportedDimensions说明|`zw-insight-server/zw-dashboard/src/main/java/com/zwinsight/dashboard/controller/CockpitController.java:32-67,106-138,214-256`|
|累计支付/已实现利润|总览paid取项目total_expense；realized=total_income-total_expense，属于审批回写收支差，不能称银行已付款或会计利润|`zw-insight-server/zw-dashboard/src/main/java/com/zwinsight/dashboard/service/CockpitService.java:123-132`|
|利润月趋势|有真实回款/付款源单据，但付款只过滤APPROVED；pay_date空回退payment_date，UNPAID也按全额入月；PARTIAL只取银行勾稽额。现金与审批混合，注释称同源不能证明口径正确|`zw-insight-server/zw-dashboard/src/main/java/com/zwinsight/dashboard/service/DashboardService.java:673-717`|
|预计成本fallback|无CBS时回退total_expense并标FALLBACK_TOTAL_EXPENSE；有披露不代表其可当完工预测。缺预测与真零需分开|`zw-insight-server/zw-dashboard/src/main/java/com/zwinsight/dashboard/service/ProfitSnapshotService.java:38,145-148`；`zw-insight-web/src/views/cockpit/index.vue:175`|
|权限|类dashboard:view；风险处理/手动扫描也继承view。聚合和按ID下钻需逐链验证数据权限，未取到全链证据，不宣称tenant过滤等于项目范围|`CockpitController.java:39,182-203`；`CockpitService.java:100,220,379`|
|供应商/合同下钻|Mapper直接跨五类合同SQL，COALESCE归一名称；名称聚合不能替代供应商身份，同名混并/空名称需披露|`zw-insight-server/zw-dashboard/src/main/java/com/zwinsight/dashboard/mapper/DrillDownMapper.java:23-47`|
|机械结算来源|M7已发现旧表只读历史与新结算错位；当前M7未提交补丁不能当驾驶舱已统一成果|`docs/deep-opt/07-machine.md:38-41,105,148-149`|
|性能|存在全量projects读取；利润趋势先查全部APPROVED再Java按年分组；无生产EXPLAIN/p95，不能称性能已合格或断言超时|`CockpitService.java:217-220`；`DashboardService.java:687-716`|
|PC/App|PC经营/项目/成本/资金/风险真实入口；App风险详情真实接口，暂无本次证据证明与PC全功能同等|`zw-insight-web/src/views/cockpit/index.vue:320-336,907-917`；`zw-insight-app/src/pages/cockpit/risk-detail.vue:92,189-218`|
|测试|Cockpit/Dashboard/ProfitSnapshot/DrillDown/RiskScan等ServiceTest、风险边界与项目驾驶舱属性测试存在；无本轮执行/覆盖率测量|`zw-insight-server/zw-dashboard/src/test/java/com/zwinsight/dashboard/service/DashboardServiceTest.java`；`zw-insight-server/zw-dashboard/src/test/java/com/zwinsight/dashboard/risk/rule/RiskRulesBoundaryTest.java`|

## 2. 数据考古发现

无本轮生产数字。AGENTS的CBS种子/历史审计与M7的机械口径问题是背景；种子金额与某轮PASS不能代替现在真实公司经营情况，两机不合并。

只读SQL未执行，先核对主机IP、容器、DB与企业，再确认列/索引。以下不回填累计值。

```sql
SELECT DATABASE(),@@hostname;
SELECT table_name,column_name FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name IN
 ('biz_project','biz_payment_apply','biz_cost_account','biz_cost_account_txn','biz_profit_snapshot');
SELECT tenant_id,status,pay_status,COUNT(*) n,SUM(payment_amount) amount,
 SUM(pay_date IS NULL) missing_cash_date FROM biz_payment_apply
 WHERE deleted=0 GROUP BY tenant_id,status,pay_status;
SELECT tenant_id,COUNT(*) n,SUM(total_income) income,SUM(total_expense) approval_expense,
 SUM(receivable_amount) receivable FROM biz_project WHERE deleted=0 GROUP BY tenant_id;
SELECT p.tenant_id,p.id,COUNT(c.id) accounts FROM biz_project p
 LEFT JOIN biz_cost_account c ON c.project_id=p.id AND c.tenant_id=p.tenant_id AND c.deleted=0
 WHERE p.deleted=0 GROUP BY p.tenant_id,p.id HAVING COUNT(c.id)=0;
SELECT a.tenant_id,a.id,a.actual_amount,COALESCE(t.actual_sum,0) txn_actual
 FROM biz_cost_account a LEFT JOIN
 (SELECT tenant_id,account_id,SUM(delta_amount) actual_sum FROM biz_cost_account_txn
 WHERE amount_type='ACTUAL' GROUP BY tenant_id,account_id) t
 ON t.account_id=a.id AND t.tenant_id=a.tenant_id
 WHERE a.deleted=0 AND ABS(a.actual_amount-COALESCE(t.actual_sum,0))>0.01;
```

amount_type值需先按实际枚举确认，未证实前不直接采信上式零行。付款审批累计还含资金调拨，不能仅Σpayment_amount就宣布total_expense不匹配；other_payment单列。补探针：OPEN应收净额、CBS根节点forecast为空率/流水勾稽、快照时效/成本basis分布、各合同结算来源、手工PAID与银行多次部分支付。性能先只读EXPLAIN，不在生产跑EXPLAIN ANALYZE重查询；sql报错必须FAIL。

## 3. 成熟度基线

人工暂评L2/L3：真实聚合、风险、穿透较完整；现金/审批混合、数据范围和预测完整性不闭合，不能L4。未运行feature-ledger。八维人工检查：功能（口径切换）、流程（风险处理权）、数据（多源勾稽）、规则（现金/审批分离）、权限（项目范围）、体验（UNKNOWN/STALE）、集成（M4/M7/M9账本）、质量（权限/性能/边界）。非工具自动评分。

## 4. 缺口矩阵（vs 标杆/规范/实绩）

|能力|我们|规范/标杆|实绩|
|---|---|---|---|
|金额可解释|有basis/note但趋势混合|AGENTS双口径与CBS流水约束已核对，属项目权威约束|未取证|
|钻透守恒|有穿透链，机械旧新表待统一|项目领域不变量；商业BI产品能力未逐项核实|未取证|
|缺值不伪零|局部fallback已披露|本项目禁止mock/静默fallback；不将缺源宣称经营零风险|未取证|
|性能/范围一致|未获真实证据|没有已核实法规或产品p95要求，门A定义目标|未取证|

本轮无已读取外部财务准则原文，不称total_income-total_expense为法定会计利润，不编造标杆产品/规范条款。

## 5. 目标状态机与业务不变量

聚合读模型按源版本/时间产生 `READY / PARTIAL / STALE / ERROR` 数据质量标记（目标协议，非现有状态）；风险沿既有 OPEN/PROCESSING/RESOLVED/IGNORED与重开，读写权限分离。

1. 审批支出=total_expense及其审批单据/调拨依据；现金支出=实际银行匹配明细或有凭证的手工实付；pay_status变化不回写total_expense/合同cumulative_paid。
2. 现金分月以每条实际支付流水日期与金额归属；多次部分支付不得全部压到申请单最后pay_date。手工PAID无银行流水另列MANUAL证据，UNPAID不得因payment_date被算现金。
3. 审批趋势与现金趋势分别命名、basis、单位、时间字段、数据覆盖率；禁止一条曲线混PARTIAL现金额与UNPAID审批全额。
4. 预计利润=合同收入−CBS完工预测成本；无CBS/forecast缺失为PARTIAL/UNKNOWN，不补成已实现支出后无条件混入可比排名。真0与缺值分开。
5. 全局筛选、卡片、趋势、风险、导出、下钻用同一tenant+可见项目集合；公司筛选不是授权机制。无范围返回拒绝/空集合明确语义，不能退回全公司。
6. 顶层总数与下钻同源勾稽，容差0.01；CBS账户余额=流水Σdelta，根叶去重；合同累计依据旧历史+新单据的M7批准口径，不双计。应收=OPEN净额。
7. 逾期未付只入当月预测一次；overdue_unpaid是expected_payments构成，不二次相加；风险影响金额重叠不能直接Σ当总敞口。
8. 聚合不改源账；快照保留basis/asOf，缓存key含租户/范围/筛选，失效失败不能静默复用其他用户缓存。

## 6. 分档实现计划（范围/代价/依赖）

人日粗估S=1-2/M=3-5/L=6-10，不含确认/环境等待。

|档/项|范围/代价|依赖|
|---|---|---|
|A1 口径分离|L：卡片标签、审批/现金趋势、实际支付多次明细、手工支付来源|M9银行勾稽/调拨，不能仅加pay_status=PAID漏掉部分支付|
|A2 范围安全|L：所有聚合/快照/风险/钻透共用可见项目范围，view与handle/scan分权|M18权限目录与M1成员；不能凭新权限码未登记导致仅超级管理员可用|
|A3 数据质量/勾稽|L：forecast缺值披露、排名可比性、旧新机械/其他支出口径、R7可触发校验|M4 CBS、M7结算、M9应收与现金，业务源未闭合前不修显示数字|
|A4 性能最小治理|M：先测EXPLAIN/数据量，再DB时间过滤/聚合/分页、范围缓存|安全范围先定；索引另批双轨迁移|
|B 体验|M：口径提示、质量徽标、时效/源单据说明、PC/App错误态|A完成后，保留现有chart组件|
|C 高级分析|L以上：自助BI/预测模型/跨公司分析|真实数据量与业务需求到来后做|

较省方案：先改口径与范围，复用当前Mapper/图表；未证明瓶颈不添数据仓库/新BI平台。

## 7. 生产数据语义与决策

A1属于展示/计算语义改变，不授权重算项目累计、篡改付款状态、删除旧结算或覆写历史快照。待定：主卡是审批收支差还是现金收支差；手工PAID证据政策；缺预测项目是否排除排名/展示不完整公司合计；快照保留旧basis并版本化还是另列新曲线。M7旧表只读历史政策不在此重新授权；归集/下钻须等其最终口径。历史重算另列时间范围、备份、差异预览与授权。回滚切回旧展示但明确标记旧口径，保留新版快照与勾稽证据；不能为对齐图表反写源账。

## 8. 验证方案

本轮仅方案，不运行测试、不写测试文件。后续CI租户9999场景：APPROVED+UNPAID只进审批不进现金；跨月PARTIAL两条流水分月；PAID手工/银行两类明示；资金调拨和other_payment不重计；缺CBS、forecast=NULL、真0、旧快照、负利润分别展示；机械历史+新结算不双计；同名供应商下钻准确；公司/项目/SELF范围卡片与钻透守恒；无权ID/跨租户/缓存复用拒绝；view不能handle/scan。R7账户/应收/累计勾稽加入负向样本确保FAIL可触发。PC八卡、趋势、风险、面包屑与App风险详情抽验。性能在隔离环境按真实匿名规模，门A确认p95/超时/并发目标后验收，不在生产压测。测试文件存在不表示本轮通过。

## 9. 门A待确认与确认记录

- 待确认A1-A4、主口径/曲线命名、手工实付政策、缺预测排名与公司汇总、性能验收目标。
- 未核实：双机实际数据与版本、全链数据范围拦截、最新现金匹配schema、快照质量/时效、生产性能及外部财务标准。
- 门A未确认，门B未进入；本轮没有任何生产数据语义执行变更。
