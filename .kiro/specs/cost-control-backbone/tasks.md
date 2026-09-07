# Cost Control Backbone - Tasks

> **状态说明**：本文件记录 Phase 1 的实际交付情况。
> 勾选项 = 已实现且已验证（编译通过 + 测试通过 + 构建通过）；
> `[~]` = 已实现但验证受限；`[ ]` = 未做（明确列入 Phase 2）。
> 禁止把未做的事勾成完成——验收时逐项对照代码核实。

## Phase 1: Foundation — ✅ 已完成

### Task 1.1: 数据库迁移
**状态**: DONE

- [x] 迁移脚本 `deploy/db-init/51_V2026_49__cost_control_backbone.sql`
- [x] 建表 6 张（全部 `CREATE TABLE IF NOT EXISTS`，可重复执行）：
  - `biz_project_wbs_node` — WBS 工作分解结构
  - `biz_cost_account` — CBS 成本账户（六维金额）
  - `biz_cost_account_txn` — 成本流水台账（幂等锁 + 可追溯）
  - `biz_cost_account_link` — 源单据→账户 显式绑定（消除科目多账户歧义）
  - `biz_change_event` — 变更事件
  - `sys_outbox_event` — 事务性发件箱
- [x] 编号规则种子：`CHANGE_EVENT`（CHG+yyyy+4位，reset_period=YEAR）
- [x] 菜单/权限目录种子：ID 段 20201-20238（高于 47 号迁移的 20199，低于演示数据 90001）
- [x] 超管角色授权绑定（role_id=1），上线即可见
- [x] 列名规避 SQL 保留字：`node_code/node_name/node_level`、`account_code/account_name`
- [x] 不加物理外键（沿用仓库既有约定：引用完整性由应用层 + 逻辑删除保障）
- [x] 回滚脚本写在文件头注释（DROP 顺序 + 菜单/编号规则清理）
- [ ] **存量数据回填**：把 `biz_budget_detail` 映射为 CBS 账户 —— 列入 Phase 2
      （原因：回填需按租户确认科目口径，自动映射可能产生错误归集；
      当前 CBS 为增量归集层，原预算口径继续可用，不阻塞上线）
- [ ] **tenant_id=9999 实库验证** —— 本环境无 MySQL 实例，未执行（见「受阻项登记」）

### Task 1.2: 领域模型与服务

#### zw-project（WBS）— DONE
- [x] `BizProjectWbsNode.java` — 实体
- [x] `BizProjectWbsNodeMapper.java` — Mapper
- [x] `ProjectWbsNodeService.java` — 树装配（O(n) 内存建树，非逐层查询）+ 不变量：
      编号唯一 / 层级由父节点推导 / 移动时环检测 + 子树层级平移 / 删除保护 / 日期合法
- [x] `ProjectWbsNodeController.java` — REST `/api/v1/project/{projectId}/wbs/nodes`

#### zw-budget（CBS + 成本流水）— DONE
- [x] `BizCostAccount.java` — 六维金额 + 派生指标（剩余/偏差/变更额/使用率/承诺率/超支判定）
- [x] `BizCostAccountTxn.java` — 流水实体（delta + balance_after）
- [x] `BizCostAccountMapper.java` — 分页/建树/按类别汇总 + 三个原子调整 SQL（带非负约束）
- [x] `BizCostAccountTxnMapper.java` — 幂等探针/账户流水/来源汇总
- [x] `CostAccountService.java` — CRUD + 锁定/关闭 + 树查询
- [x] `CostLedgerService.java` — **金额变动唯一入口**：
      幂等键 (sourceType, sourceId, accountId, amountType) + 原子调整 + 台账留痕；
      锁定账户冻结预算口径但放行实际成本回写；BASELINE/FORECAST 走覆盖式而非增量
