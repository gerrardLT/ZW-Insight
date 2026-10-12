# 全链路验收报告 · 批次 4（阶段 8 / 9 / 9B / 9C）

> 验收日期：2026-10-12
> 环境：主环境 `129.204.3.200`（tenant 1 演示租户；后端 :18080 / 前端 :18081，均真机实测）
> 权威定义：`keys/lifecycle-sim-v2.sh` 的 `stage_8_output_settlement` / `stage_9_finance` / `stage_9b_completion_settlement` / `stage_9c_purchase_settlement`
> 方法：静态（`file:line`）+ 动态（真机 API 响应摘要、真机无头 Playwright 抓包）
> 纪律遵守：全程 tenant 1；未动 90001-90004 种子；未本地起 JVM/mvn；**未直删任何 `ACT_*` 表**（唯一一条 a3 卡死待办走应用层 `terminate` 收口）；所有写用例 `E2E_TEST_B4_` 前缀，收尾已删净
> 夹具：自建 `E2E_TEST_B4_CHAIN_20261012091136`（项目 → FILED → 施工合同 100 万 EFFECTIVE → 采购合同 30 万 EFFECTIVE → 入库单 25 万 APPROVED）+ `E2E_TEST_B4_UI_20261012092042`（项目 + 50 万 EFFECTIVE 合同，供 UI 新增产值用）

---

## 汇总

| 阶段 | 后端链路 | 前端流程 | 关键缺口 |
|---|---|---|---|
| 8 产值结算 | **通** | **通** | 上限含变更额逻辑正确，但 `cumulative_change_amount` 只由变更签证回写（变更事件不回写）→ 变更事件批出的额度不计入产值上限（跨批次 #1） |
| 9 财务收付 | **部分通** | **通** | 开票额度口径正确（=累计产值−已开票）；回款登记即 CLAIMED、FIFO 核销、双写应收台账全部实测成立；**收票登记回写只认 `biz_other_contract`**，对施工/采购合同静默不写（P2）；`biz_invoice_apply` 三个快照字段是**死字段**（P3） |
| 9B 竣工验收+最终结算 | **通**（API） | **不通**（竣工验收**无任何前端入口**） | 竣工验收无页面/路由/菜单，`src/api/site.ts` 三个函数是死代码 → 用户无法从 UI 把项目置 COMPLETED；项目最终结算 UI 正常但只能对已 COMPLETED 的项目使用（P1 前端缺口） |
| 9C 采购结算 | **通** | **通** | 重复结算/超入库额/无入库单/非草稿重提四类负向均被拦；但 `submit` 并非「直接 APPROVED」（任务书与类注释过时），实为 BPMN 两级审批（P3 文档） |

**批次结论**：4 个阶段的**后端主链全部跑通并产生正确数据与状态**（含超额负向、重复结算负向、FIFO 核销、双写不变量），无阻断级缺陷。真实缺口集中在**前端入口缺失（9B 竣工验收）**与**跨模块回写不全（收票登记 / 变更额）**。批次内无 P0。

---

## 阶段 8：产值结算

### 后端逻辑链路

- **数据模型**：`biz_output_report`（`zw-insight-server/zw-contract/src/main/java/com/zwinsight/contract/domain/BizOutputReport.java`：`status`/`currentOutput`/`cumulativeOutput`/`contractId`/`projectId`/`reportPeriod`/`workflowInstanceId`）；明细 `biz_output_report_detail`（`reportId`/`boqItemId`/`quantity`/`amount`）。状态枚举 `DRAFT → SUBMITTED → APPROVED/REJECTED`。
- **状态机**：
  - `DRAFT`：`OutputReportService.save:69`（`@Transactional`），硬置位 `:85`，金额守卫 `:71-73`。
  - `SUBMITTED`：`OutputReportService.submit:95`（守卫仅 `DRAFT/REJECTED` `:100-102`）→ 上限校验 `:108` → 起流程 `:114-115` → 置 `SUBMITTED:118`。
  - `APPROVED`：审批完成回调 `OutputReportService.onApproved:127`（`OutputReportApprovalListener`）→ 生效前**二次校验上限** `:153` → 原子累加合同 `:164` → 累加项目 `:171` → 回写 BOQ 完成量 `:177-182` → 状态 CAS `:185-192`。
  - `REJECTED`：`onRejected:204`（仅 `SUBMITTED` 可回退）。
- **API（双向核对一致）**：基路径 `/api/v1/contract/output`（`OutputReportController.java:15`）
  - `GET /api/v1/contract/output`（`:22`，`page/size/projectId/contractId`）↔ `src/api/contract.ts:137-138` `getOutputReportPage`（传 `page/size`）
  - `POST`（`:31`）↔ `contract.ts:141-142`；`POST /{id}/submit`（`:38`）↔ `contract.ts:145-146`；`DELETE /{id}`（`:45`）↔ `contract.ts:150-151`
  - **无 `GET /{id}`**（前端未使用）。
