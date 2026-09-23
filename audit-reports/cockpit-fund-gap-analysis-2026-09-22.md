# 工程老板经营驾驶舱 V1 × 资金流转闭环 —— 现状差距分析

- 分析日期：2026-09-22
- 分支：`feature/boss-cockpit-fund-flow`
- 对标文档：`docs/工程老板经营驾驶舱_UI原型结构_V1.md`、`docs/资金流转流程.md`
- 取证方式：全部结论基于当前代码实读（Controller/Service/VO/迁移脚本/前端 views），非文档推断。关键证据附文件路径。

---

## 0. 结论摘要

| 维度 | 评估 |
|---|---|
| 数据底座（预算/合同/付款/回款/成本归集） | **较完整**，成本控制主线（目标成本→预算→已承诺→实际→完工预测→偏差）已落地 |
| 资金计划与预测 | **基本落地**（年度预算/月度计划/滚动预测 1-12 月含风险分级） |
| 付款硬管控 | **已落地**（超结算付款拦截、预算 BLOCK/WARN） |
| 驾驶舱 V1 五页面 | **零页面直接对应**。现有 dashboard 是"数据看板"，不是文档定义的"经营控制系统" |
| 预计利润 / 利润归因 / 风险中心 / 应收账龄 / 招待费专控 | **完全缺失**，是最大缺口 |

一句话：**底层账和数据链路大体有了，但"老板决策层"（预计利润口径、异常自动冒泡、风险待办中心、穿透链）几乎从零开始。**

---

## 1. 现状能力清单（证据）

### 1.1 公司级看板 `DashboardController`（zw-dashboard）

13 个端点（`/api/v1/dashboard/*`）：

| 端点 | 能力 | 与文档对标备注 |
|---|---|---|
| `company-overview` | 项目数/状态分布/合同总额/累计结算/收入/支出/垫资/利润 | ⚠️ `profit = totalIncome - totalExpense`，是**历史已实现利润**，不是文档要求的**预计利润**（收入 − 预计最终总成本） |
| `budget-execution` | 单项目预算执行（科目/累计付款/余额/占比） | 对应文档 §10.1 预算执行率的数据源之一 |
| `receivable-monitor` | 总应收/已收/未收/回款率/项目排行 | ⚠️ 应收 = `contractAmount − totalIncome` 简化口径；**无逾期账龄、无应收日期、无工程节点关联**（文档 §10 回款风险下钻全缺） |
| `supplier-payable` | 采购合同维度：合同额/计量/已付/未付/收票 | 对应"应付未付"数据源（仅采购合同口径） |
| `profit-trend` | 按月收入/支出/利润曲线 | 🔴 **收入侧是模拟的**：Service 注释自认"收入按月均摊（简化）"（`DashboardService.getProfitTrend`），违反本项目"真实数据"原则，且与文档 §5.1"预计利润趋势+点击归因"不是一回事 |
| `project-ranking` | 按产值/利润率/回款率 TopN | 部分对应"项目健康度排名"，但**无风险等级排序、无健康度模型** |
| `budget-variance` | 计划 vs 实际按科目对比 | 对应成本中心偏差数据源 |
| `invoice-ledger` | 进销项发票汇总 | 穿透链的"发票"环节数据存在 |
| `tender-analysis` / `inventory-analysis` / `schedule-gantt` / `project/{id}` / `hr-statistics` | 投标/库存/进度/项目聚合/人事 | 与驾驶舱 V1 五页面关系不大 |

### 1.2 项目级看板 `ProjectDashboardController`

- `project/{id}/budget|progress|contract|output|overview`
- `project/{id}/cost-control`：**Cost 360**（Baseline 目标成本 / Current 当前预算 / Commitment 已承诺 / Actual 实际 / **Forecast 完工预测** / Variance 偏差），支持按 WBS 和费用类别下钻。
- 前端 `views/dashboard/project-cost-control.vue` 已有两处下钻跳转（→ 变更事件、→ 成本账户），是现有**唯一的数字穿透雏形**。

### 1.3 成本控制 backbone（zw-budget）

