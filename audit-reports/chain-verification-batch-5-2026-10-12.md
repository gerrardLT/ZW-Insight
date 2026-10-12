# 全链路验收报告 · 批次 5（阶段 9D / 9E / 9F / 9G）

> 验收日期：2026-10-12
> 环境：主环境 `129.204.3.200`（tenant 1 演示租户；后端 :18080 / 前端 :18081，均实测）
> 权威定义：`keys/lifecycle-sim-v2.sh` 的 `stage_9d/9e/9f/9g`
> 方法：静态（`file:line`）+ 动态（真机 Node `fetch` API 链、真机无头 Playwright 抓包）
> 纪律遵守：全程 tenant 1；未动 90001-90004 种子；未本地起 JVM/mvn；Flowable 运行态一律经应用层 `terminate`（未直删 `ACT_*`）
> 数据前缀：`E2E_TEST_B5_`（收尾已全部清空，见附录 D）

---

## 汇总

| 阶段 | 后端链路 | 前端流程 | 关键缺口 |
|---|---|---|---|
| 9D 付款闭环 | **通** | **通** | 无阻断。仅 P3：前端支付态筛选缺 `PARTIAL_PAID`（V2026_64 已产此态） |
| 9E 驳回分支 | **通** | **通** | 无 |
| 9F 变更签证 | **部分通** | **不通** | ① 变更事件→合同累计变更额**无处理器**（已知断裂点 #1，复验成立）；② 变更事件→CBS 传导**运行时崩溃**（`MyBatisSystemException`）；③ 变更事件详情/编辑前端 `Number(id)` 精度丢失 → **打不开任何真实单据**；④ 变更签证 UI 无「提交审批」入口且状态被后端强制回 DRAFT |
| 9G 质保金 | **通** | **部分通** | 前端只能登记返还，**无「提交返还审批」入口** → UI 走不到 RETURNED |

**批次结论**：9D / 9E 后端与前端均**可跑通并产生正确数据**（含双口径、金额分级、两条驳回路径）。9F 是重灾区——**变更事件与变更签证双轨未打通**（已知断裂点 #1 复验成立），且叠加两个**新发现的运行时/前端缺陷**，导致「变更事件批准 → 合同产值上限抬升」这条链路在真机上**完全走不通**；变更签证虽后端通，但前端无审批入口。9G 后端闭环通，前端缺提交入口。

---

## 阶段 9D：付款闭环

### 后端逻辑链路

- **数据模型**：`biz_payment_apply`（`zw-finance/.../domain/BizPaymentApply.java:18` `@TableName`）。
  - 审批态 `status`（`:72`）取值 `DRAFT/SUBMITTED/APPROVED/REJECTED`；
  - 现金态 `payStatus`（`:75`）取值 `UNPAID`（`:22`）/`PARTIAL_PAID`（`:31`，V2026_64）/`PAID`（`:33`）；`payDate`/`payAccountId` 为 `ALWAYS` 更新策略（`:79-84`，撤销时需置空）。
  - DDL 实证：`pay_status varchar(20) NOT NULL DEFAULT 'UNPAID'`（列默认即 `UNPAID`）。
- **状态机**：
  1. 创建：`PaymentApplyService.save:91` → 金额 `>0` 校验 `:93`；资金分类校验 `:97`；**先计划后支付** `fundPlanService.validatePaymentAgainstPlan:101`；置 `DRAFT:103`；清系统字段 `clearSystemManagedFields:105`（丢弃请求体的 `payStatus/payDate/payAccountId/快照/workflowInstanceId`）；`insert:106`。
  2. 提交：`submit:225`（`@Transactional`）→ 仅 `DRAFT/REJECTED` 可提交 `:230` → 预算控制 `enforceBudgetControl:235`（BLOCK 拦截 / WARN 透传响应头）→ `resolvePayable:238` 按 `contractCategory` 路由取合同可付信息 → **上限校验** `validatePaymentLimit:242` → 回填累计结算/未付快照 `:255-256` → **金额分级** `amountTierService.matchTier(PAYMENT_APPLY, amount):266` 写入流程变量 `approvalTier`（未命中=0）→ `approvalService.startProcess("PAYMENT_APPLY", id, "payment_apply_approval", vars):274` → 置 `SUBMITTED:278`。
  3. 审批通过回调：`onApproved:319`（`@Transactional`）→ 幂等守卫 `:325`（已 APPROVED 直接返回）→ 仅 `SUBMITTED/REJECTED` 可生效 `:332`（REJECTED 覆盖「退回至发起人后流程仍在走」场景）→ **生效前复校上限** `:346`（不过则置 REJECTED 并通知发起人 `:348-352`）→ **并发超付兜底**：先原子累加 `addCumulativePaid:357`，读回复核 `exceedsLimitAfterWriteback:359`，超限则反冲并驳回 `:360-367` → 置 `APPROVED:369` → **项目总支出（审批口径）** `projectMapper.addTotalExpense:373`。
  4. 驳回回调：`onRejected:382` → 仅 `SUBMITTED` 置 `REJECTED:391`（无回滚）。
  5. 现金态：`refreshPayStatus:411`（银行勾稽驱动，三档 UNPAID/PARTIAL_PAID/PAID）、`markPaid:463` / `revokePaid:500`（手工标记/撤销）。**均不回写 `total_expense`/合同累计已付**（`:397-406` 注释明示口径不变量）。