- **调用链**：`submit` → `ApprovalService.startProcess("OUTPUT_REPORT", …, "output_report_approval")` → BPMN 两级 → 流程完成 → `ProcessCompleteListener` → `OutputReportApprovalListener` → `onApproved`。
- **跨模块联动（本阶段最易断处）**：**写别的模块的表**——`biz_construction_contract.cumulative_output`（`BizConstructionContractMapper.addCumulativeOutputWithinLimit`，经 `OutputReportService:164`）、`biz_project.cumulative_output`（`BizProjectMapper.addCumulativeOutput`，`:171`）、`biz_boq_item.completed_quantity`（`BizBoqItemMapper.addCompletedQuantity`，`:179`）。删除时对称冲销 `delete:238-251`。
- **校验拦截（红线判定式）**：`validateOutputLimit:272-281` —— `maxOutput = contractAmount + cumulativeChangeAmount`；`newCumulative = cumulativeOutput + currentOutput`；`newCumulative > maxOutput` 即拒。负向实测全部命中（见下）。
- **判定：通**
  - 证据（真机）：`POST /contract/output {currentOutput:600000}` → `code 200`；`submit` → 200；两级审批完成 → `GET /contract/{id}.cumulativeOutput = 600000`。
  - **超额负向**：累计已达 60 万后再报 50 万 → `submit` 返回 `code 500`「累计产值不能超过合同金额（含变更），当前上限：**1000000.00**」（=合同额 100 万 + 变更额 0，判定式成立）→ 真被拦。
  - 其余负向：`currentOutput=-100` → 500「本期产值必须大于0」；`projectId` 与合同不匹配 → 500「施工合同不属于该项目」。
  - 续报 40 万 → 累计 100 万；`biz_project.cumulative_output = 1000000`（`total_income` 仍为 0，符合「产值≠收款」口径）。

### 前端操作流程

1. **入口**：菜单「合同管理 > 产值上报」→ 路由 `/contract/output-report`（`src/router/index.ts:229-232` → `src/views/contract/output-report.vue`）。
2. **实测点击序列**（无头 Playwright，真服务器；自建 spec，9/9 通过）：进页面 → 查询项目下拉（**该下拉 `default-first`，挂载即选中首个项目**，`output-report.vue:10`，需显式改选本项目）→ 选本项目 → 列表出现「2026-08 600,000.00 已通过」→ 点「新增产值上报」→ 弹窗 → 直接点「保存草稿」→ 客户端校验报错、**未发任何请求** → 选合同 + 报告期间 + 本期产值 → 「保存草稿」→ **`POST /api/v1/contract/output` HTTP 200**。
3. **状态回显**：列表正确显示 `本期产值 600,000.00 / 累计产值 1,000,000.00 / 已通过`（`formatMoney` → `600,000.00`）。
4. **边界**：必填规则 `output-report.vue:260-265`（项目/合同/报告期间/本期产值 min 0.01）；空表提交被客户端拦截（实测 `.el-form-item__error` 出现且 POST 计数 0）。填报方式支持「按清单行 / 纯金额」（`:91-125`）。
- **判定：通**
  - 备注（非缺陷）：列表页项目下拉 `default-first` 会先选中「列表第一个项目」（本次为其它会话的 `E2E_TEST_B2_UI_项目_*`），用户若不改选会看到「暂无数据」，易误判。建议改为「不默认选中」或默认选中「我参与的项目」。

### 断裂点
- P3-8-1（跨批次引用）：产值上限的 `cumulativeChangeAmount` **只由变更签证回写**，而变更事件批准只写 CBS（任务书 §七 已知断裂点 #1）。故「变更事件」批出的额度不计入产值上限——若业务以变更事件为准，产值会被少放额度。本批次实测上限=合同额+0，与该结论一致。
- P3-8-2：列表项目下拉 `default-first`（`output-report.vue:10`）选中首个项目而非「我的项目」，首屏体验易误导。

---

## 阶段 9：财务收付（开票申请 / 收票登记 / 回款登记 / 应收台账）

### 后端逻辑链路

- **数据模型**：`biz_invoice_apply`（`BizInvoiceApply.java`，含 3 个快照字段 `:52/:55/:58`）、`biz_invoice_received`（`BizInvoiceReceived.java`）、`biz_payment_received`（`claimStatus`/`status`）、`biz_receivable` + `biz_receivable_write_off`（`ReceivableService` 类注释）。
- **状态机**：
  - 开票：`DRAFT`（`InvoiceApplyService.save:86`）→ `SUBMITTED`（`submit:134`，额度校验 `:148`）→ `APPROVED`（`onApproved:167`，`addCumulativeInvoiceAmount:199`）；`REJECTED`（`:208`）。
  - 回款：`save:60` **一步到 `status=APPROVED` + `claimStatus=CLAIMED`**（`:87-93`），无「待认领」中间态；`claim:137` 仅兼容存量 `UNCLAIMED`；`writeOff:154` 为纯文档状态机。
  - 收票：`save:48` 直接 `status=APPROVED`。