- `BizCostAccount`：目标成本(baselineAmount，首次批准不漂移) → 当前预算 → 已承诺 → 实际 → 完工预测 → 偏差，含预算变更额。**这正是资金流转文档 §3.1"资金控制账"的工程化实现，已存在。**
- `BudgetChangeController`：目标成本变更（对应文档"追加预算审批"）。
- `BudgetControlConfigService`：`WARN_ONLY / BLOCK / EXEMPT` 三模式，预警阈值 50-99（默认 80），执行率 >100% 时 BLOCK 模式硬拦截。**与文档 §11"预算 >80% 黄 / >100% 红 + 拦截"高度一致，已落地**（L4 测试含 BLOCK 负向用例）。
- `CostLedgerService` + `CostRollUpTask`：成本归集（待绑定单据 WARN 信号）。

### 1.4 资金模块（zw-finance，P0+P2 已落地）

- **老板资金看板** `FundDashboardController` → `FundDashboardVO`：垫资(额/率)、经营性现金流(入/出/净)、回款率、保证金占用、保函替代率、工资专户预警数、**滚动缺口列表 rollingGaps**（月份/预计收款/预计付款/净缺口/风险等级）。支持 `projectId` 不传时公司聚合。
- **资金计划** `FundPlanController`：年度资金预算、月度计划（含 `fill-actual` 实际回填）、**滚动预测生成**（1-12 个月，预计收款来自 APPROVED 月度收款计划，净缺口>0 且覆盖<50% → HIGH 风险）。
- **付款申请** `PaymentApplyService`：`paymentCategory`（挂 `biz_fund_category` 科目编码）、`fundPlanMonthId`（**先计划后支付**关联）、`unpaidAmountSnapshot`、**maxPayment = 累计结算 + 奖惩净额 − 累计已付 的超付硬拦截**。→ 文档 §6.2"完成产值 ≥ 应付 ≥ 已付"防线的付款端已实现。
- **资金科目** `V2026_51__fund_classification_schema.sql`：`biz_fund_category` 三级科目树。一级：EXP-DIRECT/INDIRECT/TAX/BOND/FINANCE/OTHER；二级直接成本：材料款/分包工程款/劳务款/机械款；二级间接：**仅办公费、差旅费**。
- 其他：收款、开票(apply/received/summary)、项目/个人报销、其他付款、质保金（含 `RetentionWarningTask` 逾期催办：3 天频控/180 天停催/去重）、保证金、工资专户（OVERDUE/INSUFFICIENT 合规预警）、融资、银行账户(组)/流水/余额调节、**资金日报 daily-cash-report**、财务关账 finance-lock、票据 bill（到期预警）。

### 1.5 预警现状：散点式，无中心

代码中实际存在的预警：预算 BLOCK/WARN、质保金逾期催办、工资专户合规、票据到期、库存预警（`StockWarningConfigService`）。
**没有任何统一"风险中心"实体/接口/页面**（全仓 grep 无 Risk/Alert/Warning 聚合服务）。

### 1.6 前端页面现状

- PC：`views/dashboard/{index, project-dashboard, project-cost-control}.vue`；`views/finance/*`（26 个文件，含 fund-dashboard、fund-plan、daily-cash-report 等）；`views/budget/{index, change, control-config, cost-account}`。
- 移动端：home / workbench / finance / approval / project 等，含 cost-control 页面。
- 现有 dashboard/index.vue 是"项目状态分布+收支+快捷入口"式传统看板，与驾驶舱 V1 首页（8 卡 + 四象限 + TOP 风险）结构完全不同。

---

## 2. 驾驶舱 V1 五页面逐项对照

### 页面 01 经营总览（文档 §3-§5、§18）