- **API（双向核对一致）**：后端 `PaymentApplyController.java:22` 基路径 `/api/v1/finance/payment-apply`；`GET /page:29`、`GET /{id}:40`、`POST:45`、`PUT /{id}:53`、`DELETE /{id}:61`、`POST|PUT /{id}/submit:67`、`POST /batch:80`、`POST /{id}/mark-paid:91`、`POST /{id}/revoke-paid:105`。前端 `src/api/finance.ts:91-137` 逐条对应（提交用 `PUT`，后端 POST/PUT 双收，一致）。
- **BPMN**：`processes/payment_apply_approval.bpmn20.xml` —— `managerApproval`（`:17`）→ `financeApproval`（`:26`）→ 排他网关 `gwTier3`（`:36`，`${approvalTier>=3}` 追加 `cfoApproval:43`）→ `gwTier4`（`:52`，`${approvalTier>=4}` 追加 `bossApproval:59`）。**各级 `assignee` 均为 `${initiator}`**（自审语义，见断裂点 P3-1）。
- **调用链**：`ProcessCompleteListener` 发 `ApprovalCompleteEvent` → `PaymentApplyApprovalListener.onApprovalComplete:32`（筛 `PAYMENT_APPLY`）→ `PaymentApplyService.onApproved`。驳回/终止：`ApprovalRejectEvent` → `PaymentApplyApprovalListener.onApprovalReject:47` → `onRejected`。
- **跨模块联动（写别人的表）**：`addCumulativePaid:621` 按分类写 `biz_other_contract.cumulative_paid` 或 `biz_purchase_contract/labor/machine/subcontract.cumulative_paid`；`projectMapper.addTotalExpense` 写 `biz_project.total_expense`。**不回写 `pay_status`**（由 DDL 默认 `UNPAID`）。
- **校验拦截**（负向实测均被拦截）：
  - 超付：`validatePaymentLimit:532`，判定式 `paymentAmount > (cumulativeSettlement + 净奖惩 − cumulativePaid)` → 实测 `code 500`「付款金额不能超过（累计结算含奖惩）减已付金额，最大可付金额：35000.00」。
  - 金额 ≤0：`save:93` → 实测 `code 500`「付款金额必须大于0」。
  - 先计划后支付：`FundPlanService.validatePaymentAgainstPlan:336`（当月有 APPROVED 计划且累计已审批付款+本次 > 计划付款额则拒）。
  - 预算：`enforceBudgetControl:564`（BLOCK 抛业务异常）。
- **判定：通**（含双口径、金额分级、跨模块路由）
  - 证据（真机 API，Node `fetch`，UTF-8 正确）：
    - `POST /finance/payment-apply {165000}` → 200；`GET /{id}` `status=DRAFT payStatus=UNPAID`。
    - `PUT /{id}/submit` → 200；`status=SUBMITTED`，快照 `cumulativeSettlementSnapshot=200000 / unpaidAmountSnapshot=200000`（提交时点回填正确）。
    - 逐级 `complete`（2 级）→ `status=APPROVED payStatus=UNPAID payDate=null`；`GET /contract/other/{id}` `cumulativePaid=165000`；`GET /project/{id}` `totalExpense=165000`。
    - **双口径复验**：审批通过只写 `total_expense`（审批口径），`pay_status` 保持 `UNPAID`（现金口径，未回写 total_expense）——符合 AGENTS.md 明令。
    - **金额分级复验**：`POST 500000`（命中 `sys_amount_tier_config` tier=3）→ 流程任务链 `项目负责人审批 → 财务审核 → 财务负责人审批`（`ACT_HI_TASKINST` 实证，3 个任务）；最终 `status=APPROVED`、`totalExpense=500000`。

### 前端操作流程

1. **入口**：菜单「财务管理 > 付款申请」→ 路由 `/finance/payment-apply`（`src/router/index.ts:314-319` → `src/views/finance/payment-apply.vue`）。
2. **实测点击序列**（无头 Playwright 真机，`e2e/tests/real/_b5-ui.spec.ts`，`--workers=1 --no-deps`）：
   - 9D-1：进页面 → 点「新增付款申请」→ 直接点「确定」→ **6 个内联必填错误、发出 POST 计数 = 0**（客户端拦截有效）。
   - 9D-2：点「新增付款申请」→ 选项目（remote）/合同类型（其他支出合同）/关联合同/收款单位（`SupplierSelector` 远程）→ 填金额 1000 → 选付款日期 → 点「确定」。
3. **实际请求**：`POST /v1/finance/payment-apply → 200 code 200`；行内点「提交」→ 确认框「确定」→ `PUT /v1/finance/payment-apply/{id}/submit → 200 code 200`。
4. **状态回显**：列表新行标签变「**审批中**」；点「查看」→ 详情抽屉（`GET /finance/payment-apply/{id}`）正确回显：付款金额 `1,000.00`、累计结算快照 `100,000.00`、未付金额快照 `100,000.00`、状态「审批中」、支付状态「—」（未审批故无支付态，条件渲染正确）。
5. **边界**：行内按钮按状态收敛（`payment-apply.vue:101-104`：`提交` 仅 DRAFT/REJECTED、`删除` 仅 DRAFT、`标记支付` 仅 APPROVED 且未 PAID）。
- **判定：通**
  - 证据：`[9D-1] inline errors = 6 posted = 0`；`[9D-2] POST /finance/payment-apply -> 200 200 操作成功`、`[9D-2] submit -> 200 200`、`[9D-2] detail drawer = …付款金额 1,000.00 … 累计结算快照 100,000.00 … 状态 审批中 …`。

### 断裂点