- [x] `CostAccountController.java` — REST `/api/v1/budget/cost-account`（含 `/ledger` 下钻）
- [x] `ChangeEventApprovedCostHandler.java` — Outbox 消费者，把批准变更传导到 CBS current_amount
- [ ] `vw_cost_ledger` 视图 —— **取消**（读模型由 `ProjectCostControlService` 内存聚合，
      无消费方的视图属死 schema 表面，违背「不为重构而重构」）

#### zw-contract（变更事件）— DONE
- [x] `BizChangeEvent.java` — 实体 + 三个嵌套值对象（ImpactAssessment/AffectedAccount/SupportingDoc）
- [x] `ChangeEventStatus.java` — 业务状态机（6 态，穷举流转表 + assertTransition）
- [x] `ChangeEventEvents.java` — 事件类型常量（集中定义防拼写漂移）
- [x] `BizChangeEventMapper.java` — 分页/sourceRef 查重/待处理计数/已批准金额合计
- [x] `ChangeEventService.java` — 全生命周期 + 业务事实去重 + Outbox 投递
- [x] `ChangeEventController.java` — REST `/api/v1/contract/change-event`
      （身份一律取 SecurityContext，**不接受请求头传 approvedBy**——否则审计不可信）
- [ ] Flowable BPMN 审批流定义 —— 列入 Phase 2
      （当前 approve/reject 为直接状态流转；状态机与审批流已分离，
      接入 BPMN 只需在 approve 前插一道流程校验，不需改业务模型）

### Task 1.3: Transactional Outbox — DONE
- [x] `sys_outbox_event` 表（`sys_` 前缀 → 被租户拦截器 ignoreTable 排除，支持跨租户扫描）
- [x] `OutboxEvent.java` / `OutboxStatus.java` — 实体与状态枚举
- [x] `OutboxEventMapper.java` — 到期查询 / 乐观抢占 claim / 成功 / 失败退避 / 死信 / 清理
- [x] `OutboxEventRecorder.java` — **事务内写入**，幂等键含聚合版本号；
      幂等命中静默跳过，其他失败抛异常让业务事务一并回滚（宁可失败也不丢事件）
- [x] `OutboxDispatcher.java` — 定时投递（30s 可配）+ 指数退避（60×2ⁿ）+ 死信 + 保留期清理；
      集群安全（claim 乐观占用）；投递前回填租户上下文，投递后恢复
- [x] `OutboxEventHandler.java` / `OutboxMessage.java` — SPI 与消息载体（含幂等键/attempts/租户）
- [x] `ChangeEventApprovedEvent.java` — **共享事件契约放 zw-common**（Published Language）：
      消费方只依赖 zw-common，不必反向依赖 zw-contract，模块边界保持单向
- [ ] RabbitMQ 实现 —— **不做**：仓库已声明 amqp 依赖但无任何配置与用法，
      接入未配置的 broker 属引入未验证依赖。当前为进程内投递 + DB 持久化，
      Handler SPI 已抽象，后续换 MQ 只需替换 Dispatcher 的投递段
- [ ] Redis 幂等检查器 —— **不需要**：幂等由 DB 唯一索引保证（比 Redis 更强，重启不丢）

---

## Phase 2: Frontend PC — ✅ 已完成（核心页面）

### Task 2.1: Project Cost 360 看板 — DONE
- [x] `views/dashboard/project-cost-control.vue` — KPI 四卡（目标/当前/实际/EAC）
      + CBS/WBS 树筛选 + 六维明细表（冻结列/搜索/使用率进度条/偏差标红）
- [x] `api/dashboard.ts` — `getProjectCostControl()` + 5 个 DTO 类型
- [x] 后端 `ProjectCostControlDTO` + `ProjectCostControlService`
      + `ProjectDashboardController.getCostControl()`（项目不存在返回 404，沿用既有约定）
- [x] 路由注册 `/project-cost-control` + 菜单 `dashboard:costcontrol:view`
- [ ] 抽离 `KpiMetricCard/VarianceChart/CostLedgerTable` 为独立组件 —— 列入 Phase 2
      （当前内联在页面中，功能完整；抽组件属复用性优化，非功能缺口）