| 文档要求 | 现状 | 差距等级 |
|---|---|---|
| 8 张核心指标卡（合同收入/预计总成本/预计利润/利润率 + 累计回款/累计支付/应收未收/90天缺口），每卡含"当前值+环比+目标+风险态" | company-overview 有合同额/收入/支出/垫资；fund-dashboard 有回款率/rollingGaps。**无预计总成本、无预计利润、无环比、无目标对比、无风险态** | 🔴 大 |
| 预计利润趋势折线 + 点击月份归因下钻（材料/分包/工期/管理费分摊） | profit-trend 存在但**收入侧按月均摊是模拟数据**；无"预计"口径；无归因结构 | 🔴 大（且现存实现违反真实数据原则，需重做） |
| 现金流预测图 | rollingGaps（月粒度收/付/净缺口）可支撑，未按 30/60/90 天窗口组织 | 🟡 中 |
| 项目健康度横向排名（风险级→亏损→利润降幅→缺口 排序） | project-ranking 仅按产值/利润率/回款率 | 🔴 大（无健康度模型） |
| 成本结构条形图 | budget-variance / cost-control 类别汇总可支撑 | 🟢 小 |
| TOP 风险 + 待老板审批区 | 审批待办有（workflow）；风险区无 | 🔴 大 |
| 全局筛选器（公司/区域/项目/项目经理/年度/月份） | 各端点参数零散（多数无参或仅 projectId/year） | 🟡 中 |

### 页面 02 项目经营（文档 §6）

| 文档要求 | 现状 | 差距等级 |
|---|---|---|
| 项目列表带健康度（🔴🟡🟢 + 预计利润/利润率/资金缺口/主要风险） | 项目列表存在，无健康度字段 | 🔴 大 |
| 单项目详情：合同收入/目标成本/**预计成本**/**预计利润**/利润率/回款/支付/缺口 八卡 | cost-control 提供目标成本/预测/偏差（**成本侧预计已具备**）；收入侧合同额有；**预计利润 = 合同收入 − 完工预测总成本 的组装计算不存在** | 🟡 中（数据源齐，缺组装） |
| 利润变化趋势 + 成本执行率条 + TOP 成本偏差 | 成本执行率、偏差在 cost-control 有；利润趋势无 | 🟡 中 |
| 项目风险清单（预计亏损/超预算/签证待确认/回款逾期） | 无 | 🔴 大 |

### 页面 03 成本中心（文档 §7-§8）

| 文档要求 | 现状 | 差距等级 |
|---|---|---|
| 总览：目标成本/已发生/预计最终/预计超支 | **BizCostAccount 主线已完整覆盖** | 🟢 小（缺公司级聚合视图） |
| 成本结构（材料/分包/人工/机械/措施/管理/商务 + 预算/实际/预计最终/偏差/风险级） | cost-control 按类别汇总已有；**"措施费""商务费"不在现有科目树**（fund_category 与预算科目均无） | 🟡 中 |
| 商务费用下钻：招待/差旅/车辆/会议/办公 | 科目树二级仅办公费、差旅费；**招待费/车辆费/会议费科目缺失** | 🔴 大 |
| 招待费专控（12 字段：对象/事由/人数/地点…；异常分析：人均/单次最高/同一人高频/无审批） | 项目报销/其他付款可录金额+备注（种子数据里出现过"招待费"字样），**无结构化字段、无专项分析、无预警** | 🔴 大 |

### 页面 04 资金中心（文档 §9-§10）

| 文档要求 | 现状 | 差距等级 |
|---|---|---|
| 四卡：账户资金/应收未收/应付未付/90天缺口 | 账户资金（bank-account+daily-cash-report）、应收（简化口径）、应付（supplier-payable 仅采购合同 + 各类结算累计）、缺口（rollingGaps）——**数据都有，无统一聚合端点** | 🟡 中 |
| 未来 90 天资金预测（30/60/90 天 回款 vs 付款 vs 差额） | 滚动预测是**月粒度**，需换算/细化到天窗口；付款侧基于月度计划 | 🟡 中 |
| 未来大额支出 TOP（分包/材料/工资/税款） | 无按类别的未来支出分解（月度计划有分类金额可聚合） | 🟡 中 |
| 回款风险：应收逾期账龄表（项目/应收/逾期/逾期天数）+ 下钻（工程节点→应收日期→甲方审核状态→负责人→下一步动作） | **完全缺失**。无收款计划到期日模型（月度计划是月粒度粗排）、无账龄计算、无甲方审核状态跟踪 | 🔴 大 |

### 页面 05 风险中心（文档 §11-§12）

