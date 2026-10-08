# 10-变更事件 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 仅制定蓝图；未实施、未运行Java/mvn、未查生产。当前代码证据不等于生产能力。M7在途改动及其审批/结算依赖仍未完成。

## 1. 现状真相表

C=`zw-insight-server/zw-contract/src/main/java/com/zwinsight/contract/`，B=`zw-insight-server/zw-budget/src/main/java/com/zwinsight/budget/`。

| 能力 | 真相 | 证据 path:line |
|---|---|---|
| 来源登记 | 项目ID、来源枚举、标题长度校验；sourceRef非空才查重；强制DRAFT/成本0，但请求实体其他受保护字段仍需白名单 | `C/service/ChangeEventService.java:145-194` |
| 来源去重 | 应用层查重排除CANCELLED；迁移已见编号唯一键，尚无本轮证据证明来源组合唯一约束 | `C/mapper/BizChangeEventMapper.java:64-72`；`zw-insight-server/zw-app/src/main/resources/db/migration/V2026_49__cost_control_backbone.sql:139` |
| 编辑 | DRAFT/ASSESSING可编辑；显式字段拷贝，不可直接改状态；sourceType变更报错，sourceRef不赋值 | `C/service/ChangeEventService.java:205-245` |
| 评估 | 成本影响、理由必填；账户金额按增减求和，精确到分；账户同项目同租户校验已实现 | `C/service/ChangeEventService.java:268-303,486-521` |
| 状态 | 有评估、批准、驳回、重评、作废；批准终态不可直接删，反向变更制度已在代码提示 | `C/service/ChangeEventService.java:315-420` |
| 乐观锁 | 注释声称并发一个成功，但各updateById返回值未检查，仍记录Outbox；插件拒写时事务是否显式失败需证实，不能因version存在认定安全 | `C/service/ChangeEventService.java:326-330,353-356,375-378,400-403,438-441` |
| 批准意见 | approve接收comment但未持久化；批准不启动BPMN。本服务注释将无BPMN直接流转作为允许设计 | `C/service/ChangeEventService.java:41,315-334` |
| CBS传导 | Outbox批准共享契约；账户CURRENT增减，eventId:accountId为来源，postBatch统一入账；不是直接写actual成本 | `C/service/ChangeEventService.java:456-482`；`B/handler/ChangeEventApprovedCostHandler.java:130-150` |
| 合同/收入传导 | 服务注释提及合同累计/预算记录，但本轮实际核到CBS消费者，不得宣称合同收入影响已完整实现 | `C/service/ChangeEventService.java:309-311`；`B/handler/ChangeEventApprovedCostHandler.java:133-149` |
| 权限 | 操作级add/edit/assess/approve/reject/cancel/delete已有，勿误报全仅view；职责分离/项目级数据授权未真实验证 | `C/controller/ChangeEventController.java:30,78,88,98,108,121,132,142,153,164` |
| PC/App | PC列表/表单/详情与评估API；App登记支持存草稿和提交评估、离线submitOrQueue。sourceRef空时离线重复重放安全需验证 | `zw-insight-web/src/api/change-event.ts:149-201`；`zw-insight-app/src/pages/contract/change-event/create.vue:91-92,307-326` |
| 测试 | Service、状态机、JSON值对象及CBS消费者测试存在，未运行且没有本轮竞态/Outbox投递结果 | `zw-insight-server/zw-contract/src/test/java/com/zwinsight/contract/service/ChangeEventServiceTest.java:1`；`zw-insight-server/zw-budget/src/test/java/com/zwinsight/budget/handler/ChangeEventApprovedCostHandlerTest.java:1` |

编号逻辑删除guard已有迁移V2026_76，不能误说编号重建始终无保护；来源事实去重是另一约束，需要单独审计。

## 2. 数据考古发现

生产未取证，事件数、状态分布、传导成功率及现场实绩均未知；种子不是实绩。双机须按IP/容器/数据库/企业四项核对，分别查询。

只读候选SQL，绑定`:tenant_id`，先核schema后运行；不返回附件URL、人员隐私。

```sql
SELECT table_name,column_name,column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('biz_change_event','biz_cost_account_txn');
SELECT index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) columns_used
FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='biz_change_event'
GROUP BY index_name,non_unique;
SELECT status,COUNT(*) n,SUM(cost_delta) cost_delta FROM biz_change_event
WHERE tenant_id=:tenant_id AND deleted=0 GROUP BY status;
SELECT project_id,source_type,source_ref,COUNT(*) n FROM biz_change_event
WHERE tenant_id=:tenant_id AND deleted=0 AND status<>'CANCELLED'
AND source_ref IS NOT NULL AND source_ref<>''
GROUP BY project_id,source_type,source_ref HAVING COUNT(*)>1;
SELECT id,status,cost_delta FROM biz_change_event
WHERE tenant_id=:tenant_id AND deleted=0 AND status='APPROVED' AND approved_at IS NULL;
```

Outbox真实表名/列、本轮未读，故不拼造SQL。后续根据实际schema补“批准事件→投递记录→每账户CURRENT流水→余额”的全链查询，并分出零影响、SEED及反向事件。SQL报错必须失败，零行不能宣称链路正常。

## 3. 成熟度基线（人工）

未运行feature-ledger：登记L2；业务状态机L3；成本评估L3；CBS传导L3；合同/收入变更暂L1/待证；PC/App登记L2/L3；整体L3，未达生产可验证L4。

八维复核：数据完整性来源引用及工期校验待加强；状态模型已有而冲销关联欠明；业务规则成本明细已严谨，收入/合同传导未证；操作权限已有、职责分离待证；并发状态更新失败与来源唯一性待补；账务CURRENT/actual/收入边界需明确；体验传导状态与失败可见不足待验；验证离线重放、Outbox重试和竞态未证明。非自动账本结果。