- **API（双向核对一致）**：
  - 开票：`/api/v1/finance/invoice-apply`（`InvoiceApplyController.java:19`）`GET /page`(`:26`)/`GET /{id}`(`:36`)/`POST`(`:41`)/`PUT /{id}`(`:50`)/`DELETE /{id}`(`:58`)/`POST|PUT /{id}/submit`(`:65`) ↔ `src/api/finance.ts:24-44`（提交用 PUT，后端 POST/PUT 双收，**一致**）。
  - 收票：`/api/v1/finance/invoice-received`（`InvoiceReceivedController.java:20`）`GET`(page,`:27`)/`POST`(`:35`)/`DELETE /{id}`(`:42`)，**无 PUT**（前端亦无更新入口）↔ `finance.ts:49-53`。
  - 回款：`/api/v1/finance/payment-received`（`PaymentReceivedController.java:17`）`GET /page`/`GET /{id}`/`POST`/`PUT /{id}`/`DELETE /{id}`/`POST /{id}/claim`/`POST /{id}/write-off` ↔ `finance.ts:63-87`（逐条对齐）。
  - 应收：`/api/v1/finance/receivable`（`ReceivableController.java:32`）`GET /page`(`:42`)/`GET /aging`(`:54`)/`GET|PUT /drill/{id}`(`:66/:76`) ↔ `src/api/receivable.ts`。
- **调用链 / 跨模块联动**：
  - 开票审批通过 → `InvoiceApplyApprovalListener` → `onApproved` → 写 `biz_construction_contract.cumulative_invoice_amount`。
  - 回款 `save` → 写 `biz_project.total_income`（`:97-102`）+ `biz_construction_contract.cumulative_received_amount`（`:106-111`）+ `ReceivableService.writeOff`（`:115`）→ 写 `biz_receivable.written_off_amount`、插 `biz_receivable_write_off`、`projectMapper.addReceivableAmount(负)`（`ReceivableService:151/158/164`）。
  - **收票 `save` → 只写 `biz_other_contract.cumulative_invoice`**（`InvoiceReceivedService:64-72`，`otherContractMapper`），**对施工/采购合同不写**（见断裂点）。
- **校验拦截**：
  - 开票额度 `validateInvoiceLimit:225-235`：`maxInvoice = cumulativeOutput − cumulativeInvoiceAmount`（**不看合同额、不看收款**）——实测负向文案「最大可开票金额：0.00」。
  - 回款上限 `PaymentReceivedService:75-84`：`max = cumulativeInvoiceAmount − cumulativeReceivedAmount`；负/零金额 `:66-68`。
  - 应收核销上限：`ReceivableService.writeOff:138-170`，超出 OPEN 余额部分不冲减、记日志不静默。
- **判定：部分通**
  - 证据（真机）：开票 100 万 → 审批通过 → `contract.cumulativeInvoiceAmount = 1000000`；再提交 100 元 → `code 500`「开票金额不能超过累计产值减已开票金额，最大可开票金额：**0.00**」（额度口径 = 产值−已开票，实测成立）。
  - 回款 40 万 → `status=APPROVED`、`claimStatus=**CLAIMED**`、`claimedBy=1`；`project.totalIncome = 400000`；`contract.cumulativeReceivedAmount = 400000`。
  - 回款 70 万 → 500「回款金额不能超过已开票未收金额，最大可回款金额：600000.00…」；回款 −100 → 500「回款金额必须大于0」。
  - **FIFO 核销 + 双写实测**：结算生成应收 60 万（OPEN）后，再登记回款 20 万 → `biz_receivable.written_off_amount = 200000`、`biz_receivable_write_off` 落明细 200000.00、`biz_project.receivable_amount` 由 600000 → **400000**（不变量「单值 = OPEN 余额合计」成立）；`aging.totalOpen = 400000`。
  - 部分通的原因：收票登记回写缺失 + 死字段（下述）。

### 前端操作流程

1. **入口**：菜单「财务管理 > 开票申请 / 收票登记 / 回款登记 / 应收台账」→ `/finance/invoice-apply`、`/finance/invoice-received`、`/finance/payment-received`、`/finance/receivable`（`src/router/index.ts:291/297/309/435`）。
2. **实测点击序列 + 抓包**（无头 Playwright，真服务器）：
   - 开票申请：列表显示「1,000,000.00」；点「新增开票申请」→ 弹窗 → 空表点「确定」→ **POST 计数 0**（客户端必填守卫有效）。
   - 收票登记：列表显示「100,000.00」。
   - 回款登记：列表显示「400,000.00」且**标签「已认领」**（CLAIMED），行内「核销」按钮**条件渲染**出现（`payment-received.vue:64-69`）。
   - 应收台账：账龄表显示本项目 + 未结清「400,000.00」（`receivable/index.vue`）。
3. **状态回显**：开票状态「已通过」、回款「已认领」、应收 OPEN 余额与后端一致。
4. **边界**：开票弹窗必填（项目/合同/开票金额/申请日期）；回款弹窗收款方式默认银行转账（既有 spec 覆盖）。
- **判定：通**

