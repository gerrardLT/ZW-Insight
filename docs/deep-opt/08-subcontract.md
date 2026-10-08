# 08-分包 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 本轮仅制定蓝图，未实施、未运行测试、未访问生产数据库。证据来自当前工作树；M7 有未提交改动，其接口、迁移与归集口径不得视为已上线。以下路径均相对仓库根目录，行号以本轮取证为准。

## 1. 现状真相表

为便于定位，S=`zw-insight-server/zw-subcontract/src/main/java/com/zwinsight/subcontract/`。

| 能力 | 真相与风险 | 证据 path:line |
|---|---|---|
| 合同创建 | 已有正金额、预算约束；非完整审批闭环 | `S/service/SubcontractService.java:51-83` |
| 合同提交 | DRAFT 直接 EFFECTIVE，无审批启动；PC 却提示进入审批、已提交审批，业务语义错误 | `S/service/SubcontractService.java:89-94`；`zw-insight-web/src/views/subcontract/contract.vue:96` |
| 合同编辑 | 已保护 status 与三类累计字段，不能重复列为未修复问题；金额/项目等变更仍须按创建规则复验 | `S/service/SubcontractService.java:104-113` |
| 分包产值 | 提交直接 APPROVED；合同缺失或 contractId 空仍可保留 APPROVED；累计用读取后覆盖而非原子加 | `S/service/SubcontractOutputService.java:65-84` |
| 结算明细 | DTO 路径检查合同项目一致；服务端 quantity×unitPrice 按分舍入求和；不是前端传总额直接采信 | `S/service/SubcontractSettlementService.java:68-86,99-142,382-407` |
| 结算提交 | DRAFT 直接 APPROVED，不启动流程；读累计验上限后整体写合同，存在并发丢增量及双单越限风险 | `S/service/SubcontractSettlementService.java:211-237` |
| 删除回冲 | 正常仅草稿可删；E2E 标记旁路允许删除 APPROVED 并 addSettlement 负向回冲，不可误说“所有已审批可删” | `S/service/SubcontractSettlementService.java:186-207` |
| 奖罚 | 创建校验类型及正金额；删除直接删，需核验已进入付款可付余额的奖罚能否撤销 | `S/service/SubcontractRewardPunishService.java:30-44`；`zw-insight-server/zw-finance/src/main/java/com/zwinsight/finance/service/PaymentApplyService.java:450-462` |
| 权限 | 四 Controller 类级均 subcontract:view，读写同码；项目级授权效果未做真实角色验证 | `S/controller/SubcontractController.java:20`；`SubcontractSettlementController.java:28`；`SubcontractOutputController.java:20`；`SubcontractRewardPunishController.java:20`（后三项同 S/controller/） |
| PC/App | PC 有合同、结算页面；API 有产值 CRUD，无 submit 函数。本轮在 App 按模块文件名检索未发现分包专页，不能据此断言全 App 无入口 | `zw-insight-web/src/api/subcontract.ts:30-43,48-69`；`zw-insight-web/src/views/subcontract/settlement.vue:177-197` |
| CBS/财务 | 结算进入成本归集；付款审批才增加项目 total_expense，不是结算支出，更不是现金支出 | `zw-insight-server/zw-budget/src/main/java/com/zwinsight/budget/mapper/CostRollUpMapper.java:96`；`zw-insight-server/zw-finance/src/main/java/com/zwinsight/finance/service/PaymentApplyService.java:288-293,316-320` |
| 测试 | 有 Service、Property、Mutation、级联测试与 PC 组件测试；文件存在不等于本轮执行通过或覆盖竞态 | `zw-insight-server/zw-subcontract/src/test/java/com/zwinsight/subcontract/service/SubcontractSettlementServiceTest.java:1`；`zw-insight-web/src/__tests__/subcontract-matrix.component.test.ts:109-183` |

结算 Service 的 `:240` 仍写“实际现金流出”，与真实付款审批回写及 AGENTS 纠正冲突；未来实施需修说明，不能据旧注释改变字段口径。

## 2. 数据考古发现

生产未取证。无状态分布、金额、字段空置率、实绩使用率结论；种子与历史报告不得代替当前业务实绩。主环境与徽颍环境必须分别核对 IP、容器、库名及企业身份；不得以 tenant_id=1 认定同库。