- **P3-5（前端筛选口径缺档）**：`payment-apply.vue:28-33` 支付状态筛选只给 `UNPAID/PAID` 两项，而后端 V2026_64 起会产生 `PARTIAL_PAID`（`BizPaymentApply.java:31`）→ 部分支付单据无法按支付态筛出。建议补 `PARTIAL_PAID` 选项。
- **P3-1（自审，与批次 1 同类）**：`payment_apply_approval.bpmn20.xml:17/26/43/59` 各级 `assignee=${initiator}` → 发起人自审；`PAYMENT_APPLY` 不在 `SELF_APPROVAL_GUARDED_TYPES`（`ApprovalService.java:61-65`）内，故不被防自审拦截。功能可过、语义失效。

---

## 阶段 9E：驳回分支

### 后端逻辑链路

- **数据模型**：同 9D（`biz_payment_apply.status` 终态 `REJECTED`）；流程态由 Flowable 维护。
- **状态机（两条真实路径）**：
  - **路径 A 退回重审**：`reject-start` → `ApprovalService.rejectToStart`（`ApprovalController.java:63`）**不终止流程**，仅把单据置 `REJECTED`（经 `ApprovalRejectEvent → onRejected:382`），流程回到发起人节点；重审通过再次回调 `onApproved`（`:332` 显式放行 `REJECTED`）→ `APPROVED`。
  - **路径 B 终止重提**：`terminate` → `ApprovalService.terminate`（`ApprovalController.java:72`）**终止流程**，单据置 `REJECTED`；重新 `submit` 起**新流程实例**（`submit:230` 放行 `REJECTED`）→ `APPROVED`。
- **API（双向核对一致）**：`POST /api/v1/workflow/approval/reject-start:63`、`reject-previous:54`、`terminate:72`、`withdraw-by-business:82`。前端 `src/api/workflow.ts` 对应 `rejectToStart/rejectToPrevious/terminateProcess`。
- **调用链**：`ApprovalController` → `ApprovalService`（`assertTaskAssignee:937`；`SUPER_ADMIN` 放行 `:947`）→ 发 `ApprovalRejectEvent` → `PaymentApplyApprovalListener.onApprovalReject:47`。
- **校验拦截**：`terminate` 后单据不得再被旧流程回调（`onApproved:332` 只认 `SUBMITTED/REJECTED` 且状态非 APPROVED；终止后重提起新流程）。
- **判定：通**
  - 证据（API 真机）：
    - 路径 A：`submit` 200 → `reject-start` 200 → `GET` `status=REJECTED` → 逐级 `complete` → `status=APPROVED`。
    - 路径 B：`submit` 200 → `terminate` 200 → `GET` `status=REJECTED` → 重新 `submit` 200 → `complete` → `status=APPROVED`。

### 前端操作流程

1. **入口**：菜单「工作流管理 > 审批管理」→ `/workflow/approval`（`src/router/index.ts:905-907` → `src/views/workflow/approval/index.vue`）。
2. **实测点击序列**：
   - 9E-1（退回重审）：行内「查看详情 / 审批」→ 抽屉 → 填意见 → 选「退回发起人」→ 点「退回」。
   - 9E-2（终止重提）：行内「查看详情 / 审批」→ 抽屉 → 填意见 → 点「终止流程」→ 确认框「确定」。
3. **实际请求**：`POST /v1/workflow/approval/reject-start → 200 code 200`（9E-1）；`POST /v1/workflow/approval/terminate → 200 code 200`（9E-2）。
4. **状态回显**：抽屉头部展示任务名「项目负责人审批」+「待我审批」；业务详情区展示 `单据ID/付款金额/收款单位/合同分类/累计结算快照/可付快照`；操作后 `GET /finance/payment-apply/{id}` 均 `status=REJECTED`。抽屉内「单据ID」与我的付款申请 ID **逐一吻合**（排除并行批次串扰）。
5. **边界**：操作区按状态渲染（`approval/index.vue:94-98`：仅 `activeTab==='todo' && detail.status==='pending'` 显示 通过/退回/终止）；退回、终止必填意见（`:195` 前端预检「请填写退回或终止原因」）。
- **判定：通**
  - 证据：`[9E-1] drawer 单据ID = 2109581880463507457 my id = 2109581880463507457`、`[9E-1] reject-start -> 200 200`、`payment status after reject-start = REJECTED`；`[9E-2] terminate -> 200 200`、`payment status after terminate = REJECTED`。

### 断裂点

- 无。

---

## 阶段 9F：变更签证 / 变更事件

> 本阶段是任务书 §七 已知断裂点 #1（变更事件与变更签证双轨未打通）的复验对象，**同时新发现 2 个运行时/前端缺陷**。

### 后端逻辑链路

- **数据模型**：
  - `biz_change_visa`（`zw-contract/.../domain/BizChangeVisa.java`）：`status` DRAFT/SUBMITTED/APPROVED/REJECTED；`changeAmount`、`workflowInstanceId`。
  - `biz_change_event`（`zw-contract/.../domain/BizChangeEvent.java`）：状态机 `ChangeEventStatus`（`domain/ChangeEventStatus.java`）= DRAFT→ASSESSING→APPROVING→APPROVED/REJECTED/CANCELLED。
  - 目标回写字段：`biz_construction_contract.cumulative_change_amount`。
- **状态机**：
  - **变更签证（走 BPMN）**：`ChangeVisaService.save:51` 置 DRAFT（**硬编码**，忽略请求体 status）→ `submit:60` 仅 DRAFT/REJECTED 可提 `:65`，起 `change_visa_approval`（单级，`processes/change_visa_approval.bpmn20.xml:17` `assignee=${initiator}`）→ `onApproved:87` 幂等 `:93` → 置 `APPROVED:98` → **回写合同累计变更额** `contract.setCumulativeChangeAmount(+changeAmount):108`（`contractMapper.updateById:109`）。
  - **变更事件（不走 BPMN，直改状态）**：`ChangeEventService.create` 置 DRAFT → `startAssessment` 置 ASSESSING → `submitAssessment`（`:295` 置 APPROVING，`validateCostDetails` 校验「成本影响≠0 必须指定账户」）→ `approve:316` 置 APPROVED 并 `outboxRecorder.record(CHANGE_EVENT_APPROVED, buildApprovedPayload(event)):330`。