- [ ] ECharts 偏差趋势图 —— 列入 Phase 2（后端 `trends` 字段已预留，需实际成本流水按月聚合）

### Task 2.2: WBS / CBS 管理页 — DONE
- [x] `views/project/wbs/index.vue` — 树视图 + 增删改 + 加子节点 + 层级/状态标签 + 悬停操作
- [x] `views/budget/cost-account/index.vue` — 六维表格 + 汇总条 + 树形装配
      + 锁定/删除/批量删除 + **成本流水抽屉**（下钻「这个数字怎么来的」）
- [x] `api/wbs.ts` / `api/cost-account.ts` — 完整类型定义
- [x] 路由注册 `/project/wbs`、`/budget/cost-account`

### Task 2.3: 变更事件管理 — DONE
- [x] `views/contract/change-event/index.vue` — 列表 + 四维筛选 + 状态标签 + 行内操作
- [x] `views/contract/change-event/detail-drawer.vue` — Drawer 详情（基本信息/影响评估/
      受影响账户/佐证文档/操作区），符合「详情优先 Drawer 减少跳转」
- [x] `views/contract/change-event/form-modal.vue` — 新增/编辑弹窗
- [x] `api/change-event.ts` — 全量端点 + 类型
- [x] 路由注册 `/contract/change-event`
- [ ] 影响评估的账户选择器（当前手填 accountId）—— 列入 Phase 2，需接 CBS 树下拉
- [ ] 附件真实上传（当前仅结构占位）—— 列入 Phase 2，需接 zw-file 上传接口

---

## Phase 3: Mobile App — ✅ 已完成

### Task 3.1: 现场变更事件采集 — DONE
- [x] `pages/contract/change-event/index.vue` — 列表 + 状态筛选 chips + 下拉刷新/上拉加载
      + 失败显式重试（不静默空列表）+ 项目选择弹窗（复用 construction-log 交互）
- [x] `pages/contract/change-event/create.vue` — 现场登记：
      来源类型/标题/描述/影响类别/关联签证号 + **拍照自动水印**（时间/GPS/人员/项目）
      + 双按钮「存草稿 / 提交评估」+ 离线入队（无照片时）
- [x] `pages/contract/change-event/detail.vue` — 详情 + **流转进度四步可视化**
      + 影响评估 + 受影响账户 + 佐证图片预览 + 按状态机给出可执行动作
- [x] `api/common.ts` — 12 个新端点（变更事件 CRUD/流转 + WBS/CBS/成本看板只读）
- [x] `pages.json` 注册 3 个页面（通过 pages-registry 一致性测试，无孤儿页/死路由）
- [x] 离线策略遵循既有约定：含照片明确拒绝离线（临时路径不可序列化，不静默丢图）；
      状态流转强一致操作离线拒绝（避免留永久中间态）；纯文本登记离线入队由 syncEngine 补投
- [x] 全部使用 uni-app 原生组件（view/text/scroll-view），**未引入 Element Plus**

---

## Phase 4: 测试与验证

### Task 4.1: 单元测试 — ✅ 新增 142 个全绿
- [x] `ChangeEventStatusTest` — 40 例：穷举 6×6 流转（合法 9 / 非法 18 / 终态无出边 / null 容错）
- [x] `ChangeEventServiceTest` — 34 例：登记去重、评估完整性、审批流转、终态只读、
      来源类型不可改、Outbox 用聚合版本号做幂等区分
- [x] `CostLedgerServiceTest` — 22 例：记账/负余额拒绝/幂等命中/并发撞唯一键转 DUPLICATE/
      锁定账户放行实际成本/覆盖式设置/参数校验
- [x] `ChangeEventApprovedCostHandlerTest` — 14 例：方向解析（INCREASE/DECREASE）、
      多账户幂等键、零变动过滤、脏数据容忍、**有成本影响却无明细则抛异常**（不静默丢账）
- [x] `CostRollUpServiceTest` — 20 例：承诺/实际口径分离、多单据累加为一笔差额、
      **歧义不分摊**、显式绑定优先、部分绑定、源单据减少产生负 delta 冲回、
      单账户失败不阻断其余、null 金额容忍