以下仅为待执行只读 SQL，`:tenant_id` 由审核人员绑定，不含写操作；先检查 information_schema 的实际列和索引，SQL 报错必须记失败，不能解释为零异常。

```sql
SELECT table_name,column_name,column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN
('biz_subcontract','biz_subcontract_output_report','biz_subcontract_settlement','biz_subcontract_settlement_detail','biz_subcontract_reward_punish');
SELECT status,COUNT(*) AS n,SUM(settlement_amount) AS amount
FROM biz_subcontract_settlement WHERE tenant_id=:tenant_id AND deleted=0 GROUP BY status;
SELECT c.id,c.cumulative_settlement,COALESCE(s.amount,0) AS document_amount
FROM biz_subcontract c LEFT JOIN
(SELECT contract_id,tenant_id,SUM(settlement_amount) amount FROM biz_subcontract_settlement
 WHERE deleted=0 AND status='APPROVED' GROUP BY contract_id,tenant_id) s
ON s.contract_id=c.id AND s.tenant_id=c.tenant_id
WHERE c.tenant_id=:tenant_id AND c.deleted=0
AND ABS(COALESCE(c.cumulative_settlement,0)-COALESCE(s.amount,0))>0.01;
SELECT s.id FROM biz_subcontract_settlement s LEFT JOIN biz_subcontract c
ON c.id=s.contract_id AND c.tenant_id=s.tenant_id AND c.deleted=0
WHERE s.tenant_id=:tenant_id AND s.deleted=0 AND (c.id IS NULL OR c.project_id<>s.project_id);
SELECT s.id,s.settlement_amount,COALESCE(SUM(d.amount),0) AS detail_amount
FROM biz_subcontract_settlement s LEFT JOIN biz_subcontract_settlement_detail d
ON d.settlement_id=s.id AND d.tenant_id=s.tenant_id AND d.deleted=0
WHERE s.tenant_id=:tenant_id AND s.deleted=0 GROUP BY s.id,s.settlement_amount
HAVING ABS(s.settlement_amount-COALESCE(SUM(d.amount),0))>0.01;
```

待补：产值累计勾稽、奖罚被付款引用、已删除历史、CBS 账户/流水/归集来源、全量雪花 ID 与固定种子 ID 分层统计。不得直接清理。

## 3. 成熟度基线（人工评分）

未运行 feature-ledger。人工按 L0 无入口、L1 CRUD、L2 规则、L3 流程、L4 可验证闭环评分：合同 L2；产值 L1/L2；结算 L2；奖罚 L1；PC L2；App 待入口取证，暂不评分；整体 L2，不能因 APPROVED 字样评 L3。

八维人工复核：数据完整性缺关联复验；状态机缺审批；业务规则缺产值上限及结算占用；权限缺操作分权；并发幂等缺提交竞争保护；财务勾稽需保持三口径；体验缺真实审批反馈；验证有存量测试但未证明并发闭环。此处为人工分析轴，非工具自动结果。

## 4. 缺口矩阵（标杆/规范/实绩）

| 能力 | 当前 | 目标依据 | 实绩 |
|---|---|---|---|
| 结算并发上限与累计 | 读后覆盖 | 已查 MySQL 官方 locking reads 文档：事务中 FOR UPDATE 保护读后更新；也可采用条件 UPDATE 并检查行数 | 未取证 |
| 审批职责分离 | 提交即批准 | 企业内控决策，尚未确认，不能冒称强制法规 | 未取证 |
| 分包产值支撑结算 | 两条独立链 | 产品目标候选：按已确认产值计量，避免重复结算 | 未取证 |
| 资质、履约、扣款、质保 | 本轮未完整取证 | 标杆产品及具体行业条款未核实；列候选能力，不作为强制实施依据 | 未取证 |

唯一已核实外部来源：https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html （2026-10-08 读取）。尚未查得可靠产品官方功能树、分包法规条款与时限，均待补证。

## 5. 目标状态机与业务不变量

建议合同 DRAFT→SUBMITTED→EFFECTIVE，驳回回可编辑态；产值与结算 DRAFT→SUBMITTED→APPROVED/REJECTED；奖罚先明确确认/撤销制度。此为待确认设计，不代表已有 BPMN。