- **API（双向核对一致）**：
  - 变更签证：后端 `ChangeVisaController.java:15` 基路径 `/api/v1/contract/change-visa`（`GET:23` / `POST:34` / `POST /{id}/submit:39`）↔ 前端 `src/api/contract.ts:54-64`（`submitChangeVisa` 已定义）。
  - 变更事件：后端 `ChangeEventController.java:28` 基路径 `/api/v1/contract/change-event`（`/page`、`/{id}`、`POST`、`/{id}/start-assessment`、`/{id}/assessment`、`/{id}/approve` 等）↔ 前端 `src/api/change-event.ts`。
- **调用链（关键分歧）**：
  - 变更签证批准 → `ChangeVisaApprovalListener` → `ChangeVisaService.onApproved` → **写 `biz_construction_contract.cumulative_change_amount`**。
  - 变更事件批准 → **Outbox** `CHANGE_EVENT_APPROVED` → 唯一消费者 `ChangeEventApprovedCostHandler`（`zw-budget/.../handler/ChangeEventApprovedCostHandler.java:49`）→ `CostLedgerService.postBatch`（`zw-budget/.../service/CostLedgerService.java:235`）→ **只写 CBS `current_amount`**。
- **跨模块联动**：变更签证写 `zw-contract` 自己的合同表；变更事件经 Outbox 写 `zw-budget` 的 CBS（`biz_cost_account` / `biz_cost_account_txn`）。
- **校验拦截**（负向实测）：变更签证非草稿重提交 → `code 500`「仅草稿或已驳回状态可提交」；变更事件评估「成本影响≠0 无账户明细」→ `code 500`「成本影响不为 0 时必须指定受影响的成本账户」。

#### ⚠ 断裂点 #1 复验：双轨未打通（成立）

1. **`ChangeEventApprovedCostHandler` 的 javadoc 声称的「zw-contract 侧处理器」不存在**（复验成立）：
   - 全仓 `implements OutboxEventHandler` 的**唯一实现**是 `ChangeEventApprovedCostHandler`（`zw-budget`，`grep -rln "implements OutboxEventHandler" zw-insight-server` 仅返回该文件 + 一个测试）。
   - 且事件契约 `ChangeEventApprovedEvent`（`zw-common/.../event/contract/ChangeEventApprovedEvent.java`）**根本不含 `contractId`** → 即使有处理器也无从定位合同。`buildApprovedPayload`（`ChangeEventService.java:467-495`）只填 `eventId/projectId/tenantId/costDelta/affectedAccounts/affectedWbsIds`。
   - `ChangeEventService.approve` 的 javadoc（`:307-313`）与 handler 的类注释（`:20-29`）均声称有「② 回写合同累计变更金额」的下游处理器——**与代码不符（失实声称）**。
2. **两条路径各自回写什么（实测）**：
   - 变更签证批准 → `contract.cumulative_change_amount`：**0 → 50000**（`[9F-contract-after-visa] cumulativeChangeAmount: 50000`）。
   - 变更事件批准（costDelta=30000，指明账户）→ `contract.cumulative_change_amount`：**保持 50000 不变**（`[F5-after-approve] contractCumChange: 50000`）→ 证明变更事件**不会**回写合同累计变更额。
3. **对阶段 8 产值上限的影响（跨批次关键）**：`OutputReportService.validateOutputLimit:272` 的判定式为
   `maxOutput = contractAmount + cumulativeChangeAmount`（`:273-274`）。由于 `cumulativeChangeAmount` **只由变更签证回写**、变更事件不回写，**变更事件批准的金额永远不会抬高产值上限** → 现场通过「变更事件」登记的增量在阶段 8 报产值时会被上限拦截。

#### ⚠ 新发现缺陷 A（P1，运行时）：变更事件→CBS 传导在真机崩溃

- 实测：变更事件 `approve` 返回 200、状态 `APPROVED`，Outbox 记录 `CHANGE_EVENT_APPROVED`（payload 正确含 `accountId`），但消费者**抛 `MyBatisSystemException`**：
  - `sys_outbox_event`：`event_type=CHANGE_EVENT_APPROVED, status=PENDING, attempts=0→1→2, last_error=MyBatisSystemException`（重试退避 120s，**永不投递成功**）。
  - 后果：`biz_cost_account.current_amount` 保持 `100000` 不变（期望 `130000`）；`biz_cost_account_txn` **无流水**。
- **根因未在日志中暴露（可诊断性缺陷）**：`OutboxDispatcher.deliver` 的 catch（`OutboxDispatcher.java:183-188`）只 `log.warn("…error={}", msg)`，而 `MyBatisSystemException` 的 `getMessage()` 为 `null` → 落库/日志只剩类名 `MyBatisSystemException`，**完整栈从不输出**（已 grep 全量 `docker logs zwi-backend`，无该异常栈）。这是「失败被记录但无法定位」的静默失效模式。
- 佐证：`biz_cost_account_txn` 全表 50 行**全部来自种子**（`source_type='SEED'`，V2026_66），**无任何运行时写入**；旁路 `CostRollUpService.rollup` 亦报 `Unknown column 'settlement_code'`（`CostRollUpService.java:140`，CBS 子系统存在 schema 漂移，另案）。

#### ⚠ 新发现缺陷 B（P2）：未注册事件类型无限重试、无死信