### 断裂点
- **P2-9-1（跨模块回写不全）**：`InvoiceReceivedService.save:64-72` 的收票回写**只查 `biz_other_contract`**。当 `contractId` 指向施工合同或采购合同时 `otherContractMapper.selectById` 返回 null → **静默不回写、不报错**。实测：对采购合同登记收票 10 万后，`biz_purchase_contract.cumulative_invoice_received` 仍为 **0.00**。前端收票登记弹窗的合同下拉也不区分合同类型（`invoice-received.vue`），用户可对任意合同登记却看不到任何回写效果。
- **P3-9-2（死字段）**：`biz_invoice_apply` 的 `contract_amount_snapshot` / `settlement_amount_snapshot` / `historical_invoiced_snapshot`（`BizInvoiceApply.java:52/55/58`）**全仓无任何写入方**（`grep setContractAmountSnapshot|setSettlementAmountSnapshot|setHistoricalInvoicedSnapshot` = 0 处），创建 DTO `InvoiceApplyCreateRequest` 不含这三字段，前端也不展示。实测新建两单后三列均 `NULL`。→ 与任务书预判一致，确认为死字段。
- **P3-9-3（错误语义）**：收票登记实体 `BizInvoiceReceived` 无 Bean Validation；日期字段名为 `invoiceDate`，若传 `receiveDate` 得到 **400「业务日期不可为空」**（字段名与提示不直观）。服务层仅校验金额 >0（`:52-55`），`projectId` 为 null 也可落库。
- P3-9-4（口径观察）：收入侧 `total_income` 由**回款登记**（登记即写）驱动，支出侧 `total_expense` 由**付款申请审批通过**驱动（审批口径）。二者口径不对称（一为「登记」一为「审批」），跨模块对账时需留意。

---

## 阶段 9B：竣工验收 + 最终结算

### 后端逻辑链路

- **数据模型**：`biz_completion_acceptance`（`status`：`DRAFT/SUBMITTED/APPROVED`，`workflowInstanceId`）；`biz_project_settlement`（`BizProjectSettlement`，`status`：`DRAFT/SUBMITTED/APPROVED/REJECTED`）+ `biz_settlement_contract_detail`。
- **状态机**：
  - 竣工验收：`save:48` 置 `DRAFT`；`submit:61` 起 `completion_acceptance_approval` → `SUBMITTED:77`（**不再提交即 APPROVED**，`CompletionAcceptanceService.java:54-58` 注释记录 2026-08-13 修复）；`onApproved:85` 置 `APPROVED` 并 `projectService.complete(projectId):101`（**状态机拒绝会向上抛出**，不做半成品）；`onRejected:108` 回退 `DRAFT`。
  - 项目结算：`createSettlement:67`（**守卫：项目必须 `COMPLETED`** `:73-75`；同项目仅一份进行中 `:78-84`）→ `DRAFT`；`submit:391` → `SUBMITTED`；`onApproved:429`（幂等 `:436-439`）→ 置 `APPROVED` → `receivableService.generateFromSettlement:447` → `constructionContractMapper.settleByProjectId:450`（施工合同 `EFFECTIVE→SETTLED`）。
- **API（双向核对一致）**：
  - 竣工：`/api/v1/site/completion`（`CompletionController.java:18`）`GET /page`(`:25`)/`POST`(`:33`)/`POST /{id}/submit`(`:39`) ↔ `src/api/site.ts:146-154`。**无 `GET /{id}`**。
  - 结算：`/api/v1/project-settlements`（`ProjectSettlementController.java:27`）`GET`(`:37`)/`POST ?projectId=`(`:49`)/`GET /{id}`(`:58`)/`PUT /{id}`(`:69`)/`POST /{id}/submit`(`:78`)/`POST /{id}/export`(`:87`)/`GET /{id}/unsettled-contracts`(`:98`) ↔ `src/api/settlement.ts`。
- **调用链 / 跨模块联动**：
  - 竣工验收审批通过 → `CompletionAcceptanceApprovalListener` → `onApproved` → `ProjectService.complete` → `biz_project.status = COMPLETED`（+ 实际竣工日/大事记）。
  - 结算审批通过 → 生成 `biz_receivable`（`SOURCE_SETTLEMENT`）+ 同事务 `projectMapper.addReceivableAmount`（双写）+ `biz_construction_contract.status = SETTLED`。
- **校验拦截**：结算创建要求项目 `COMPLETED`（实测对 `CONSTRUCTION` 项目会拒）；竣工验收 `submit` 仅 `DRAFT`；`createSettlement` 拒绝重复进行中结算单。
- **判定：通（后端 API 链）**
  - 证据（真机）：竣工 `POST` → `DRAFT`；`submit` → `SUBMITTED`（**中间态断言成立**，未提前 APPROVED）；重提交 → 500「仅草稿状态可提交」；审批通过 → `APPROVED` 且 `GET /project/{id}.status = COMPLETED`。
  - 结算 `POST /project-settlements?projectId=` → `DRAFT`（`constructionContractAmount 1000000`、`cumulativeOutput 1000000`、`cumulativeReceived 400000`、`cumulativeInvoiced 1000000`、`finalSettlementAmount null`、`totalIncome 1000000`、`profit 900000`）；`submit` → `SUBMITTED`；审批 → `APPROVED`；`GET /contract/{id}.status = **SETTLED**`。
  - **应收台账生成（结项前置）**：`biz_receivable` 新增 1 行 `amount 600000`（= 最终结算额（空→累计产值 100 万）− 累计收款 40 万）、`sourceType=SETTLEMENT`、`dueDate=2026-11-11`（默认账期 30 天）、`status=OPEN`；`biz_project.receivable_amount = **600000**`（双写成立）；`aging.totalOpen=600000`（NOT_DUE 桶）。