- SI-1：合同、产值、结算、奖罚必须同租户同项目；关联合同存在且允许执行，不可缺合同仍批准。
- SI-2：主表结算金额等于服务端明细金额之和；数量、单价、金额精度在后端验证。
- SI-3：合同累计结算等于有效 APPROVED 结算汇总；上限校验与入账同事务串行化；重复提交/回调只能产生一次效果。
- SI-4：产值累计只由确认事件产生；进入结算的计量依据锁定；重提/撤回释放占用必须对称。
- SI-5：结算形成 CBS actual；付款审批形成 total_expense/cumulative_paid；现金勾稽形成 pay_status，三者不得混加。
- SI-6：已批准账据原则上不删；使用冲销单留痕。测试旁路不得绕过租户和账本守卫。
- SI-7：新增 project_id 表进入本模块级联监听器；失败传播，账本不删除。M7 候选组/机械新旧口径未完成依赖由 M9/M17 协同，不在本模块宣称解决。

## 6. 分档实现计划

估算为单人有效工作日，含代码与测试、不含用户审批和数据补证；非承诺。

| 档/项 | 范围与代价 | 依赖 |
|---|---|---|
| A1 输入/归属闭环 | 后端创建更新复验、受保护字段白名单、细粒度写权限、PC 真实提示；2–4日 | M1 项目权限、M18 权限目录 |
| A2 并发累计 | 合同锁/条件更新、状态 CAS、更新行数硬检查、双单上限测试；3–5日 | M9 可付余额口径 |
| A3 审批与产值依据 | 合同/产值/结算审批，计量锁定及驳回重提；5–8日 | 用户确认职责；M17 可办理候选任务，M7 方案未完成 |
| A4 对账与冲销 | 合同累计/CBS/付款勾稽；奖罚变更不得让已批款越限；3–5日 | M4 账本、M9 付款 |
| B1 体验与追溯 | 产值入口、明细来源、导出审批轨迹、App 最小计量入口；3–5日 | M11 计量共享规则 |
| B2 履约/质保 | 扣款、质保释放与异常提醒；4–7日 | M9、M12；税含口径待确认 |
| C1 资质画像/供应商评价 | 仅真实资质和履约数据；5–10日 | 实绩、官方标杆补证；不强制先建 |

更省方案：先实施 A1/A2 与真实状态文案，暂不新增资质画像；正式审批与冲销制度仍须门A选择，不能自行降级。

## 7. 生产语义及待确认决策

Q1 提交立即生效是否保留为“确认”，还是加入审批？推荐正式审批，须指定办理角色与自审限制。
Q2 结算是否必须来源于已确认分包产值，支持纯金额结算否？存量无来源单据不得伪造关联。
Q3 合同额含税否、奖罚是否含税、保留款是否减少可付？先统一后迁移。
Q4 已批准的纠错采用反向单还是受控撤销？推荐反向单；现金已付不得删账逃避。

任何回填先只读清单、备份、人工映射，再幂等双轨迁移；严禁按累计差额生成伪造结算。CBS 的 SEED/ROLLUP 与真实单据来源分别保留，不能将演示种子当实绩。未作生产变更，当前无回滚动作；未来回滚不得撤掉已产生财务事实。

## 8. 验证方案

本轮仅规划，不运行 Java/mvn、生产写或真实流程。未来租户9999：合同→确认产值→结算审批→付款审批→部分/足额勾稽→冲销/驳回分支；硬断言累计、明细、CBS流水与付款口径。

必测：负/零金额、跨租户跨项目、已失效合同；重复提交；两结算争夺同一余额；编辑结算占用产值；普通只读用户写入拒绝；无办理人流程明确失败；奖罚删除导致可付倒挂；级联事务失败整体回滚。Service 正常/异常路径、PC/App 契约、真实API结构、R7勾稽与SQL负向故障检测分别验。清理仅 tenant9999 业务数据，不删系统表与账本；测试环境阻塞按现有受阻登记规范由用户决策。

## 9. 门A待确认记录

| 日期 | 状态 | 用户决策 | 未核实项 |
|---|---|---|---|
| 2026-10-08 | 蓝图完成，门A待确认；未实施 | Q1–Q4、A档范围及依赖均待确认 | 两环境生产实绩、App完整入口、覆盖率/并发测试、官方标杆与行业条款 |

门B未开启；本蓝图不替代 M7/M9/M17 尚未完成事项。