- `ChangeEventService` 为每次状态跃迁都投递 Outbox（CREATED/ASSESSING/UPDATED/ASSESSED/APPROVED），但只有 APPROVED 有处理器 → 其余 4 类恒 `last_error=未注册事件类型 [...] 的处理器`、`status=PENDING`、`attempts` 递增，**无限重试且不落死信**（`OutboxDispatcher.java:157-163` 记为失败重试）。我的 1 个变更事件即产生 14 条 Outbox 行。

### 前端操作流程

**（B-1）变更事件** `/contract/change-event`（`router/index.ts:235-238` → `views/contract/change-event/index.vue`）：

1. 实测点击序列：选项目 → 「登记变更事件」→ 填标题/详细描述 → 「登记」→ 行「草稿」→ 「转评估」→ 行「评估中」→ 「详情」。
2. 实际请求：`POST /v1/contract/change-event → 200 code 200`；`POST /{id}/start-assessment → 200`。
3. **状态回显 / 阻断**：点「详情」打开抽屉（`ChangeEventDetailDrawer`）→ **抽屉内容为「变更事件不存在：2109581984234782700」**，无「填写影响评估」按钮（count=0）。
4. **根因（前端精度丢失）**：抽屉/编辑弹窗把列表行的字符串 ID 转成 JS `Number`：
   - `views/contract/change-event/detail-drawer.vue:270` `loadEventData(Number(id))`（prop 类型 `eventId?: number`，`:211`）；
   - `views/contract/change-event/form-modal.vue:276` `await loadEvent(Number(id))`（prop 类型 `eventId?: number`，`:207`）。
   - 雪花 ID `2109580940821970946`（DB 实测）经 `Number()` 变为 `2109580940821971000`（末位被四舍五入）→ 后端 `getById` 抛「变更事件不存在」。**凡 ID 超过 `Number.MAX_SAFE_INTEGER` 的真实单据，详情/编辑一律打不开**。
5. **后果**：`APPROVING` 只能由「填写影响评估」达成，而该入口必须经详情/编辑弹窗（两者均坏）→ **变更事件在 UI 上永远到不了 `APPROVING`，也就永远点不到「批准」**。列表级「转评估」按钮因直接透传 `row.id`（字符串）故可成功，恰形成「能登记、能转评估、卡在评估」的假象。
- **判定：不通**（登记、转评估可用；评估→批准不可达）
  - 证据：`[9F-1] create change-event -> 200 200`、`[9F-1] start-assessment -> 200`、`[9F-1] detail drawer text = 变更事件详情 变更事件不存在：2109581984234782700`、`[9F-1] detail drawer broken (precision loss) = true`、`填写影响评估 button count = 0`；DB 实证真实 id `…946`（≠ 请求的 `…000`）。

**（B-2）变更签证** 无独立菜单；入口在「合同管理 > 施工合同」列表行「全景视窗」→ 抽屉「变更历程与签证」页签（`views/contract/components/ContractDetailDrawer.vue:118-156`）：

1. 实测点击序列：`/contract/list` → 行「全景视窗」→ 页签「变更历程与签证」→ 「登记变更签证」（仅 `contract.status==='EFFECTIVE'` 显示，`:122`）→ 填金额/原因/内容 → 「确认提交」。
2. 实际请求：`POST /v1/contract/change-visa → 200 code 200`。
3. **状态回显（口径不一致）**：前端 `submitVisa`（`ContractDetailDrawer.vue:378-386`）在请求体里显式传 `status: 'APPROVED'`，但后端 `ChangeVisaService.save:52` **硬编码 `setStatus("DRAFT")`** 覆盖之 → 列表标签显示「**编制中**」而非「已批准」。
4. **边界（致命缺口）**：该页签内**无「提交」按钮**（实测 `抽屉内提交按钮数 = 0`），全仓 `submitChangeVisa` 除单测外**无任何调用点** → **变更签证无法从 UI 提交审批** → 合同累计变更额永远不会因 UI 操作而回写。
- **判定：不通**（登记可用；提交审批不可达，状态回显与预期不符）
  - 证据：`[9F-2] POST /contract/change-visa -> 200 200`、`[9F-2] 变更签证状态标签 = 编制中`、`[9F-2] 抽屉内提交按钮数 = 0`。

### 断裂点

| # | 严重度 | 问题 | 位置 | 实测证据 |
|---|---|---|---|---|
| F1 | **P1** | 变更事件→合同累计变更额**无处理器**（已知断裂点 #1 复验成立）；事件契约不含 `contractId`；javadoc 失实声称有该处理器 | `ChangeEventApprovedCostHandler.java:20-29`、`ChangeEventService.java:307-313/467-495`、`ChangeEventApprovedEvent.java` | 变更事件批准后 `contract.cumulativeChangeAmount` 保持 50000 不变；变更签证批准则 →50000 |
| F2 | **P1** | 变更事件→CBS 传导**运行时崩溃**（`MyBatisSystemException`），Outbox 恒 PENDING、CBS 未更新、无流水 | `ChangeEventApprovedCostHandler.handle:75` → `CostLedgerService.postBatch:235` | `sys_outbox_event` attempts 0→1→2、`last_error=MyBatisSystemException`；`biz_cost_account.current_amount` 保持 100000 |
| F3 | **P1** | 前端变更事件详情/编辑 `Number(id)` 精度丢失 → **打不开任何真实单据**，评估→批准链路不可达 | `change-event/detail-drawer.vue:270`、`form-modal.vue:276`（prop 类型 `eventId?: number`） | 抽屉「变更事件不存在：…000」；DB 真实 id `…946` |
| F4 | **P2** | 变更签证 UI 无「提交审批」入口；`submitChangeVisa` 无调用点；且前端传 `status:'APPROVED'` 被后端强制回 DRAFT | `ContractDetailDrawer.vue:374-395`、`ChangeVisaService.java:52` | 抽屉内提交按钮数 = 0；状态标签「编制中」 |
| F5 | **P2** | Outbox 未注册事件类型无限重试、无死信 | `OutboxDispatcher.java:157-163` | 14 条 Outbox 行恒 PENDING、`未注册事件类型[…]的处理器` |
| F6 | **P2** | Outbox 失败仅记类名、不打栈 → 可诊断性缺失（掩盖 F2 根因） | `OutboxDispatcher.java:183-188` | `last_error=MyBatisSystemException`，全量日志无异常栈 |