- [x] `CostAccountLinkServiceTest` — 12 例：**跨项目绑定拦截**、重复绑定幂等、
      解绑不触碰已记账金额、批量绑定只计新建数、超长字段拒绝
- [x] 回归验证：zw-common 105 / zw-project 58 / zw-contract 364 / zw-budget 466 / zw-dashboard 19 = **1012 例全绿**
- [x] 前端回归：web 103 文件 1109 例全绿；app 18 文件 141 例全绿
- [x] 前端构建：`vite build` 9374 模块通过，新页面产物已生成
      （构建暴露并修复了 `Export` 图标不存在的真实缺陷——测试全绿但构建会挂）
- [ ] JaCoCo 覆盖率基线登记 —— 待做（`tests/coverage-baseline.json` 需加新模块实测值）

### Task 4.2: 集成测试 — 未做
- [ ] `keys/test-api-change-event.sh`（CRUD + 状态流转 jq 断言）
- [ ] `keys/test-api-cost-control.sh`（看板端点）
- [ ] L4 全生命周期仿真扩展（tenant_id=9999 跑通变更→预算传导）
      **受阻原因**：本环境无 MySQL/Redis 实例，无远程联调凭证（`keys/zwinsight.pem` 已 gitignore）

### Task 4.3: 人工 QA — 待产品/QA 执行
- [ ] 全链路：建 WBS → 挂 CBS → 登记变更 → 评估 → 批准 → 查 CBS current 与流水
- [ ] 移动端离线登记 → 联网同步
- [ ] 看板 KPI 与真实数据核对

---

## 受阻项登记（依 AGENTS.md「测试受阻汇报规则」）

| 日期 | 层级 | 测试项 | 分类 | 原因 | 影响范围 | 处置 |
|------|------|--------|------|------|----------|------|
| 2026-09-04 | L2/L3 | Testcontainers 集成测试、`keys/test-api-*.sh` 契约验证、L4 生命周期仿真 | ENV | 本地无 Docker/MySQL/Redis 实例；无远程联调凭证 | 新增 25+ REST 端点未经真库验证；DB 迁移脚本未在真实 MySQL 执行过 | 已完成 L1 单测 132 例 + 全模块编译 + 前端构建三重验证；**待用户决策**：①提供联调环境后补跑 ②接受 L1 验证先行合并 ③缩减范围 |
| 2026-09-04 | L1 | JaCoCo 覆盖率采集 | ENV | 覆盖率基线需 CI 环境实测值回填 | 新模块未登记覆盖率基线，CI 可能因缺基线报错 | 待补 `tests/coverage-baseline.json` 条目 |

> 说明：L1 单元测试（132 例新增 + 527 例回归）与前端 1250 例全部真实执行通过，
> 未使用 mock 数据替代真实验证；受阻项仅限需要真实数据库/服务实例的 L2-L4 层级。

---

## 回滚方案

**粒度：单个 commit 可回滚，不影响既有能力。**

1. **数据库**：执行迁移文件头部的回滚 SQL（DROP 5 表 + DELETE 菜单/角色绑定/编号规则）
2. **后端**：新增文件均为独立类，删除即可；唯一改动的既有文件是
   `ProjectDashboardController.java`（新增一个 `@GetMapping` 方法），回退该 hunk 即可
3. **前端**：新增 4 个 view + 3 个 api 文件；既有文件改动仅 `router/index.ts`（4 条路由）
   与 `api/dashboard.ts`（追加），回退这两个文件的 hunk 即可
4. **移动端**：新增 3 个 page；既有文件改动仅 `api/common.ts`（追加）与 `pages.json`（3 条注册）
5. **存量数据零影响**：未修改任何既有表结构，`biz_budget_detail` 口径继续可用

---

## Phase 2 已完成部分：成本自动归集（主链最后一公里）

