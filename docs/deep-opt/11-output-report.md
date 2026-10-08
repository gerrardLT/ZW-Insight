# 11-产值上报 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 本模块为施工合同收入侧产值；劳务/分包执行产值分别属于M6/M8，不混成项目收入。本轮仅蓝图，未实施、未运行测试、未访问生产。M7在途改动不是已完成依赖。

## 1. 现状真相表

C=`zw-insight-server/zw-contract/src/main/java/com/zwinsight/contract/`。

| 能力 | 真实现状 | 证据 path:line |
|---|---|---|
| 创建草稿 | 强制DRAFT，插入请求实体并存明细；未见服务端正金额、合同项目匹配、主明细合计与BOQ归属验证 | `C/service/OutputReportService.java:68-72,229-237` |
| 明细计价 | 注释称quantity×单价，但saveDetails仅清ID、设reportId、insert；并未实际重新计算，不能把注释当已实现 | `C/service/OutputReportService.java:227-237` |
| 提交 | DRAFT/REJECTED可提，查合同并验累计≤合同额+累计变更额，启动output_report_approval，SUBMITTED时不入账 | `C/service/OutputReportService.java:78-102` |
| 生效 | 批准回调重验上限，合同/项目原子加产值，BOQ加完成量；但仅APPROVED重复短路，不限制原状态SUBMITTED | `C/service/OutputReportService.java:110-153` |
| 并发 | 单次SQL累加可避免丢增量，但读上限与加金额分离；双单批准越限、双同单回调重复入账未证明安全 | `C/service/OutputReportService.java:121-153` |
| 异常生效 | 超上限改REJECTED并通知；报告/合同缺失仅记录返回，工作流成功与业务未生效可分裂 | `C/service/OutputReportService.java:112-136` |
| 删除 | 普通DRAFT/REJECTED可删；E2E标记旁路可越状态删除，未见合同/项目/BOQ对称反冲，风险需结合E2eTestGuard实际限制复验 | `C/service/OutputReportService.java:186-198` |
| API能力 | Controller有列表、创建、提交、删除；未见详情/明细读取/编辑端点，Service listDetails不等于对外详情能力 | `C/controller/OutputReportController.java:22-47`；`C/service/OutputReportService.java:204-207` |
| 权限 | 写操作已有add/submit/delete，类级contract:view；项目级数据权限与真实角色未现场验证 | `C/controller/OutputReportController.java:17,32,39,46` |
| PC录入 | 有BOQ选择及前端计算合计/正金额校验；不能替代后端安全边界 | `zw-insight-web/src/views/contract/output-report.vue:257,312,319-342` |
| App | 本轮按模块文件名未核到施工产值专页，common API与路由需补完整检索；不能据此断言绝无移动能力 | 本轮仅PC契约已核：`zw-insight-web/src/api/contract.ts:138-151` |
| 测试 | Service、ApprovalListener、BatchHandler、Controller测试存在；未运行，竞态/BOQ归属/主明细勾稽覆盖未证 | `zw-insight-server/zw-contract/src/test/java/com/zwinsight/contract/service/OutputReportServiceTest.java:1`；`zw-insight-server/zw-app/src/test/java/com/zwinsight/contract/controller/OutputReportControllerTest.java:1` |

产值不等于开票、不等于回款、不等于已确认营业收入。上限里的cumulativeChangeAmount必须来自合法收入变更，M10成本事件不能直接替代。

## 2. 数据考古发现

生产未取证；无真实单数、月度分布、BOQ超量/空置、在途流程或使用率数字。演示种子与tenant9999测试不能作为业务实绩。双机先核IP、容器、库名、企业身份。

待执行只读SQL（`:tenant_id`人工绑定；先核实际列再扩展）：

```sql
SELECT table_name,column_name,column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN
('biz_output_report','biz_output_report_detail','biz_construction_contract');
SELECT status,COUNT(*) n,SUM(current_output) amount FROM biz_output_report
WHERE tenant_id=:tenant_id AND deleted=0 GROUP BY status;
SELECT id,project_id,contract_id,current_output,status FROM biz_output_report
WHERE tenant_id=:tenant_id AND deleted=0 AND (current_output IS NULL OR current_output<=0);
SELECT c.id,c.cumulative_output,COALESCE(r.amount,0) document_amount
FROM biz_construction_contract c LEFT JOIN
(SELECT contract_id,tenant_id,SUM(current_output) amount FROM biz_output_report
 WHERE deleted=0 AND status='APPROVED' GROUP BY contract_id,tenant_id) r
ON r.contract_id=c.id AND r.tenant_id=c.tenant_id
WHERE c.tenant_id=:tenant_id AND c.deleted=0
AND ABS(COALESCE(c.cumulative_output,0)-COALESCE(r.amount,0))>0.01;
SELECT r.id FROM biz_output_report r LEFT JOIN biz_construction_contract c
ON c.id=r.contract_id AND c.tenant_id=r.tenant_id AND c.deleted=0
WHERE r.tenant_id=:tenant_id AND r.deleted=0
AND (c.id IS NULL OR c.project_id<>r.project_id);
```

本轮未读取BOQ/明细schema全部列，故不猜金额列名拼SQL；后续补主表=明细合计、BOQ完成量=批准明细工程量、合同与项目累计、删除历史与在途流程勾稽。先查旧批次是否存在无BOQ金额单，再定迁移，禁止伪造明细。SQL错误不能当“0异常”。

## 3. 成熟度基线（人工）

未运行feature-ledger：上报录入L2；审批L3；累计入账L2/L3；BOQ计量L2；PC L2；App待证；整体L2/L3，不达L4。