## 4. 缺口矩阵（标杆/规范/实绩）

| 能力 | 当前 | 对照/证据性质 | 实绩 |
|---|---|---|---|
| 事实唯一登记 | 先查后插 | MySQL官方事务锁文档说明读后写需保护；来源唯一约束还须DB实际索引证据 | 未取证 |
| 影响评估分离 | 已实现 | 源码事实，可保留，不重复造模型 | 未取证 |
| 变更收入与成本分离 | CBS成本CURRENT传导已见 | 企业商业决策；成本变化不自动成为甲方认可收入 | 未取证 |
| 签证证据、时限及责任链 | 有附件字段 | 标杆官方页面及行业规范原文未核实，无强制时限结论 | 未取证 |

已查来源：https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html （2026-10-08）。没有查得可核实行业条款或产品能力树；不猜标准条款、网址与报送天数。

## 5. 目标状态机与业务不变量

沿用DRAFT→ASSESSING→APPROVING→APPROVED/REJECTED；APPROVING可退ASSESSING，非批准态可CANCELLED；已批准反向新事件，原事件留存。新增传导结果作为独立执行状态，不将异步失败改成“未批准”。

- CI-1：sourceType/sourceRef在同租户项目内代表同一事实；并发和离线重放唯一。取消重登的guard策略须支持无限历史，禁止布尔deleted拼唯一键。
- CI-2：成本影响=各账户带符号明细和；账户同租户项目且精确到分。已有规则不得退回前端校验。
- CI-3：状态更新失败不得生成批准Outbox；CAS/乐观锁结果检查与Outbox同事务。
- CI-4：CBS CURRENT变化与actual成本、commitment、施工合同收入变更分别记账；不能把costDelta直接变成合同cumulativeChangeAmount。
- CI-5：异步批准传导允许重试且不重复入账；失败可见、可定位、可恢复；反向事件与原事件建立明确关联。
- CI-6：登记、评估、批准职责明确；批准意见、依据和人员不可缺；流程若采用BPMN不得保留可绕过流程的直接approve入口。
- CI-7：project_id依赖清理遵守事件级联，已批准财务事实及幂等流水不删。

M3施工合同含变更收入上限、M4CBS已实施边界、M11产值上限、M17办理策略需共同确认；M7审批未完成，不作为已可用能力。

## 6. 分档实现计划

| 档/项 | 范围及代价（单人日，含测试） | 依赖 |
|---|---|---|
| A1 状态并发正确性 | 所有update检查行数、非法回调失败、并发批准测试；2–4日 | Outbox事务/插件实际行为 |
| A2 来源幂等 | 输入白名单、sourceRef/离线客户端键，DB唯一guard与冲突业务提示；3–5日 | 存量重复只读探针、App队列 |
| A3 传导可观测 | 批准Outbox/CBS流水关联、失败重试入口与权限、跨租户负向；3–5日 | M4账本、Outbox运营能力 |
| A4 商业变更边界 | 明确合同收入变更与成本事件关系，若缺收入认可则不传合同；2–4日取证设计后另估实现 | M3/M11、商务人员 |
| B1 审批留痕 | 意见持久化、自审约束、重评轨迹；2–4日 | 是否接BPMN由用户选，M17 |
| B2 反向/工期 | 原事件关联、合法冲销、工期有符号规则与证据清单；3–5日 | M4/M12 |
| C1 签证索赔包 | 证据时间线、申报追踪、外部认可对照；5–10日 | 真实标杆/合同条款补证 |

更省方案：保留已有状态机与CBS消费者，只补写入结果检查、来源幂等及失败可见，不重做全套工作流。

## 7. 生产语义及待确认决策

Q1 直接批准是否允许，或统一BPMN？推荐先明确职责，再选一种权威路径，不能双入口绕过。
Q2 取消事件允许同sourceRef重登否；离线无sourceRef时使用何幂等键？
Q3 costDelta仅调整CBS当前预算还是同时生成预算变更记录；不得自动调整合同收入。
Q4 甲方认可的收入变更由签证、合同补充协议还是独立单据承载？与产值上限及含税口径如何勾稽？
Q5 反向变更是否允许部分冲销/跨期、工期负数如何解释？

未改生产，暂无回滚动作。未来索引/关联迁移先备份、只读去重清单、人工裁决，双轨幂等；不得删除批准事实与Outbox/成本流水以“去重”。回滚停新命令但保留事实，不对已消费事件盲目重放。

## 8. 验证方案

本轮仅规划。未来tenant9999：现场登记→评估→重评→批准→Outbox→CBS→反向事件；另验拒绝/取消与离线重放。断言每账户入账一次、CURRENT净变动吻合、actual与合同收入不被成本事件误增。

关键负向：相同来源并发创建；批准/作废竞争；updateById返回0而Outbox不得提交；批量账户部分失败重试；外租户账户；成本非分精度/合计不一致；缺办理角色；非审批人员批准；已批准删改；sourceRef空离线重复上传。核PC/App实际操作、审批意见与传导失败提示；结合R7及只读流水对账，不以种子表现替代实绩。Java/mvn本轮不执行，未来环境受阻走规定登记与用户决策。

## 9. 门A待确认记录

| 日期 | 状态 | 待决定 | 未核实 |
|---|---|---|---|
| 2026-10-08 | 蓝图完成，门A待确认；未实施 | Q1–Q5及A档范围 | 两生产实绩、来源实际唯一键、Outbox表/运行、合同收入消费者、项目数据权限、行业标杆原文 |

门B未开启。
