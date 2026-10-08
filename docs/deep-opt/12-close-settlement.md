# 12-竣工结算/结案 深挖蓝图（v1 · 2026-10-08 · 门A待确认）

> 本轮仅蓝图。已读 AGENTS.md、00-方法论与总控.md、07-machine.md；创建前未发现同名或同模块独立蓝图。证据为当前工作树静态代码，不等于已部署版本；M7 未提交改动仅供依赖识别，不修改、不认定已完成。未连接生产、未运行任何环境测试。下述路径均相对 `D:/tem_projects/ZW-Insight/`；行号按源文件从 1 起计。

## 1. 现状真相表

| 能力 | 判定与真相 | path:line |
|---|---|---|
| 合同竣工结算 | 错误实现：启动审批后立即 APPROVED、合同 SETTLED、项目结算额累加；空金额按零、缺合同/项目跳过回写，不能称审批后生效 | `zw-insight-server/zw-contract/src/main/java/com/zwinsight/contract/service/FinalSettlementService.java:57-97` |
| 两类结算入口 | 已实现但语义分裂：合同 `/api/v1/contract/settlement` 与财务 `/api/v1/project-settlements` 并存；后者才是项目结项查询的凭据 | `zw-insight-server/zw-contract/src/main/java/com/zwinsight/contract/controller/FinalSettlementController.java:15-38`；`zw-insight-server/zw-finance/src/main/java/com/zwinsight/finance/controller/ProjectSettlementController.java:27-98`；`zw-insight-server/zw-project/src/main/java/com/zwinsight/project/mapper/BizProjectMapper.java:46-48` |
| 项目最终结算 | 半实现：草稿/驳回可编辑，提交为 SUBMITTED；通过生成应收、同项目施工合同置 SETTLED；串行重复 APPROVED 回调跳过，尚不能证明并发幂等 | `zw-insight-server/zw-finance/src/main/java/com/zwinsight/finance/service/ProjectSettlementService.java:216-350,391-474` |
| 利润成本 | 风险实现：总支出累加四类合同结算、累计付款与净奖惩；同一成本的结算与付款可能重复计量，必须先追单据归属而非直接改历史利润 | 同上 `:118-130,323-340` |
| 关闭预检 | 已实现：COMPLETED、应收≤100元、已审批最终结算为阻断项；应付、质保金、保证金/备用金失败仅提示，不进入 failedReasons。注释“基本结清”不能替代实际阻断策略 | `zw-insight-server/zw-project/src/main/java/com/zwinsight/project/service/ProjectService.java:589-661` |
| 应付口径 | 半实现：五类合同累计结算减累计已付为审批口径，不是银行实际付款余额；不可当作现金清零证明 | `zw-insight-server/zw-project/src/main/java/com/zwinsight/project/mapper/BizProjectMapper.java:119-125`；`AGENTS.md:115-123` |
| 关闭审批与日志 | 已实现 CLOSING→CLOSED / COMPLETED，走状态机留大事记；通过回调未再次调用财务预检，审批期间余额变化需补门禁 | `zw-insight-server/zw-project/src/main/java/com/zwinsight/project/service/ProjectService.java:529-578,664-676` |
| 竣工验收交接 | 错误分支：验收先 APPROVED，项目 complete 被 BusinessException 拒绝后仅日志告警，可能出现验收通过但项目未竣工 | `zw-insight-server/zw-site/src/main/java/com/zwinsight/site/service/CompletionAcceptanceService.java:84-104` |
| PC/App | PC 有最终结算 API/列表/详情及关闭预检 API；App 当前 common.ts 可查项目档案，检索未见独立最终结算/关闭接口，不能据 PC 推断移动闭环 | `zw-insight-web/src/api/settlement.ts:1`；`zw-insight-web/src/views/finance/settlement/detail.vue:1`；`zw-insight-web/src/api/project.ts:98-103`；`zw-insight-app/src/api/common.ts:320` |
| 既有测试 | 合同测试反而固化“提交即回写”和“缺引用跳过”；有测试不代表正确业务断言，更不代表本轮通过 | `zw-insight-server/zw-contract/src/test/java/com/zwinsight/contract/service/FinalSettlementServiceTest.java:101-131`；`zw-insight-web/src/__tests__/settlement-docs.component.test.ts:1` |

## 2. 数据考古发现