### 前端操作流程

1. **项目最终结算**：菜单「财务管理 > 项目最终结算」→ `/finance/settlement`（`src/router/index.ts:351-353` → `src/views/finance/settlement/index.vue`）。
   - 实测：列表显示本项目结算单，状态「已通过」；「创建结算单」弹窗项目下拉提示「请选择已竣工的项目」（`:96`）。
   - **判定：通（列表/回显）**；创建入口仅在项目 `COMPLETED` 时可用。
2. **竣工验收**：**无任何前端入口**。
   - 证据：全前端 `grep -rn "竣工验收\|acceptance"` 仅命中 `src/api/site.ts:144-155` 的三个导出函数（`getAcceptancePage`/`createAcceptance`/`submitAcceptance`），**无任何 view 引用它们**；`src/router/index.ts` 无 `completion`/`acceptance`/`竣工验收` 路由；`src/views/site/` 只有 `construction-log.vue`/`inspection/`/`schedule.vue`；档案模块 `src/views/archive/` 亦无验收页。
   - 后果：用户无法从 UI 创建/提交竣工验收单，**也就无法在 UI 上把项目推进到 `COMPLETED`**；进而「项目最终结算」在 UI 上只能对已 `COMPLETED` 的项目（如种子 90002/90004）使用，**自建项目走不完 9B 闭环**。
   - **判定：不通（缺入口）**

### 断裂点
- **P1-9B-1（前端缺口）**：竣工验收单无页面/路由/菜单，`src/api/site.ts:144-155` 为死代码。9B 的「竣工验收 → 项目 COMPLETED」这一半在 UI 上不可达。建议：在「现场管理」或「项目管理」下补建竣工验收页面并接三个既有 API；或明确该动作仅由移动端/外部触发（需在任务书澄清）。
- P3-9B-2：`GET /api/v1/site/completion/{id}` 缺失（`CompletionController` 无详情端点），前端不需要但 L4/第三方无法按 id 取单。
- P3-9B-3：结算单 `totalIncome` 在 `finalSettlementAmount` 为空时取「累计产值」，与应收生成口径（`ReceivableService:79-81`）一致；但 `profit` 只扣「已结算/已付」支出（`ProjectSettlementService:120-126`），若项目支出尚未结算则利润虚高（本次夹具无支出合同，`profit=900000`）。属口径设计，非缺陷，但演示时易被误解为「暴利」。

---

## 阶段 9C：采购结算

### 后端逻辑链路

- **数据模型**：`biz_purchase_settlement`（`inboundId`/`inboundAmount`/`settlementAmount`/`cumulativeSettlement`/`settlementNo`/`status`）。
- **状态机**：`create:94`（守卫：必须关联入库单 `:95-97`、金额 >0 `:98-100`、入库单存在 `:104-106`、**入库单必须 `APPROVED`** `:107-109`、入库单须关联采购合同 `:110-112`、**同一入库单只能结算一次** `:114-116`、结算额 ≤ 入库额 `:119-121`）→ `DRAFT`；`submit:196`（仅 `DRAFT`）→ 起 `purchase_settlement_approval` → `SUBMITTED:215`；`onApproved:223`（幂等 `:229-236`）→ `APPROVED` + `contractMapper.addSettlement:243`；`onRejected:253` 回退 `DRAFT`。
- **API（双向核对一致）**：`/api/v1/purchase/settlement`（`PurchaseSettlementController.java:22`）`GET /page`(`:29`)/`GET /{id}`(`:39`)/`GET /available-inbounds?contractId=`(`:47`)/`POST`(`:52`)/`PUT /{id}`(`:58`)/`DELETE /{id}`(`:65`)/`POST|PUT /{id}/submit`(`:71`) ↔ `src/api/purchase.ts:31-57`（逐条对齐）。
- **调用链 / 跨模块联动**：`onApproved` → 写 `biz_purchase_contract.cumulative_settlement`（`BizPurchaseContractMapper.addSettlement`）；`availableInbounds:76-84` 用 `settlementExistsForInbound` 把已结算入库单**从候选中排除**。
- **BPMN**：`processes/purchase_settlement_approval.bpmn20.xml` —— `managerApproval`（`candidateGroups=PROJECT_MANAGER`）→ `financeApproval`（`candidateGroups=FINANCE_STAFF`）。`PURCHASE_SETTLEMENT` **在 `ApprovalService.SELF_APPROVAL_GUARDED_TYPES:61-65` 内**，发起人不得自审（需两级不同角色办理）。
- **判定：通**
  - 证据（真机）：
    - `available-inbounds` 结算前返回 1 条（我的入库单）→ 建单后返回 0 条 → 结算并审批后仍 0 条（**候选排除成立**）。
    - 建单 → `code 200 DRAFT`，`inboundAmount=250000`、`cumulativeSettlement=100000`（自动带出/计算）、`settlementNo=CGJS202610120005`。
    - **重复结算负向**：同入库单再建 → 500「该入库单已存在结算记录，不可重复结算」。
    - **超入库额负向**（换第二张入库单 5 万）：报 6 万 → 500「结算金额不能大于入库金额」；报 0 → 500「结算金额必须大于0」；缺入库单 → 500「采购结算必须关联入库单」。
    - `submit` → `SUBMITTED`；由 `zhangwei`(PROJECT_MANAGER) 办 `managerApproval`、`wangqiang`(FINANCE_STAFF) 办 `financeApproval` → `APPROVED`；`purchase_contract.cumulativeSettlement = **100000**`。
    - 非草稿重提 → 500「仅草稿状态可提交」。