---

## 阶段 9G：质保金

### 后端逻辑链路

- **数据模型**：`biz_retention_money`（`zw-finance/.../domain/BizRetentionMoney.java`）：`status` ACTIVE/RETURNED（`RetentionMoneyService.getExpiring:67` 按 ACTIVE 筛）、`retentionAmount`/`returnedAmount`/`expireDate`；`biz_retention_return`：`status` DRAFT/SUBMITTED/APPROVED/REJECTED。
- **状态机**：
  1. 登记：`RetentionMoneyService.save:41` → 金额 `>0` 校验 `:43`；`expireDate = startDate + retentionPeriod 月` `:48-49`；`returnedAmount` 默认 0、`status` 默认 ACTIVE `:51-56`。
  2. 到期查询：`getExpiring(days):63`（ACTIVE 且 `now ≤ expireDate ≤ now+days`）；`getOverdue():78`（ACTIVE 且 `expireDate < now`，与 `RetentionWarningTask` 同口径）。
  3. 返还申请：`RetentionReturnService.save:37` 置 DRAFT → `submit:46` 仅 DRAFT/REJECTED `:51`、金额 `>0` `:62`、**上限校验** `validateReturnAmount:65`（`returnAmount > retentionAmount − returnedAmount` 则拒 `:153`）→ 起 `retention_return_approval`（单级，`processes/retention_return_approval.bpmn20.xml:17`）。
  4. 审批通过：`onApproved:85` 幂等 `:91` → **生效前复校** `validateReturnAmount:101` → 置 APPROVED `:103` → `returnedAmount += returnAmount` `:112` → `returnedAmount ≥ retentionAmount` 则置 `RETURNED:115` → 全额返还联动清理预警去重 key `retentionWarningTask.onRetentionReturned:123`。
- **API（双向核对一致）**：后端 `RetentionController.java:24` 基路径 `/api/v1/finance/retention`（`GET /page:35`、`POST:47`、`GET /expiring:56`、`GET /overdue:65`、`POST /return:73`、`POST /return/{id}/submit:82`）↔ 前端 `src/api/finance.ts:197-221`。
- **调用链**：`RetentionReturnApprovalListener` → `RetentionReturnService.onApproved`；预警 `RetentionWarningTask`（定时任务，经 `UrgeNotifyEvent` 落站内信）。
- **校验拦截**（负向实测被拦截）：超额返还草稿允许创建，`submit` 被拒 → `code 500`「返还金额不能超过剩余可返还金额：96000.00」。
- **判定：通**
  - 证据（API 真机）：登记 → `status=ACTIVE expireDate=<today> returnedAmount=0`；`GET /expiring?days=30` 包含该条（`contains=true`）；超额 `submit` → 500 拒绝；全额 96000 `submit` → 逐级 `complete`（1 级）→ `status=RETURNED returnedAmount=96000`。

### 前端操作流程

1. **入口**：菜单「财务管理 > 质保金管理」→ `/finance/retention`（`src/router/index.ts:345-348` → `views/finance/retention.vue`）。
2. **实测点击序列**：进页面 → 「新增质保金」→ 选项目 → 填质保金金额 6000 → 「确定」→ 行（标签「有效」）→ 「返还」→ 填返还金额 1000 + 返还日期 → 「确定」。
3. **实际请求**：`POST /v1/finance/retention → 200 code 200`；`POST /v1/finance/retention/return → 200 code 200`。
4. **状态回显**：列表新行标签「有效」，`已返还` 列显示 `-`（返还单为草稿，未审批故 `returnedAmount` 未变，正确）。
5. **边界（缺口）**：`retention.vue:259-275` 的「返还」只调用 `createRetentionReturn`（登记草稿），**页面无「提交返还审批」按钮、无返还单列表**；`submitRetentionReturn`（`api/finance.ts:219`）除单测外无调用点 → **UI 无法把返还单提交审批** → `RETURNED` 只能靠 API/L4 达成。
- **判定：部分通**
  - 证据：`[9G-1] create retention -> 200 200`、`[9G-1] POST /finance/retention/return -> 200 200`、`[9G-1] 页面存在返还提交审批入口 = false`。

### 断裂点

- **P2-1（前端缺审批入口）**：`retention.vue` 只有登记返还，无提交审批与返还单查询 → UI 闭环不可达（后端 `POST /return/{id}/submit` 存在但无 UI 调用）。建议补「返还单列表 + 提交审批」或复用审批中心。
- **P3-2（口径提示）**：`retention.vue:97-105` 的「质保金比例/质保期(月)/到期日期」为非必填且后端 `save` 会按 `startDate+period` 重算 `expireDate`，前端手填 `expireDate` 会被后端覆盖（易误解），建议前端提示。

---

## 按严重度排序的修复清单