**生产未核实**：两台数据库独立；结算量、待审流程、成本重叠金额、关闭项目残余应收/现金应付均未知。90001–90004 及负利润/可结项种子只用于夹具，不是实绩。M7 蓝图的历史探针不可转录为本模块当前生产结果。

待执行只读 SQL（授权后逐主机核对 IP、容器、DATABASE()、企业；`:tenant` 为绑定参数，不直接粘贴执行）：

```sql
SELECT DATABASE(), @@hostname;
SELECT table_name,column_name FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN
('biz_final_settlement','biz_project_settlement','biz_project','biz_payment_apply','biz_receivable');
SELECT status,COUNT(*) n,SUM(settlement_amount) amount
FROM biz_final_settlement WHERE tenant_id=:tenant AND deleted=0 GROUP BY status;
SELECT status,COUNT(*) n,SUM(total_expenditure) expenditure,SUM(profit) profit
FROM biz_project_settlement WHERE tenant_id=:tenant AND deleted=0 GROUP BY status;
SELECT p.id,p.status,p.receivable_amount,COUNT(s.id) approved_settlements
FROM biz_project p LEFT JOIN biz_project_settlement s
ON s.project_id=p.id AND s.tenant_id=p.tenant_id AND s.deleted=0 AND s.status='APPROVED'
WHERE p.tenant_id=:tenant AND p.deleted=0 AND p.status IN ('CLOSING','CLOSED')
GROUP BY p.id,p.status,p.receivable_amount;
SELECT project_id,pay_status,COUNT(*) n,SUM(payment_amount) amount
FROM biz_payment_apply WHERE tenant_id=:tenant AND deleted=0 AND status='APPROVED'
GROUP BY project_id,pay_status;
```

先核列，金额列不符即停止修订 SQL，不把 SQL 错误当零异常。补探针：两种结算 workflow_instance_id 与引擎运行/历史实例逐单对照；四类结算与付款按合同映射重叠；种子、tenant 9999、测试标记分层统计。应收账与核销须按 OPEN 余额对账；只有上述聚合还不足以认定错账。

## 3. 成熟度基线

人工静态定位，未运行 feature-ledger：合同结算 L1/L2；最终结算 L2/L3；关闭 L3；整体不认 L4。八维：efficiency 有导出但无整体结案包；query 双入口待统一；state 提交提前生效/通过未复检；audit 项目有日志、结算证据版本不足；notify 审批可达性待证；permission 财务 Controller 类级 finance:view，写权限待细分；error 缺引用跳过与验收吞错；value 利润口径可能重复。运行覆盖率未知，不填百分比。

## 4. 缺口矩阵（标杆/规范/实绩）

| 目标能力 | 当前缺口 | 标杆/规范依据 | 实绩 |
|---|---|---|---|
| 审批后冻结结算及证据 | 合同结算提前生效 | 项目内审批约定；外部产品能力未核实 | 未核实 |
| 成本、审批支出、现金分列 | 结算与付款混加风险 | AGENTS.md 双口径约束，非外部法规 | 未核实 |
| 结案/质保期后关闭分层 | 提示项不阻断，100元容差固定 | 《建设工程价款结算暂行办法》名称仅候选，现行效力、条款及官方 URL 未核实；不据此给时限 | 未核实 |
| 竣工档案交付清单 | 无结案包证明 | 《建设工程文件归档规范》名称候选，版本与条款未核实；M14 补证 | 未核实 |

不宣称任何产品已支持具体功能，不引用未经核验条款、比例或 URL。

## 5. 目标状态机与业务不变量