### 前端操作流程

1. **入口**：菜单「采购管理 > 采购结算」→ `/purchase/settlement`（`src/router/index.ts:515-517` → `src/views/purchase/settlement.vue`）。
2. **实测**：列表显示「关联合同/关联入库单/入库金额/结算金额 100,000.00/状态 已审批」；新增弹窗「关联入库单」为远程下拉（`settlement.vue:70-74`，`:disabled="isEdit || !formData.contractId"`），无可结算入库单时提示「该合同暂无可结算的已审批入库单」；行内按钮按状态收敛（`DRAFT` 才有编辑/提交/删除，`APPROVED` 显示「发起付款」）。
3. **状态回显**：`已审批`（注意该页标签用「已审批」而非「已通过」，`settlement.vue:41`）。
- **判定：通**

### 断裂点
- P3-9C-1（文档过时）：任务书 §二 描述「submit **直接 APPROVED**」，实际代码（PI-3 改造）为 `submit → SUBMITTED → BPMN 审批 → APPROVED`；`PurchaseSettlementService:30-32` 类注释也仍写「提交后发起 Flowable 审批，审批发起成功即回写合同累计结算金额」，与 `:214-216` 实现矛盾。建议同步修订任务书与类注释。
- P3-9C-2（守卫顺序，非缺陷）：同一入库单重复结算的校验先于金额校验，故对已结算入库单传超额金额时返回的是「已存在结算记录」而非「超过入库金额」——提示准确但可能让人误以为金额没问题。可选：先校验金额再校验重复。

---

## 跨阶段横切发现

- **X-1：`a3-contract.spec.ts` 的 `A-X10` 仍失败（批次 1 的 P0 未修）**。真机：`completeAllTodos` 完成合同审批第 2 级返回 **HTTP 500**（`Expected 200 / Received 500`）；结果 `3 passed / 1 failed / 7 did not run`（serial 中断）。因 `A-X10` 位于产值用例之前，该 spec 内的 `A10-12/A-X14`（产值 UI 闭环）**无法执行**——本批次的产值 UI 已由自建 `E2E_TEST_B4_` spec 覆盖（见附录）。该 P0 直接决定阶段 8 能否在 UI 上对「非 WON/FILED 项目」生效。
- **X-2：`consistency-real` 的 contract/finance/subcontract 三 spec 全绿**（6 passed），开票申请/付款申请/封账/施工合同/分包合同页面字段级一致。运行会重写 `e2e/consistency/reports/*`（跟踪文件），我已 `git checkout` 还原，保持工作区干净。
- **X-3：9B 的「项目 COMPLETED」在 UI 上无路径**（竣工验收无入口，见 P1-9B-1），这会连带影响依赖 COMPLETED 的后续阶段（如阶段 10 项目关闭的 close-check 条件）。

---

## 按严重度排序的修复清单