| # | 严重度 | 问题 | 位置 | 建议修复 | 影响阶段 |
|---|---|---|---|---|---|
| 1 | **P1** | 变更事件→合同累计变更额**无处理器**（断裂点 #1 成立）；事件契约不含 `contractId` | `ChangeEventApprovedCostHandler.java:20-29`（失实 javadoc）、`ChangeEventService.java:307-313/467-495`、`ChangeEventApprovedEvent.java` | 在 `zw-contract` 新增 `ChangeEventApprovedContractHandler implements OutboxEventHandler<ChangeEventApprovedEvent>`（按 `projectId` + 受影响合同映射回写 `cumulative_change_amount`），并给契约补 `contractId`；同步删除 javadoc 的失实声称 | 9F → 8 |
| 2 | **P1** | 变更事件→CBS 传导运行时崩溃（`MyBatisSystemException`），CBS 未更新、无流水 | `ChangeEventApprovedCostHandler.handle:75` → `CostLedgerService.postBatch:235` | 抓完整异常栈定位（先修 #4 的诊断）；核对 `CostLedgerService.post` 在调度线程（无登录上下文）下经 `TenantLine/DataPermission` 拦截器与 `@Transactional` 的交互 | 9F |
| 3 | **P1** | 前端变更事件详情/编辑 `Number(id)` 精度丢失 → 打不开真实单据，评估→批准不可达 | `change-event/detail-drawer.vue:270`、`change-event/form-modal.vue:276`（prop 类型 `eventId?: number`） | 移除 `Number(id)`，ID 全程按字符串透传（`eventId?: number\|string`）；同类页面全量排查 `Number(id)`/`parseInt(id)` 用法 | 9F |
| 4 | **P2** | Outbox 失败仅记类名、不打栈 → 掩盖 #2 根因 | `OutboxDispatcher.java:183-188` | `log.warn(..., e)`（带栈）或至少记录 `e.getCause()` | 横切 |
| 5 | **P2** | 变更签证 UI 无提交审批入口；`submitChangeVisa` 无调用点；前端传 `status:'APPROVED'` 被后端强制回 DRAFT | `ContractDetailDrawer.vue:374-395`、`ChangeVisaService.java:52` | 抽屉加「提交审批」按钮调用 `submitChangeVisa`；去掉前端无意义的 `status:'APPROVED'`；按状态渲染标签（DRAFT/SUBMITTED/APPROVED） | 9F |
| 6 | **P2** | 质保金 UI 无「提交返还审批」入口 → RETURNED 不可达 | `retention.vue:259-275`、`api/finance.ts:219` | 补返还单列表 + 提交审批按钮 | 9G |
| 7 | **P2** | Outbox 未注册事件类型无限重试、无死信 | `OutboxDispatcher.java:157-163` | 未注册类型应直接进死信（`max_attempts` 后 FAILED），或只对「已注册但处理失败」重试 | 横切 |
| 8 | **P3** | 付款申请支付态筛选缺 `PARTIAL_PAID` | `payment-apply.vue:28-33` ↔ `BizPaymentApply.java:31` | 补 `PARTIAL_PAID` 选项 | 9D |
| 9 | **P3** | 付款/变更签证 BPMN 各级 `assignee=${initiator}` → 自审 | `payment_apply_approval.bpmn20.xml:17/26/43/59`、`change_visa_approval.bpmn20.xml:17` | 改用 `candidateGroups`（如 `PROJECT_MANAGER`→`FINANCE_STAFF`），与 `project_close_approval` 一致 | 9D/9F |
| 10 | **P3** | `CostRollUpService.rollup` 报 `Unknown column 'settlement_code'`（CBS schema 漂移，旁路发现） | `CostRollUpService.java:140` | 核对归集查询列与库结构 | 横切 |

---

## 附录：动态验证证据索引

### A. 真机 API 链（Node `fetch`，tenant 1）

| 证据 | 命令/入口 | 关键输出 |
|---|---|---|
| 9D 付款闭环（OTHER_EXPENSE 165000） | 自定义 Node 探针 | `POST /finance/payment-apply → 200`；`submit → 200`；快照 `cumulativeSettlementSnapshot=200000 / unpaidAmountSnapshot=200000`；2 级 `complete` 后 `status=APPROVED payStatus=UNPAID`；`contract.cumulativePaid=165000`；`project.totalExpense=165000` |
| 9D 双口径复验 | 同上 | 审批通过只写 `total_expense`；`pay_status` 保持 `UNPAID`（未回写项目账） |
| 9D 金额分级 | 探针3（500000 付款） | `sys_amount_tier_config` tier=3 → 任务链 `项目负责人审批→财务审核→财务负责人审批`（`ACT_HI_TASKINST` 3 行）；`status=APPROVED`、`totalExpense=500000` |
| 9D 负向 | 探针1 | 超付 → `code 500`「…最大可付金额：35000.00」；金额 0 → `code 500`「付款金额必须大于0」 |
| 9E 路径 A | 探针1 | `submit→200`；`reject-start→200`；`status=REJECTED`；重审 `complete`→`APPROVED` |
| 9E 路径 B | 探针1 | `submit→200`；`terminate→200`；`status=REJECTED`；重提 `submit→200`；`complete`→`APPROVED` |
| 9F 变更签证 | 探针1 | `POST→200`（DRAFT）→`submit→200`→1 级 `complete`→`APPROVED`；`contract.cumulativeChangeAmount=50000`；非草稿重提交 → `code 500`「仅草稿或已驳回状态可提交」 |
| 9F 变更事件双轨 | 探针1 + 探针2b | `approve→200 status=APPROVED`；Outbox `CHANGE_EVENT_APPROVED` 存在；`contract.cumulativeChangeAmount` **保持 50000**（不回写）；`biz_cost_account.current_amount` **保持 100000**（CBS 未更新）；`biz_cost_account_txn` 空 |
| 9F 缺陷 A 复现 | 探针2b + 后端日志 | `sys_outbox_event`：`status=PENDING, attempts=0→1→2, last_error=MyBatisSystemException`；`OutboxDispatcher` WARN「发件箱事件投递失败…error=MyBatisSystemException」（无栈） |
| 9F 缺陷 B | 探针2b | 14 条 Outbox 行恒 PENDING，`last_error=未注册事件类型 [CHANGE_EVENT_CREATED/ASSESSING/UPDATED/ASSESSED] 的处理器` |
| 9G 质保金 | 探针1 | `POST /finance/retention→200 status=ACTIVE expireDate=today`；`expiring?days=30` 含该条；超额 `submit→500`「…剩余可返还金额：96000.00」；全额 `submit→200`→1 级 `complete`→`status=RETURNED returnedAmount=96000` |

