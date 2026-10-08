# 09-财务 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 仅蓝图，不实施、不运行测试、不查生产。主链优先取证付款、现金执行、应收核销及CBS边界；报销、备用金、开票、资金计划等次链尚需深化。M7 当前工作树未提交改动不代表生产已完成。

## 1. 现状真相表

F=`zw-insight-server/zw-finance/src/main/java/com/zwinsight/finance/`。路径别名前缀与行号可直接定位。

| 子域 | 真实现状 | 证据 path:line |
|---|---|---|
| 新增付款 | 校验金额>0、科目与计划；非空科目才校验，不能当作完整合同项目归属验证 | `F/service/PaymentApplyService.java:89-102` |
| 草稿更新 | 仅检查原状态DRAFT，随后直接 updateById(body)；未复用创建金额/计划校验、未过滤请求status/累计快照/现金态 | `F/service/PaymentApplyService.java:119-127` |
| 提交 | 有预算BLOCK/WARN、可付上限、金额档位、流程启动及快照 | `F/service/PaymentApplyService.java:165-219` |
| 分类路由 | 四模块走对应表，其他分类/空值回退其他合同；可付结构只含累计结算和已付，需补严谨项目归属/支出合同分类验证 | `F/service/PaymentApplyService.java:508-522` |
| 审批回调 | APPROVED顺序重复被挡；其他状态未限制为SUBMITTED；先读上限再原子加，未证明并发双回调或双单余额安全 | `F/service/PaymentApplyService.java:259-294` |
| 双口径 | 审批增合同累计已付及项目total_expense；现金勾稽仅改pay_status/pay_date，不再增项目支出 | `F/service/PaymentApplyService.java:288-293,316-373` |
| 部分付款 | 按银行勾稽合计判UNPAID/PARTIAL_PAID/PAID；按首笔记录日期账户，不是每笔现金的月度事实表 | `F/service/PaymentApplyService.java:339-371` |
| 手工支付 | 已审批、无勾稽可标记；有勾稽/部分支付拒绝；可撤销无流水手工标记 | `F/service/PaymentApplyService.java:383-441` |
| 删除/批量 | 正常仅草稿可删；E2E旁路已批删除同时反冲合同/项目；批量整体事务。但银行关联与核销影响需专门验证 | `F/service/PaymentApplyService.java:139-155,230-251` |
| 权限/封账 | 类级finance:view，提交/现金标记用finance:payment:submit；save/update带FinanceLockCheck，delete/mark/revoke未见同注解，不能据此宣称所有路径受封账保护 | `F/controller/PaymentApplyController.java:24,45-68,91-108` |
| 应收核销 | 有从结算生成应收、核销明细和按回款ID精确反冲，项目应收同链更新；并发与已删应收分支需专项验证 | `F/service/ReceivableService.java:67-103,122-164,185-209` |
| PC契约 | PC付款submit用PUT，Controller明确兼容POST/PUT，不能误报方法错位 | `zw-insight-web/src/api/finance.ts:111-112`；`F/controller/PaymentApplyController.java:67` |
| 测试 | 已有PaymentApplyService/ReceivableService及Controller测试；没有运行，不报覆盖率或通过 | `zw-insight-server/zw-finance/src/test/java/com/zwinsight/finance/service/PaymentApplyServiceTest.java:1`；`ReceivableServiceTest.java:1`（同目录） |

App完整资金操作入口、真实角色授权、银行流水配对方向与拆分约束、回款改额的全链、本期滚动预测部分支付余额，尚未完整取证；不能把这些候选风险写作已发生事故。

## 2. 数据考古发现

两生产环境均未取证，现无真实分布数字。AGENTS历史基线/种子金额不是本轮实绩。操作前核对主机IP、容器名、库名和企业；只读探针输出亦须去除账号、银行卡等敏感信息。

待执行只读SQL（`:tenant_id`由审核者绑定；先按实际schema核列）：