| # | 严重度 | 问题 | 位置 | 建议修复 | 影响阶段 |
|---|---|---|---|---|---|
| 1 | **P1** | **竣工验收无任何前端入口**（无页面/路由/菜单），`getAcceptancePage/createAcceptance/submitAcceptance` 为死代码 → UI 上无法把项目置 `COMPLETED`，9B 闭环在 UI 不可达 | `zw-insight-web/src/api/site.ts:144-155`（无引用）、`src/router/index.ts`（无路由）、`src/views/site/`（无页面） | 在「现场管理」或「项目管理」下补建竣工验收页面并接入三个既有 API；或澄清该动作仅移动端触发 | 9B（→10） |
| 2 | **P2** | 收票登记回写**只认 `biz_other_contract`**：对施工/采购合同登记收票**静默不回写**（实测采购合同 `cumulative_invoice_received` 恒 0.00） | `zw-insight-server/zw-finance/.../service/InvoiceReceivedService.java:64-72` | 按 `contractCategory` 路由到对应合同表（对齐 `PaymentApplyService` 的多合同路由范式）；或明确收票仅针对其他合同并在前端限定下拉 | 9 |
| 3 | **P2** | 产值上限的 `cumulative_change_amount` 只由变更签证回写，**变更事件批准不回写** → 变更事件批出的额度不计入产值上限（少放额度） | 跨批次：任务书 §七 #1；`OutputReportService.java:272-281` 消费该字段 | 打通 `ChangeEvent` 批准 → 合同 `cumulative_change_amount`（任务书已知断裂点，需专项） | 8（←9F） |
| 4 | **P3** | `biz_invoice_apply` 三个快照字段（`contract_amount_snapshot`/`settlement_amount_snapshot`/`historical_invoiced_snapshot`）**无任何写入方**（死字段） | `zw-insight-server/zw-finance/.../domain/BizInvoiceApply.java:52/55/58` | 二选一：在 `saveFromRequest`/`save` 时填充（合同额/结算额/历史已开票快照）；或删列（迁移双轨） | 9 |
| 5 | **P3** | 收票登记实体无 Bean Validation，字段名 `invoiceDate` 与提示「业务日期不可为空」不直观；`projectId` 可空落库 | `zw-insight-server/zw-finance/.../domain/BizInvoiceReceived.java`、`InvoiceReceivedService.java:48-55` | 给 `projectId/invoiceDate/invoiceAmount` 加 `@NotNull`；统一字段提示语 | 9 |
| 6 | **P3** | 产值上报列表项目下拉 `default-first` 选中「列表第一个项目」（常为他人数据），首屏显示「暂无数据」，易误导 | `zw-insight-web/src/views/contract/output-report.vue:10` | 改为不默认选中，或默认选中当前用户参与的项目 | 8 |
| 7 | **P3** | 任务书与类注释称采购结算「submit 直接 APPROVED / 审批发起即回写」，实际为 `submit→SUBMITTED→BPMN→APPROVED` | `docs/全链路26阶段验收任务书.md` §二 9C、`PurchaseSettlementService.java:30-32` | 修订文档与类注释以匹配 PI-3 实现 | 9C |
| 8 | **P3** | `GET /api/v1/site/completion/{id}` 缺失（无详情端点） | `zw-insight-server/zw-site/.../controller/CompletionController.java` | 按需补 `GET /{id}`（前端暂不需要） | 9B |
| 9 | **P3** | 结算单 `profit` 只扣「已结算/已付」支出，未结算支出不入账 → 支出未结算时利润虚高（演示易误解） | `ProjectSettlementService.java:120-126` | 口径设计，建议在 UI 加注「支出按已结算口径」说明 | 9B |
| 10 | **P3** | 收入侧 `total_income`（回款登记即写）与支出侧 `total_expense`（付款审批通过写）口径不对称 | `PaymentReceivedService.java:97-102` ↔ `PaymentApplyService.onApproved` | 明确文档化；对账时注意口径差异 | 9（横切） |

---

## 未验证 / 受阻项（如实列出）

| # | 项 | 分类 | 原因 | 影响范围 | 处置 |
|---|---|---|---|---|---|
| B1 | 阶段 9B「竣工验收」前端流程 | DEP（前端缺口） | 前端**不存在**竣工验收页面/路由（见 P1-9B-1），非我未测，而是**无可测对象** | 9B 前端判定为「不通（缺入口）」 | 已静态确证（grep 全前端无引用 + 无路由）；建议补页面后再回归 |
| B2 | 既有 `a3-contract.spec.ts` 的产值 UI 用例（A10-12/A-X14） | DEP（serial 中断） | `A-X10` 命中批次 1 的 P0（合同审批 500）→ serial 模式中断后续 7 条 | 该 spec 的产值 UI 覆盖 | 已用自建 `E2E_TEST_B4_` UI spec 独立覆盖产值新增/列表/守卫（9/9 通过） |
| B3 | 阶段 9C 审批闭环的「发起人自审」场景 | OTHER（设计约束） | `PURCHASE_SETTLEMENT` 在防自审清单内，admin 无法自审自己的单；我用 tenant 1 的 `zhangwei`(PROJECT_MANAGER) + `wangqiang`(FINANCE_STAFF) 完成两级办理（凭据经 captcha-from-Redis 登录取得，非重登 admin） | 无（闭环已完整跑通） | 已解决，无需处置 |
| B4 | 变更事件对产值上限的影响（9F→8） | OTHER（跨批次） | 变更事件回写属批次 5（9F）范围，本批次只复验「上限=合同额+变更额」判定式与「变更额当前来源只有变更签证」 | 8 的上限口径 | 已在 P2-#3 记录，留给批次 5 交叉验证 |

> 说明：B3 中我为取得「非发起人审批人」token，用 `admin` 之外的 tenant 1 用户（`zhangwei`/`wangqiang`，密码为演示种子已知值）经真实登录取得会话，**未重登 admin**，不影响并行批次的 admin 会话。

---

## 附录：动态验证证据索引