- 合同结算：DRAFT→SUBMITTED→APPROVED；驳回回草稿（或 REJECTED 待用户选）；仅通过回调改变合同/项目金额。财务最终结算保持既有 DRAFT/REJECTED→SUBMITTED→APPROVED；“对业主合同结算”与“项目利润结算”不得互为同义。
- 项目：CONSTRUCTION→COMPLETED→CLOSING→CLOSED；验收、结案、质保义务完成三件事分别表达，不擅增业务状态。
- CI-1：合同、项目、结算同租户且合同归属该项目；引用缺失拒绝，金额非空非负，变更/负调整另建授权路径。
- CI-2：审批回调以 SUBMITTED 条件更新、行数及流水幂等守卫；审批状态、合同累计、应收和事件同事务，失败整体回滚。
- CI-3：利润成本只计算一次；已结算成本、审批支出、已付现金各列，禁止重复扣成本，禁止 pay_status 回写累计支出。
- CI-4：提交与通过两时点复检；CLOSING 期间新增单据须阻断或使预检失效，用户选定一致策略。
- CI-5：日志记录操作者、依据版本、阻断/豁免原因；CLOSED 不允许无审计直接回退。
- CI-6：`zw-project` 不反向注入 contract/finance/site/archive。跨域写入采用 zw-common 事件契约、模块前缀 Listener，同事务不得吞异常；跨表只读预检可保留 Mapper 惯例并显式租户约束。
- CI-7：关闭不等于删除；既有 ProjectDeletedEvent 级联不得用来实现结案。历史账本和文件保留期由业务确认；新增 project_id 表登记模块 Listener，子表亦按真实关系核查。
- M7 依赖：机械新旧结算来源以 M7 最终门B口径为准，当前工作树变化不作为可用能力；审批候选组可达性由 M17 协同，不代改。

## 6. A/B/C 分档计划、推荐与代价

代价为相对人日估计，含单测/PC接线，不含等候业务确认、生产核账；非承诺。

| 档 | 内容与推荐 | 代价/涉及面 |
|---|---|---|
| A 必须闭环（推荐） | 修合同结算审批后生效、引用校验、并发幂等；核清利润重叠；关闭回调复检及阻断策略；验收失败回滚；最小权限和日志 | 8–13人日；contract/finance/project/site/common/workflow、PC；可能需要双轨迁移，未授权 |
| B 细节增强 | 双结算导航、来源明细下钻、预检缺口工单、档案清单和受控重新打开；移动只读预检 | 4–7人日；PC/App/archive；A 后独立确认 |
| C 锦上添花 | 电子签章、外部结算协同、档案交付包和经营复盘 | 8–15人日及外部成本，延后 |

更省方案：保留双表，不做通用结案引擎，先修 A 的状态及口径；不把合并表作为前提。

## 7. 生产语义与用户决策

Q1 推荐区分合同结算与项目最终结算，旧单只读保留，不自动合并。Q2 推荐“经营结案允许有已登记质保义务、财务最终关闭现金债务清零”分层；若仍只用 CLOSED，必须明确哪些余额阻断、100元容差是否保留、豁免权限及审计。Q3 利润改口径后历史审批单保留原快照并标版本，是否重述历史必须业务签认，不自动重算。Q4 推荐验收拒绝导致全事务失败；不允许以日志伪装成功。Q5 现金清零是否纳入本轮、CLOSING 写入限制和重开审批由用户定。

任何未来存量修复先双机分别只读核账、备份目标表、逐单映射、事务回滚演练；禁止删引擎表。回滚区分代码回退与不可逆已审批账务，后者采用可追溯更正，不能直接覆写快照。本轮不执行修复。

## 8. 验证方案（仅计划，未执行）

1. CI/受权环境 L1：提交不回写、驳回无副作用、缺引用/跨租户拒绝、重复及并发回调仅一次、更新行数0回滚；替换当前固化提前回写的断言。
2. tenant 9999 L3/L4：验收→两类结算→应收核销→已批未付→关闭阻断/豁免→关闭通过；每个失败检查金额、日志和状态不变；审批期间新增欠款负向测试。
3. R7 增量：结算额对应有效单据、应收 OPEN 汇总、成本不重复、CLOSED 欠款及义务清单；植入隔离异常验证 FAIL 可触发。
4. PC 抽验 API 路径、预检 blocking 与文案一致；App 不虚构提交入口。归档/机械消费方同源回归；财务候选任务由普通财务办理，不靠 SUPER_ADMIN。
5. 本轮未启动测试，属授权范围排除，不是环境测试失败；未改受阻台账。通过数、覆盖率与线上结论均待后续授权。

## 9. 门A待确认

- 2026-10-08：蓝图已编制；**门A未确认，门B未启动**，不授权实现、迁移、生产写入或部署。
- 待确认 A 范围、人日、Q1–Q5、M7/M17 依赖顺序、外部规范补证与双机只读探针。
- 证据不足：当前生产分布、成本重复的真实单据金额、流程部署/办理权限、现金状态可靠性、法规现行版本均未核实。用户确认后另行 Requirements→Design→Tasks；本轮不修改 Spec/总控/GOAL。