> 本轮追加。原 Phase 2 待办中优先级最高的两项已落地：
> 「承诺额自动占用」与「实际成本自动归集」。

### Task 2.1: 成本归集服务 — ✅ DONE

**补齐的断点**：此前 commitment/actual 只能靠人工调 `/sync` 接口，等于把「合同签了多少、
结算了多少」重新抄一遍——直接违反「禁止重复录入业务事实」。现在业务模块照常审批，
成本主线自动跟上。

- [x] `CostRollUpMapper` — 单据级查询（非科目汇总级），9 个源：
      采购/劳务/机械/分包合同（承诺）+ 采购/劳务/机械/分包结算 + 材料出库（实际）
- [x] `CostRollUpService` — 归集主逻辑：
      单据 → 账户解析（显式绑定优先 → 科目唯一账户自动 → 歧义报 unmapped）
      → 目标绝对值 vs 库内当前值 → 差额走 CostLedgerService 幂等记账
- [x] `BizCostAccountLink` + Mapper + `CostAccountLinkService` — 显式绑定维护（含跨项目拦截）
- [x] `CostRollUpTask` — 每日 02:30 自动对账（Redis 分布式锁 + TenantTaskRunner 逐租户）
- [x] Controller 端点：`POST /rollup`、`GET /link/list`、`POST /link`、`POST /link/batch`、`DELETE /link/{id}`
- [x] 前端：CBS 页「立即归集」按钮 + 归集报告弹窗（待绑定清单 + 失败明细）+ 绑定账户对话框（仅列同科目未关闭账户）
- [x] 单测 32 例（`CostRollUpServiceTest` 20 + `CostAccountLinkServiceTest` 12）

**三个关键设计决策**：

1. **成本口径：实际成本 ≠ 付款**
   材料以「出库消耗」计成本，分包/劳务/机械以「结算」计成本，付款只是现金流时点。
   用付款当成本会让「已付款未结算」虚增成本、「已结算未付款」漏记成本，
   两者都使项目核算失真——这是施工 ERP 最经典的口径错误。

2. **歧义不猜**
   一个科目多个账户时（材料拆成「混凝土」「钢筋」），一份合同该记哪个账户
   **无法从数据推断**。归集遇歧义报告 unmapped 等业务人员绑定，
   绝不按比例自动分摊——分摊错会导出错误的项目盈亏结论，危害远大于「暂时少归集一笔」。

3. **幂等键编码「状态跃迁」而非「单据」**
   `ROLLUP:{accountId}:{amountType}:{from}->{to}`。归集算的是目标绝对值，与库内做差得 delta：
   - 重复跑：from 已等于 to → delta=0 → 不写流水
   - 并发跑同一跃迁：唯一键冲突 → DUPLICATE，杜绝双记
   - 源单据变化（含合同作废导致金额下降）：产生新跃迁 → 正常记账，负 delta 自动冲回
   因此本服务可安全地重复执行、定时执行、手工触发，结果始终收敛到正确值。

---

## Phase 2 剩余待办（明确不在本次范围）

| 项 | 价值 | 依赖 |
|----|------|------|
| 存量预算回填 CBS | 老项目直接有成本主线视图 | 需按租户确认科目口径映射 |
| Flowable BPMN 接入变更审批 | 多级审批/退回/加签 | 状态机已分离，接入点清晰 |
| 合同累计变更金额回写 Handler | 变更→合同传导闭环 | 复用已建 Outbox，加一个 Handler |
| EAC 公式化预测（AC + (BAC−EV)/CPI） | 预测从手填变算出 | 需进度模块提供 EV |
| 偏差趋势图（按月） | 看板可视化完整 | 后端 trends 字段已预留 |
| 三单匹配（PO/收货/发票） | 材料链路防重防漏 | 独立专题 |
| 变更事件影响评估的账户选择器 + 附件真实上传 | 变更录入体验 | 接 CBS 树 + zw-file |
| 归集绑定的「批量绑定」前端入口 | 一次处理多张待绑定单据 | 后端 `POST /link/batch` 已就绪 |