| 文档要求 | 现状 | 差距等级 |
|---|---|---|
| 统一风险台账：🔴严重/🟡关注/⚪提醒 分级 + 影响金额汇总 | 无。预警散落在 5 处（预算/质保金/工资专户/票据/库存），互不聚合 | 🔴 大 |
| 每条风险六要素：发生了什么/影响金额/为什么/谁负责/下一步动作/处理状态 | 现有预警只有消息通知，**无风险实体、无处理状态流转** | 🔴 大 |
| 风险详情弹窗 + 跳转业务单据 | 无 | 🔴 大 |
| 规则引擎自动产生状态（文档 §15 四类状态由规则判定） | 预算预警规则已配置化（阈值 50-99）；其余无规则引擎 | 🔴 大 |

### 横切：单据穿透（文档 §13、§16）

| 文档要求 | 现状 | 差距等级 |
|---|---|---|
| 任意经营数字 → 项目 → 成本分类 → 供应商 → 合同 → 采购/入库 → 付款 → 发票 → 审批 → 附件 全链下钻 | 仅 project-cost-control.vue 有 2 处跳转；**数字卡/图表普遍不可点击下钻**；后端无穿透导航所需的关联链路聚合接口（各环节数据表都在，缺"链路查询"） | 🔴 大 |

---

## 3. 资金流转流程文档逐项对照

### 3.1 已对齐（可直接复用）

| 文档要求 | 现状证据 |
|---|---|
| §3.1 资金控制账（目标成本/预算/已签合同/已发生/应付/已付/预计总成本） | `BizCostAccount` 六维主线 |
| §4 预算科目 + 追加预算审批 | budget + BudgetChange（目标成本变更走审批） |
| §5 单笔资金闭环（预算校验→申请→审批→支付→归集） | 预算 BLOCK/WARN 前置校验 → 付款申请挂 fundPlanMonthId（先计划后支付）→ Flowable 审批 → onApproved 回写累计 → CostRollUpTask 归集 |
| §6.2 分包付款三道闸（防超合同/超工程量/超结算付款） | `PaymentApplyService` maxPayment 硬拦截 |
| §10.1 预算执行率、§10.2 支付率 | budget-execution、collection-rate/fund-dashboard 可算 |
| §11 预算预警（80% 黄/100% 红+拦截） | BudgetControlConfig，默认阈值 80 |
| §11 付款预警（超产值禁付） | 超结算拦截（结算≤产值口径） |
| §15 最小 6 张表：预算表/合同台账/费用支出台账/付款台账 | 均已有对应表 |
| 资金计划（月度+滚动预测） | FundPlan 三件套 |

### 3.2 部分对齐（有基础需补强）

| 文档要求 | 差距 |
|---|---|
| §3.2 实际费用账 12 类（人工/材料/机械/分包/**措施**/管理/**商务**/差旅/**车辆**/专业服务/财税/其他） | `biz_fund_category` 现有树缺：**措施费、招待(商务)、车辆、会议、专业服务(检测/咨询/审计)**；预算科目侧同样需核对 CBS 映射 |
| §8 资金状态 11 态（已申请→…→已结算→已关闭） | 付款申请仅 DRAFT/APPROVED（+工作流过程态）；"待结算/已结算/已关闭"分布在结算单/合同累计值上，**无单笔资金统一状态视图**（可按文档口径做投影聚合，不必重构表） |
| §9 月度经营分析表（类别 × 预算/本月发生/累计发生/累计支付/应付未付/预计最终） | 数据源全在（cost account + payment + settlement），**无这张矩阵报表**；daily-cash-report 是日粒度现金账，不是月度经营分析 |
| §10.3 合同执行率（累计产值 ÷ 动态合同金额） | 产值、变更签证（change-event）、合同额都有，无该指标端点 |
| §10.4 资金缺口 | rollingGaps 月粒度已有；缺 90 天窗口与"哪个项目导致/主要付款对象"归因 |
| §6.1 材料闭环之"实际耗用 vs 理论耗用"预警 | 库存/出入库有（inventory-analysis），无理论耗用对比 |
| §11 合同变更预警（变更后金额 > 原合同 × 比例 → 重点审核） | change-event 有审批流，未见比例阈值自动升级规则（待开发时再核） |

### 3.3 完全缺失