```sql
SELECT table_name,column_name,column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN
('biz_payment_apply','biz_bank_flow','biz_receivable','biz_receivable_write_off','biz_fund_plan','biz_cost_account_txn');
SELECT status,pay_status,COUNT(*) n,SUM(payment_amount) amount
FROM biz_payment_apply WHERE tenant_id=:tenant_id AND deleted=0 GROUP BY status,pay_status;
SELECT id,status,pay_status,payment_amount FROM biz_payment_apply
WHERE tenant_id=:tenant_id AND deleted=0
AND (payment_amount<=0 OR (status<>'APPROVED' AND pay_status IN ('PAID','PARTIAL_PAID')));
SELECT p.id,p.receivable_amount,COALESCE(r.balance,0) document_balance
FROM biz_project p LEFT JOIN
(SELECT project_id,tenant_id,SUM(receivable_amount-written_off_amount) balance
 FROM biz_receivable WHERE deleted=0 AND status='OPEN' GROUP BY project_id,tenant_id) r
ON r.project_id=p.id AND r.tenant_id=p.tenant_id
WHERE p.tenant_id=:tenant_id AND p.deleted=0
AND ABS(COALESCE(p.receivable_amount,0)-COALESCE(r.balance,0))>0.01;
SELECT id,receivable_amount,written_off_amount FROM biz_receivable
WHERE tenant_id=:tenant_id AND deleted=0
AND (written_off_amount<0 OR written_off_amount>receivable_amount);
```

后续按R7实际表达式核total_expense（包含资金调拨、排除other_payment）；不可仅Σ付款申请而误判调拨差额。核银行匹配剩余、回款核销明细、现金首笔与分月、计划超支、CBS流水余额。SQL错误是失败，不是零异常；种子、测试与业务数据分层统计，未确认不清理。

## 3. 成熟度基线（人工）

未运行账本工具：付款审批L3；现金执行L3（并发及多笔月度口径未证明）；应收核销L3；权限/封账L2；PC L2/L3；App、开票/报销/备用金/资金计划暂不评分。整体主链L3但不达L4。

八维分析：数据缺更新信任边界；状态缺回调CAS；规则缺跨项目与分类硬约束证据；权限写操作分权不足；并发缺余额预占与双回调证据；财务双口径已建立但分月/部分余额须深化；体验真实现金状态与审批提示需统一；验证存量测试存在而竞态/真实角色尚未证明。非自动扫描结论。

## 4. 缺口矩阵（标杆/规范/实绩）

| 能力 | 我们 | 对照依据 | 实绩 |
|---|---|---|---|
| 付款双口径 | 已建立 | AGENTS项目强制约定，不得退回“审批即银行支付” | 未取证 |
| 并发余额硬约束 | 读校验后累加 | MySQL官方locking reads可保护事务读后更新；条件更新亦可，但必须检查行数 | 未取证 |
| 账期/复核/出纳职责 | 需逐路径核验 | 内控制度候选，用户决定；未核实会计法规条款 | 未取证 |
| 自动网银/凭证 | 本轮未取证 | 标杆官方功能树未核实，不按印象列为必做 | 未取证 |

已读取官方来源：https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html （2026-10-08）。行业会计/税务条款、具体产品官方能力及适用企业制度尚未核实；不编条款号、税率、URL。

## 5. 目标状态机与业务不变量

审批DRAFT→SUBMITTED→APPROVED/REJECTED；现金UNPAID→PARTIAL_PAID→PAID，取消勾稽可逆；批准纠错与实际退款采用可追溯反向事实，不能混为删除。

- FI-1：付款创建/编辑/提交/回调都验证正金额、分精度、同租户同项目支出合同、合法分类及有效科目；请求不能写状态、累计和现金执行字段。
- FI-2：付款批准累计不超过已确认可付余额（结算+合法净奖罚−已批准款）；同单重复与同合同不同单并发分别保护。
- FI-3：审批支出=批准付款与合法调拨回写；现金支付=银行匹配/受控手工支付事实；CBS actual=履约结算，不用现金额替换。
- FI-4：核销明细与应收written_off_amount、项目OPEN余额同事务且可精确反冲；核销不可超回款与应收余额。
- FI-5：部分付款预测按剩余未付金额；逾期余额只纳当月一次，overdue_unpaid是构成项，不再相加。现实现是否全部满足待验证，不能凭AGENTS推定。
- FI-6：封账覆盖所有改变金额、现金态与核销的入口，包括批量与后台回调；跨期纠错走明确制度。
- FI-7：账本/幂等流水不删除；无合法办理人不伪造流程成功；银行关联的单据删除必须拒绝或经明确冲销流程。