| 证据 | 命令 / 用例 | 关键输出 |
|---|---|---|
| 阶段 8 顺流程 + 超额负向 | 自建 Node `fetch` 探针（已清理） | `POST /contract/output {600000}`→200；`submit` 600k→200；审批后 `contract.cumulativeOutput=600000`；再报 500k → `code 500`「累计产值不能超过合同金额（含变更），当前上限：**1000000.00**」；报 400k → 累计 100 万；`project.cumulativeOutput=1000000` |
| 阶段 8 其余负向 | 同上 | `currentOutput=-100`→500「本期产值必须大于0」；项目/合同不匹配→500「施工合同不属于该项目」 |
| 阶段 9 开票 + 额度负向 | 同上 | 开票 100 万→审批→`contract.cumulativeInvoiceAmount=1000000`；再提交 100 元→500「…最大可开票金额：**0.00**」 |
| 阶段 9 死字段直证 | 直连生产库 SELECT | 两行 `biz_invoice_apply` 的 `contract_amount_snapshot/settlement_amount_snapshot/historical_invoiced_snapshot` 全为 `NULL` |
| 阶段 9 回款登记即 CLAIMED | 同上 | `status=APPROVED, claimStatus=CLAIMED, claimedBy=1`；`project.totalIncome=400000`；`contract.cumulativeReceivedAmount=400000` |
| 阶段 9 回款负向 | 同上 | 70 万→500「…最大可回款金额：600000.00…」；−100→500「回款金额必须大于0」 |
| 阶段 9 收票回写缺口 | 同上 + 库查 | 收票 10 万→200（`invoiceDate` 字段）；`biz_purchase_contract.cumulative_invoice_received=0.00`（未回写）；`biz_other_contract` 无行 |
| 阶段 9 FIFO 核销 + 双写 | 同上 | 结算生成应收 60 万 OPEN；再回款 20 万 → `biz_receivable.written_off_amount=200000`、`biz_receivable_write_off` 明细 200000.00、`biz_project.receivable_amount` 600000→**400000**；`aging.totalOpen=400000` |
| 阶段 9B 竣工验收 + 结算 | 同上 | 竣工 `submit`→`SUBMITTED`（中间态）；重提→500「仅草稿状态可提交」；审批→`APPROVED`+`project.COMPLETED`；结算建单→`DRAFT`（收入/收款/开票/利润见正文）；审批→`APPROVED`+`contract.SETTLED`；应收 600000（`sourceType=SETTLEMENT`, `dueDate=2026-11-11`, OPEN）；`project.receivable_amount=600000` |
| 阶段 9C 采购结算 + 4 类负向 | 同上 | `available-inbounds` 1→0→0；建单→`DRAFT`（`inboundAmount=250000`,`cumulativeSettlement=100000`）；重复→500「该入库单已存在结算记录，不可重复结算」；超额→500「结算金额不能大于入库金额」；0 额→500「结算金额必须大于0」；缺入库单→500「采购结算必须关联入库单」；`submit`→`SUBMITTED`；zhangwei+wangqiang 审批→`APPROVED`+`purchase_contract.cumulativeSettlement=100000` |
| 阶段 8/9/9B/9C UI 正向 + 抓包 | `npx playwright test --project=e2e-real _b4-ui.spec.ts --workers=1 --retries=0`（自建 spec，**9/9 passed**） | 产值上报列表「600,000.00/已通过」+ 新增草稿 `POST /api/v1/contract/output → 200`；开票申请「1,000,000.00」+ 空表守卫（POST 0）；收票登记「100,000.00」；回款登记「400,000.00/已认领」+「核销」按钮；应收台账账龄表「400,000.00」；项目最终结算「已通过」；采购结算「100,000.00/已审批」 |
| 既有 L5 套件（本批次分配） | `npx playwright test --project=e2e-real a3-contract.spec.ts --workers=1 --retries=0` | `3 passed / 1 failed / 7 did not run`；失败= `A-X10` 完成审批任务返回 **500**（批次 1 P0 复现） |
| 既有一致性套件（本批次分配） | `npx playwright test --project=consistency-real contract.spec.ts finance.spec.ts --workers=1 --retries=0` | **6 passed**（施工合同/开票申请/付款申请/财务封账/分包合同 字段级一致） |
| 竣工验收无 UI 入口（静态） | `grep -rn "竣工验收\|acceptance\|completion" src/` + `src/router/index.ts` | 仅 `src/api/site.ts:144-155` 三个函数，无 view 引用、无路由、无菜单 |

**数据卫生（自查，已复验）**：
- ✅ 自建 `E2E_TEST_B4_*` 数据已**全部删净**：项目 2 个（含 1 条逻辑删除墓碑已硬删）、施工合同 2、采购合同 1、预算 1、产值上报 4、开票申请 2、收票 1、回款 2、入库单 2、采购结算 1、项目结算 1、竣工验收 1、应收 1、应收核销明细、项目成员/状态流水/材料库存等——按项目 id 精确清理，**逐表复验计数为 0**；`biz_project LIKE '%E2E_TEST_B4%'` = **0**。
- ✅ 未动 `90001-90004` 等种子；未直删任何 `ACT_*` 表；`a3` 因 P0 卡死的 1 条合同审批待办已走应用层 `POST /workflow/approval/terminate` 收口（`code 200`），复验该 `PROC_INST_ID_` 下 `ACT_RU_TASK` 已清零。
- ✅ 临时探针/夹具/spec（`keys/_b4_*`、`zw-insight-web/e2e/tests/real/_b4-*.spec.ts`、`test-results/`）已删除；`e2e/consistency/reports/*` 因跑测试被重写，已 `git checkout` 还原。
- ✅ `git status` 仅剩**其它批次**的产物（batch-1/2/6 报告、任务书、batch-5 的 `_b5-ui.spec.ts`），无本批次未清理文件。