| 文档要求 | 说明 |
|---|---|
| **预计利润体系**（§3.1 预计总成本/预计利润；驾驶舱全文的核心指标） | 无"合同收入 − 完工预测总成本"的项目级/公司级预计利润计算；BizProject 无预计利润字段；company-overview 的 profit 是历史口径，二者不可混用 |
| **利润变化归因**（驾驶舱 §5.1：点击月份展开 −220万 = 材料−82 + 分包−65 + …） | 无预计利润快照（月度 snapshot），无归因分解结构。需要"每月存预计利润快照 + 与上期差额按成本类别分解"机制 |
| **统一风险中心**（§11 全部 + 驾驶舱 §11-12） | 无风险实体表、无规则引擎（除预算外）、无风险处理状态流转、无"老板待处理事项"页面 |
| **项目健康度模型**（驾驶舱 §5.2 四级排序） | 无健康度评分/定级规则 |
| **应收逾期账龄 + 回款风险下钻**（驾驶舱 §10） | 无应收计划到期日、无账龄、无甲方审核状态字段 |
| **招待费专项管控**（资金文档 §6.3：12 字段 + 8 类异常分析） | 无结构化字段、无专项分析 |
| **月度经营分析表**（§9） | 见 3.2 |
| **穿透导航**（驾驶舱 §13/§16 五级点击链） | 见第 2 节横切 |

---

## 4. 需要修正的现存问题（非新增，是存量隐患）

1. **`profit-trend` 收入侧模拟数据**：`DashboardService.getProfitTrend` 按项目 totalIncome 年均摊到 12 个月，代码注释自认"简化/模拟"。违反项目"真实接口真实数据"铁律（base.md 第 3 条），且该端点已被 L3 测试脚本 `test-api-dashboard.sh` 覆盖（只断言 HTTP/code，测不出数据假）。**驾驶舱做利润趋势时必须以 payment_received 按 receiveDate 真实分月重做，并同步处理该旧端点（重做或废弃）。**
2. **`receivable-monitor` 口径过简**：应收 = 合同额 − 累计收款，未扣质保金/未到期部分，无账龄。做回款风险页时不能直接复用，需定义真实应收模型（基于收款计划或开票−收款差额）。
3. **company-overview 的 `profit` 命名易误导**：实为"已实现收支差"，前端如直接标"利润"会与未来"预计利润"混淆，建议改名 `realizedProfit` 并在驾驶舱中明确双口径（已实现 vs 预计）。

---

## 5. 差距汇总与优先级建议

按"驾驶舱 V1 五页面可上线"为目标倒排：

| # | 差距项 | 类型 | 建议优先级 | 理由 |
|---|---|---|---|---|
| G1 | **预计利润口径**：项目级 = 合同收入 − cost-account 完工预测总成本；公司级聚合；BizProject/VO 增字段或计算端点 | 后端 | **P0** | 驾驶舱所有页面的第一数字，且数据源（Forecast）已存在，只缺组装，ROI 最高 |
| G2 | **profit-trend 重做**（真实收款分月 + 预计利润月度快照表） | 后端 | **P0** | 存量假数据必须清除（铁律），快照表同时是 G3 归因的前提 |
| G3 | **利润变化归因**（快照环比差额按成本类别分解） | 后端 | P1 | 依赖 G2 快照 |
| G4 | **风险中心**：风险实体表（六要素+处理状态）+ 规则引擎（利润/预算/资金/回款四类规则先行）+ 页面 | 前后端 | **P0** | 文档定义的产品灵魂（"老板待处理事项中心"）；预算规则可先接入存量 BudgetControl |
| G5 | **项目健康度**（规则自动定级 🟢🟡🔴 + 四级排序） | 后端 | P1 | 依赖 G1；规则文档 §15 已给判定式 |
| G6 | **应收账龄与回款风险**：应收计划（或开票-收款）到期模型 + 账龄计算 + 下钻 | 前后端 | P1 | 资金中心页核心，工作量中等偏大（需新表） |
| G7 | **科目树补全**：措施费/招待费/车辆费/会议费/专业服务 二级科目 + 招待费结构化字段（可挂项目报销扩展） | DB+后端 | P1 | 成本中心"商务费用下钻"的前提；科目表 INSERT 幂等扩充即可 |
| G8 | **资金中心聚合端点**：账户资金/应收/应付/90天缺口四卡 + 30/60/90 天预测窗口 + 未来大额支出按类别 TOP | 后端 | P1 | 数据源全在（bank/rolling/月度计划分类），纯聚合 |
| G9 | **月度经营分析表**（类别 × 6 列矩阵报表） | 前后端 | P2 | 文档 §9，数据源齐 |
| G10 | **穿透导航**：从数字卡/图表 → 明细页 → 单据 → 审批/附件的路由与链路接口 | 前端为主 | P2 | 可增量做：先打通"项目→成本类别→合同→付款"一条主链 |
| G11 | **驾驶舱前端五页面**（新首页布局：8 卡+四象限+TOP 风险） | 前端 | 随 G1/G4/G8 分批 | 建议新建 `views/cockpit/` 独立路由，不改造旧 dashboard/index（旧页保留给项目管理层） |
| G12 | 全局筛选器（年度/月份/项目/项目经理） | 前后端 | P2 | 端点需统一加参数，涉及面广，后置 |
| G13 | 资金 11 态统一视图（投影聚合，不改表） | 后端 | P2 | 锦上添花 |