M7机械结算新旧来源与FINANCE办理链路尚未完成；M8结算/奖罚口径、M4CBS和M17流程均为依赖，不能宣称本蓝图已统一。

## 6. 分档实现计划

| 档/项 | 范围与估算（单人工作日，含测试） | 依赖 |
|---|---|---|
| A1 信任边界 | 付款更新白名单、创建同源校验、合同项目分类检查、权限拆分；3–5日 | M1/M18 |
| A2 余额并发 | 状态CAS、合同余额条件更新/锁、回调非法状态失败、原子核销及回冲；5–8日 | M8奖罚、M7结算未完成 |
| A3 双口径闭环 | 部分余额/逾期预测、逐笔现金分月、手工支付来源审计、封账完整覆盖；4–7日 | 用户账期规则、M19 |
| A4 可办审批 | 真实角色、候选任务签收/领取、金额档位及异常补偿；3–6日 | M17；M7方案不可当已交付 |
| A5 次链补证 | 开票→收款→核销、报销/备用金/其他支付/调拨逐项真相与硬断言；3–5日取证后再定实现 | 业务人员、M12 |
| B1 对账工作台 | 差异来源链、处理责任/截止时间、PC/App可付解释；4–7日 | A档事实模型 |
| B2 月度计划细化 | 科目计划、滚动实际与例外审批；4–7日 | M4/M19 |
| C1 网银/凭证集成 | 仅在真实接口、财务制度和ROI明确后建设；8–15日以上 | 外部接口、凭证授权 |

更省方案：先守住A1/A2与现有真实资金链，不另建网银或会计总账。

## 7. 生产语义及待确认决策

Q1 cumulative_paid/total_expense继续为审批口径（推荐保持），UI是否明确改称“已批准付款/审批支出”？
Q2 付款可付是否预占审批中余额；建议批准时硬上限，提交时预占可配置制度须用户决定，不能仅靠快照。
Q3 手工支付需要出纳复核、银行账户必填、附件和撤销理由否？部分付款可否手工记录，推荐先保留流水唯一事实源。
Q4 封账按申请日、审批日还是现金日；跨期冲销归哪期？必须财务负责人决定。
Q5 已付账据的作废/退款/核销逆向制度与净奖罚规则。

生产未写，尚无回滚动作。未来存量修复必须双环境分开取证、备份并确认单据事实；不以种子填平差额，不伪造银行流水，不将历史审批累计迁成现金累计。迁移双轨、幂等；回滚不能删已产生的资金流水。

## 8. 验证方案

本轮未执行测试，并非受阻测试。未来tenant9999真实链：结算→付款提交→普通财务审批→UNPAID→两笔PARTIAL/PAID→取消一笔→撤销/退款；开票→回款→多应收核销→改额/反冲。断言审批累计在现金变化前后不变、核销及CBS流水平衡。

负向：PUT注入APPROVED/pay_status；负金额；跨项目合同；其他收入合同付款；相同回调并发；两单抢余额；重复核销；封账后删除/标付/撤销/批量；无候选办理人；银行勾稽后删单；部分支付跨月现金汇总。JUnit/Mockito与Controller契约、PC/App真实入口、R7只读、测试清理分别验，不把Mock通过当真实流程验收。环境受阻按现有登记与用户决策规则处理。

## 9. 门A待确认记录

| 日期 | 状态 | 待决策 | 未核实 |
|---|---|---|---|
| 2026-10-08 | 蓝图完成，门A待确认；未实施 | Q1–Q5、A档优先级、A5取证范围 | 生产实绩、App完整入口、次链约束、并发/封账覆盖、行业与标杆原文 |

门B未开启；不得据本文宣称全财务域考古完成或已上线。