八维缺口：数据归属及明细重算不足；状态已有流程但回调/异常一致性不足；规则缺正金额/BOQ上限/主明细合计；权限操作分权已有、项目授权待证；并发上限和回调幂等不足；财务需分开产值/开票/回款；体验缺详情纠错闭环；验证文件存在但真实并发与流程结果未证。为人工轴，不冒称自动评分。

## 4. 缺口矩阵（标杆/规范/实绩）

| 能力 | 当前 | 对照依据 | 实绩 |
|---|---|---|---|
| BOQ按量计价 | 前端计算，后端直存 | 应用信任边界及本项目真实接口约定 | 未取证 |
| 批准上限原子保护 | 读后加 | 已查MySQL官方locking reads：锁读需在事务中，锁至提交/回滚释放 | 未取证 |
| 本期/累计/甲方确认分离 | 部分字段/流程 | 业务目标候选，甲方计量认可制度须用户确认 | 未取证 |
| 计量附件/截止时间/签认 | 未完整取证 | 产品官方树与工程计量规范条款未核实，不规定未经证实天数 | 未取证 |

已查来源：https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html （2026-10-08）。行业规范、产品功能与合同约定未核实，不编条款号和URL。

## 5. 目标状态机与业务不变量

保留DRAFT→SUBMITTED→APPROVED/REJECTED，REJECTED可编辑重提；撤回、冲销的操作与数据效果由制度确认。已批准单据禁止直接修改；纠错有原单关联与审批，不抹除BOQ历史。

- OI-1：施工合同属于当前项目租户且有效；currentOutput正值精确到分；系统字段由服务端赋值。
- OI-2：有BOQ明细时金额由服务端数量×快照单价按分舍入求和；BOQ必须属于该合同，禁止重复行/负数量/超可计量量。
- OI-3：批准累计≤合同额+合法收入变更额，额度检查与累计写入原子完成；同单回调仅生效一次，不同单并发不得越限。
- OI-4：合同累计与项目累计=有效批准产值汇总；BOQ完成量=批准明细数量汇总，冲销三者同时对称。
- OI-5：SUBMITTED所用BOQ版本/价格/本期量不可漂移；驳回释放占用，不产生金额与工程量。
- OI-6：业务生效失败必须可见，不能工作流已成功而业务静默未记账；通知失败可追查。
- OI-7：成本事件不增收入产值；产值不直接增total_income/receivable/银行回款。收款与应收遵循M9自己的事实链。

依赖M3合法收入变更和BOQ、M10成本/收入边界、M9开票回款、M12最终结算与M17流程。M7审批候选办理链尚未完成，不宣称已有公共审批修复。

## 6. 分档实现计划

| 档/项 | 范围/代价（单人日，含测试） | 依赖 |
|---|---|---|
| A1 后端计量边界 | DTO白名单、同租户项目合同BOQ、正金额、主明细重算、价格快照；3–5日 | M3 BOQ及存量只读补证 |
| A2 并发入账 | 报告状态CAS、合同额度条件更新或锁、BOQ数量原子守卫、行数硬检查；4–6日 | M3合同上限、M17回调 |
| A3 删除纠错 | 普通已批不可删、测试旁路限定及对称清理、正式冲销制度；3–5日 | M9/M12与用户规则 |
| A4 可追溯异常 | 详情/明细/驳回编辑重提入口、流程业务状态对照、真实办理人；3–5日 | M17；M7在途公共方案 |
| B1 计量体验 | 月份/周期防重、附件、签认、App最小填报和离线幂等；4–7日 | 用户周期规则、移动队列 |
| B2 对账看板 | 本期累计、甲方认可/开票/回款差异来源；3–5日 | M9/M19 |
| C1 智能测量/进度联动 | BIM/现场工程量辅助；8–15日以上 | 真实集成接口与ROI，标杆补证 |

更省方案：先保证服务器重算与审批幂等，沿用PC现有BOQ表单，不引入BIM或新计量引擎。

## 7. 生产语义及待确认决策

Q1 金额模式无BOQ是否继续允许？推荐保留明确的旧单类型而非伪造BOQ；新单适用范围由商务决定。
Q2 currentOutput含税否，BOQ单价含税否；批准是企业内审还是甲方认可？两者不得含糊。
Q3 同合同同月允许多张否，是否按周期预占数量；推荐以明细可计量量守卫而非简单一月一单。
Q4 工程量超清单是否必须先批准收入变更，反向/负量纠错与跨期如何办？
Q5 已批历史差额如何处理、E2E旁路是否只许9999；是否增加正式冲销单？

生产未改，无当前回滚动作。后续先取真实批准明细/累计/流程清单并备份；旧单缺明细如实标记，不用种子补成实绩。迁移双轨幂等；回滚停新入口，不删除已批准工程量事实。

## 8. 验证方案

本轮仅规划。未来tenant9999：合同BOQ→草稿明细→提交→审批→合同/项目/BOQ累计→拒绝超量→驳回编辑重提→正式冲销；开票回款单独验不重复计产值。

必测：直接API负/零金额、主明细不符、伪造单价、其他合同BOQ、重复BOQ行、跨租户项目；同单双回调与两单抢额度；流程批准时合同缺失/失效；已批单删除及E2E清理回冲；无候选人；审批中价格/量变更；PC详情/重提真实接口与App入口。断言总金额、完成数量和累计三方，故意注入SQL错误确认审计FAIL。Service正常/异常及Controller契约、真实流程和R7分开验；未执行不宣称通过。受阻遵循现有登记与用户决策规则。

## 9. 门A待确认记录

| 日期 | 状态 | 待决定 | 未核实 |
|---|---|---|---|
| 2026-10-08 | 蓝图完成，门A待确认；未实施 | Q1–Q5、A档范围 | 生产分布/BOQ明细schema、App完整入口、真实角色/流程、并发测试、行业与标杆原文 |

门B未开启。