### 明确"不需要做"的（文档要求已被现状覆盖）

- 预算 80%/100% 预警与拦截（BudgetControlConfig 已配置化）
- 超结算付款拦截（PaymentApplyService 已实现）
- 成本控制账六维主线（BizCostAccount）
- 先计划后支付（paymentApply.fundPlanMonthId）
- 滚动资金预测（FundPlanService，含风险分级）
- 质保金/工资专户/票据/库存四类散点预警（已有，将来作为风险中心的规则数据源接入即可）

---

## 6. 建议的实施切分（供决策）

- **第一批（P0，构成最小可用驾驶舱）**：G1 预计利润组装 + G2 利润趋势重做（含快照表）+ G4 风险中心骨架（实体+4 类规则+页面）+ G11 经营总览页（8 卡中先上 6 卡）
- **第二批（P1）**：G3 归因 + G5 健康度 + G6 应收账龄 + G7 科目补全 + G8 资金中心页 + G11 其余页面
- **第三批（P2）**：G9 月度分析 + G10 穿透 + G12 筛选器 + G13 状态视图

> 所有新增遵循项目铁律：真实接口、无 mock/fallback；新表迁移脚本双轨放置（`zw-app/db/migration` + `deploy/db-init`）；集成测试用 tenant_id=9999；前后端字段以 Controller 为准。

---

## 附：证据文件索引

| 证据 | 路径 |
|---|---|
| 公司看板 13 端点 | `zw-insight-server/zw-dashboard/.../controller/DashboardController.java` |
| profit-trend 收入均摊（模拟） | `zw-insight-server/zw-dashboard/.../service/DashboardService.java` L677-710 |
| receivable 简化口径 | 同上 L182-225 |
| Cost360 / cost-control | `zw-insight-server/zw-dashboard/.../controller/ProjectDashboardController.java` L134-141 |
| 成本控制主线 | `zw-insight-server/zw-budget/.../domain/BizCostAccount.java`、`service/CostAccountService.java` |
| 预算管控 WARN/BLOCK | `zw-insight-server/zw-budget/.../service/BudgetControlConfigService.java` |
| 老板资金看板 VO | `zw-insight-server/zw-finance/.../vo/FundDashboardVO.java` |
| 滚动预测 | `zw-insight-server/zw-finance/.../service/FundPlanService.java` L181-238 |
| 超付拦截 | `zw-insight-server/zw-finance/.../service/PaymentApplyService.java` L310-316 |
| 资金科目树 | `zw-insight-server/zw-app/src/main/resources/db/migration/V2026_51__fund_classification_schema.sql` L272-286 |
| 付款申请科目/计划关联 | `zw-insight-server/zw-finance/.../domain/BizPaymentApply.java`（paymentCategory/fundPlanMonthId） |
| 前端看板页 | `zw-insight-web/src/views/dashboard/{index,project-dashboard,project-cost-control}.vue` |
| 资金页面群 | `zw-insight-web/src/views/finance/*`（26 文件） |