### B. 真机 UI 抓包（Playwright 无头，`--project=e2e-real --no-deps --workers=1`，`e2e/tests/real/_b5-ui.spec.ts`）

| 用例 | 结果 | 关键抓包/断言 |
|---|---|---|
| 9D-1 表单必填校验 | PASS | `inline errors = 6, posted = 0` |
| 9D-2 新增→提交 | PASS | `POST /finance/payment-apply -> 200 200`；`submit -> 200 200`；状态「审批中」；详情抽屉回显 `付款金额 1,000.00 / 累计结算快照 100,000.00 / 状态 审批中` |
| 9E-1 退回发起人 | PASS | 抽屉「单据ID」= 我的付款 ID；`reject-start -> 200 200`；`status=REJECTED` |
| 9E-2 终止流程 | PASS | `terminate -> 200 200`；`status=REJECTED` |
| 9F-1 变更事件 | PASS（记录缺陷） | `create -> 200`；`start-assessment -> 200`；详情抽屉「变更事件不存在：2109581984234782700」；`填写影响评估 button count = 0` |
| 9F-2 变更签证 | PASS（记录缺陷） | `POST /contract/change-visa -> 200`；状态标签「编制中」；`抽屉内提交按钮数 = 0` |
| 9G-1 质保金 | PASS（记录缺陷） | `retention -> 200`；`return -> 200`；`页面存在返还提交审批入口 = false` |

### C. 后端日志 / 直连库证据

| 证据 | 命令 | 关键输出 |
|---|---|---|
| 变更事件真实 id | `SELECT id FROM biz_change_event WHERE title LIKE 'E2E_TEST_B5_UI%'` | `2109580940821970946`（≠ 前端请求的 `…000`） |
| CBS 流水仅种子 | `SELECT source_type, COUNT(*) FROM biz_cost_account_txn` | 50 行全为 `SEED`（无运行时写入） |
| 金额档位 | `SELECT module,min_amount,max_amount,tier_level FROM sys_amount_tier_config` | PAYMENT_APPLY tier1 `0-10万` / tier2 `10-50万` / tier3 `50-500万` / tier4 `>500万` |
| `pay_status` 默认 | `SHOW COLUMNS FROM biz_payment_apply LIKE 'pay_status'` | `varchar(20) NOT NULL DEFAULT 'UNPAID'` |
| 已知断裂点 #1 静态 | `grep -rln "implements OutboxEventHandler" zw-insight-server` | 仅 `ChangeEventApprovedCostHandler.java`（+1 测试） |

### D. 数据卫生（收尾自查）

- ✅ 业务行：本批次所有 `E2E_TEST_B5_` 前缀数据（`biz_project` / `biz_other_contract` / `biz_construction_contract` / `biz_purchase_contract` / `biz_payment_apply` / `biz_change_visa` / `biz_change_event` / `biz_retention_money` / `biz_retention_return` / `biz_cost_account` / `biz_cost_account_txn` / `sys_outbox_event` / 成员 / 状态流水）**已全部删除，复验计数为 0**。
- ✅ Flowable：本批次创建的付款流程**均经应用层 `terminate` 收口**（UI 用例 afterAll 对 `businessId` 命中者逐个 terminate），未直删 `ACT_*`；复核 `ACT_RU_TASK` 无本批次 businessKey 的运行态残留。
- ✅ 未动 90001-90004 种子；临时探针/截图/临时 spec 收尾删除；`git status` 见附录 E。

### E. 未能验证 / 受阻项

| # | 项 | 原因 | 处置 |
|---|---|---|---|
| 1 | 9D 跨模块付款路由（`contractCategory=PURCHASE` → 写 `biz_purchase_contract.cumulative_paid`）**未动态验证** | 自建采购合同被 `@BudgetCheck` 拦（`该科目未设置预算额度`）；本批次为验收不改生产数据，未绕开预算 | 静态证据充分（`PaymentApplyService.resolvePayable:601-616` / `addCumulativePaid:621-635` 按 `contractCategory` switch）；L4 `stage_9d` 覆盖该路径。如实标注为「静态通、动态未证」 |
| 2 | 9F 变更事件→CBS 的**根因栈**未取得 | `OutboxDispatcher` 只记 `e.getMessage()`（null → 类名），不打栈；无法从日志还原 | 已作为缺陷 F2 + 可诊断性缺陷 F6 记录 |
| 3 | 9F 变更事件前端「批准」步骤未实测 | 详情/编辑入口被 `Number(id)` 缺陷阻断，UI 无法进入评估填写 → 到不了 APPROVING | 已作为缺陷 F3 记录（这本身即验收结论） |
| 4 | token 稳定性 | 并行批次反复重登 admin（`max-devices=5`），`keys/_tok` 中途失效 | 改用 `e2e/.auth/storage-state.json` 的 token 完成后续验证；未自行重登 |
