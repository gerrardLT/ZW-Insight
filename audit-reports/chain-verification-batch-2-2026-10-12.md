# 全链路验收报告 · 批次 2（阶段 5 / 6）

> 验收日期：2026-10-12
> 环境：主环境 `129.204.3.200`（tenant 1 演示租户；后端 :18080 / 前端 :18081，均真机实测）
> 权威定义：`keys/lifecycle-sim-v2.sh` 的 `stage_5_budget`（:678）/ `stage_6_subcontracts`（:734）
> 方法：静态（`file:line`）+ 动态（真机 API 响应摘要、真机无头 Playwright UI 抓包）
> 纪律遵守：全程 tenant 1；写用例统一 `E2E_TEST_B2_` 前缀并**逐条按前缀删除**（未做 `LIKE 'E2E_TEST_%'` 无差别清扫）；未动 90001-90004 种子；未本地起 JVM/mvn；中文 JSON 一律走 Node `fetch`；终止流程一律走应用层 `withdraw-by-business`，**未直删任何 `ACT_*` 表**

---

## 汇总

| 阶段 | 后端链路 | 前端流程 | 关键缺口 |
|---|---|---|---|
| 5 预算编制 | **通**（预算主链）/ **部分通**（预算控制链） | **部分通** | `@BudgetCheck` 共 **6 处**（任务书称 5 处，已过时），其中 **4 处空转**；「全局默认」预算控制配置在 UI 上**创建不出真正生效的默认规则**（`isDefault` 恒 0）；预算控制配置页「留空=全局默认」文案与后端语义不符 |
| 6 支出合同 | **部分通** | **部分通** | 劳务/机械合同**发起人（admin）无法推进审批**（防自审 403，单账号环境卡死）；机械/分包/采购 `submit` 的预算校验**空转**（500 万合同对 100 万预算放行）；劳务列表 SUBMITTED 显示「**草稿**」；机械列表**无状态列**；`b5-purchase.spec.ts` 前提过期（1 failed / 7 did not run） |

**批次结论**：两个阶段在「单账号 + 顺流程」的**主链**上可跑通并产生正确数据（预算直批 APPROVED 并回写项目 `budget_amount`；四类合同创建+提交均成功，分包直批生效）；但**预算控制（阶段 5 的核心价值）大面积空转**，且**劳务/机械合同审批在单账号下无法闭环**，另有 2 处列表状态回显缺陷与 1 条过期 E2E 用例。未发现批次 1 那样的 P0 阻断（无 500 卡死）。

---

## 阶段 5：预算编制

### 后端逻辑链路

- **数据模型**
  - 主表 `biz_budget`（`zw-budget/.../domain/BizBudget.java:16` `@TableName`）；`projectId:22`、`status:48`（实际取值 `DRAFT` / `APPROVED`，见下）、`budgetType`（`ORIGINAL`/`CHANGE`）、`totalAmount`、`changeSeq`。
  - 明细 `biz_budget_detail`（`BizBudgetDetail.java:15`）；`budgetId:21`、`costCategory:26`（MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER）、`budgetTotalPrice:61`。
  - 预算控制配置 `sys_budget_control_config`（`SysBudgetControlConfig.java:14`）；`projectId:18`、`controlMode`（`WARN_ONLY`/`BLOCK`/`EXEMPT`）、`warningThreshold`（50-99）、`isDefault:27`。
  - 请求 DTO `BudgetCreateRequest`（`dto/BudgetCreateRequest.java:14`）：`projectId @NotNull:19`、`budgetType @NotBlank:25`、`totalAmount`（可空）、`details`（可选）。
- **状态机**（无 Flowable，纯方法推进）
  - 创建：`BudgetService.saveFromRequest:77` → `save:159` → **强制 `budgetType=ORIGINAL`**（`:161-163` 白名单守卫，防传 CHANGE 绕过唯一性）→ 同项目重复 ORIGINAL 拦截 `:166-172` → 置 `DRAFT:175` → `insert:179`。明细写入 `saveDetails:110`；存在明细时以明细合计覆盖 `totalAmount`（`:83-86`）。
  - 提交：`BudgetService.submit:186` → 守卫仅 `DRAFT:191-193` → 有明细时以明细 Σ 覆盖总额 `:199-205` → **直接置 `APPROVED:206`（直批，无审批链）** → `updateById:207`。
  - **跨模块联动（回写别的模块的表）**：`:210-214` 回写 `biz_project.budget_amount = budget.totalAmount`（`projectMapper.updateById`）。**这是本阶段唯一的跨模块写**，且**无任何 Spring 事件 / 监听器 / 定时任务**（`BudgetService` 内无 `publishEvent`）。
- **API（双向核对一致）**
  - 前端 `src/api/budget.ts:21-23` `createBudget` → `POST /v1/budget` ↔ 后端 `BudgetController.java:46-51`（基路径 `/api/v1/budget`:20 + `@PostMapping`:46）。
  - 前端 `budget.ts:35-37` `submitBudget` → `POST /v1/budget/{id}/submit` ↔ 后端 `BudgetController.java:53-58`。
  - 前端 `budget.ts:9-11` `getBudgetPage` → `GET /v1/budget/page` ↔ 后端 `BudgetController.java:27-34`。方法/路径/字段名一致。
  - 二级科目：前端 `budget.ts:40-42` `/v1/budget/subcategory/{costCategory}` ↔ `CostSubcategoryController.java:16` 基路径 + `:23` `@GetMapping("/{costCategory}")`，一致。
- **调用链**：`BudgetController.save:48` → `BudgetService.saveFromRequest:77` → `save:159` → `saveDetails:110`；`BudgetController.submit:55` → `BudgetService.submit:186`。**无审批流程、无事件**。
- **校验拦截**（负向均被正确拦截，实测）
  - 同项目重复 ORIGINAL → `code 500`「该项目已存在原始预算，不可重复创建」（实测）。
  - APPROVED 编辑 → `code 500`「仅草稿状态可编辑」；APPROVED 重提交 → `code 500`「仅草稿状态可提交」（a4-budget `A11-07/A11-11` 通过）。
  - 缺 `projectId` / `budgetType` → Bean Validation 400。
  - 提交时明细合计覆盖总额：实测明细 10×50000=500000 覆盖录入值 500000；a4-budget `A11-06/A-X15` 断言「明细合计 1000 覆盖录入 999」通过。
- **判定：通（预算主链）/ 部分通（预算控制链）**
  - 主链证据（真机）：`POST /v1/budget` → `200 code 200`；`POST /v1/budget/{id}/submit` → `200 code 200`；回查 `status=APPROVED`、`totalAmount=4000000`；`GET /project/{pid}` → `budgetAmount=4000000`（跨模块回写成立）。
  - 控制链证据见下方「@BudgetCheck 挂载点核查」——6 处中 4 处空转。

### 前端操作流程

1. **入口**：菜单「预算管理 > 预算编制」→ 路由 `/budget/list`（`src/router/index.ts:469-474` → `src/views/budget/index.vue`）；控制配置 `/budget/control-config`（`router/index.ts:487-492`）。
2. **实测点击序列**（临时 UI 用例 `_b2-ui.spec.ts`，真机无头 Chrome，`--project=e2e-real --no-deps --workers=1`）：
   进 `/budget/list` → 点「新增预算编制」→ 弹窗选项目（remote）→ 填预算总额 500000 → 点「添加明细行」→ 明细行填 itemName/数量 10/单价 50000 → 点「确定」→ 列表刷新 → 点行内「提交」→ 确认框「确定」。
3. **实际请求**（`waitForResponse` 抓包）：
   - `POST /v1/budget` → `200 code=200`「操作成功」
   - `POST /v1/budget/{id}/submit` → `200 code=200`「操作成功」
   - 回查 `GET /v1/budget/{id}` → `status=APPROVED`、`totalAmount=500000`；`GET /v1/project/{pid}` → `budgetAmount=500000`
   - 列表状态标签实测 `已批准`（`[UI-5-tag] budget tag="已批准"`）
4. **边界**：行内「导入明细/提交/删除」仅 `status==='DRAFT'` 渲染（`budget/index.vue:46-48`）；提交有二次确认（`handleSubmit:317-322`）；状态标签三元 `APPROVED→已批准 / APPROVING→审批中 / 其余→草稿`（`:37-38`）——`APPROVING` 是**死分支**（`BudgetService` 从不置该值）。
5. **判定：部分通**
   - 主链通（上述真机证据）。
   - 缺口在**预算控制配置页**（下述 P1-2）：页面文案「留空表示全局默认配置」（`control-config/index.vue:68`）与后端语义不符，创建的「全局默认」规则**永不生效**。

### 断裂点

- **P1-2（全局默认预算控制配置不可用）**
  - **静态**：`BudgetControlConfigService.save:100` 恒 `config.setIsDefault(0)`；而 `getEffectiveConfig:157-159` 只在**无项目级配置**时查 `is_default=1` 作为系统默认。→ 经 UI/API 以 `projectId=null` 创建的「全局默认」行 `is_default=0`，**既不被项目级查询命中（projectId=NULL），也不被默认查询命中（is_default≠1）**，成为死行。
  - **附带**：`save:90-94` 的「同项目重复预检」用 `eq(getProjectId, dto.getProjectId())`，当 `projectId=null` 时生成 `project_id = NULL`（恒不匹配），**全局默认配置可无限重复创建**（实测连续两次 `POST /api/v1/budget-control-configs {projectId:null,...}` 均 `200`，未触发「已存在预算控制配置」）。
  - **动态复验**：创建两条全局配置（`WARN_ONLY/70`、`EXEMPT/60`，均 `code 200`）后，`GET /v1/budget-control-configs/project/{pid}` 仍返回 `id=1 BLOCK/80 isDefault=1`（种子默认行）——新建的「全局默认」**未被采用**。
  - **实际可用路径**：DB 中存在种子的 `sys_budget_control_config.id=1`（`project_id=NULL, is_default=1, BLOCK/80`），`getEffectiveConfig` 的真实默认来自它；`hardCodedDefault()`（`:174-180`）仅在前者也不存在时兜底。故「全局默认」功能**只能靠种子/DB 直插**，UI 无法维护。
  - 影响阶段 5 的「控制配置」能力与 UI 承诺；a4-budget `A14-08`（删项目级配置后断言 `isDefault=1`）之所以通过，是因为它命中了种子行而非 UI 创建的行。

- **P1-1（`@BudgetCheck` 6 处中 4 处空转）** —— 任务书 §七 #2 的复验，**结论成立但计数需修正（实为 6 处，非 5 处）**：

  | # | 挂载点 | 方法签名 | category | 切面提取结果 | 是否生效 |
  |---|---|---|---|---|---|
  | 1 | `PurchaseContractService.java:70` | `save(BizPurchaseContract)` | `MATERIAL` | projectId + contractAmount 均可反射取得 | ✅ **生效** |
  | 2 | `LaborContractService.java:55` | `save(BizLaborContract)` | `LABOR` | 同上 | ✅ **生效** |
  | 3 | `PurchaseContractService.java:122` | `submit(Long id)` | `MATERIAL` | 裸 Long → `extractProjectId` 返回 null | ❌ **空转**（`:58-62` log.warn 跳过） |
  | 4 | `MachineContractService.java:147` | `submit(Long id)` | `MACHINE` | 裸 Long → null | ❌ **空转** |
  | 5 | `SubcontractService.java:87` | `submit(Long id)` | `SUBCONTRACT` | 裸 Long → null | ❌ **空转** |
  | 6 | `OtherPaymentService.java:43` | `save(BizOtherPayment)` | `""`（自动识别） | `BizOtherPayment` 无 `getContractCategory/getCostCategory/getCategory` → category 仍空 | ❌ **空转**（`:52-56` 跳过） |
  | — | `PaymentApplyService.submit` | 程序化 `enforceBudgetControl`（`:220`/`:234`） | 合同分类映射科目 | 注解已移除，改显式调用 | ✅ **生效** |

  切面跳过逻辑：`BudgetControlAspect.java:42-68`（category 空 → `:52-56` 跳过；projectId null → `:58-62` 跳过；amount ≤0 → `:64-68` 跳过）。反射提取器 `extractProjectId:112-136` / `extractAmount:148-180` / `extractCategory:86-103`。

  **动态实测（每条空转均有独立证据）**：
  - **#1 生效（正向拦截）**：项目 P 有 MATERIAL 预算 1,000,000；首份采购合同 500,000 建成（DRAFT）并审批生效（EFFECTIVE，累计占用 500,000）；再建第二份采购合同 600,000 → **`code 500`「科目[MATERIAL]预算执行率110.00%，已超预算」**（BLOCK 生效）。
  - **#3 空转**：同项目两份采购合同各 600,000（save 时 `used` 只统计 EFFECTIVE，故两份均放行）→ 提交并审批 A 至 EFFECTIVE（used=600,000）→ **提交 B 返回 `code 200`、状态 `SUBMITTED`**；若 submit 的 `@BudgetCheck` 生效，此时 used(600,000)+600,000=1.2M>1M 必被 BLOCK。
  - **#4 空转**：MACHINE 预算 1,000,000，机械合同 **5,000,000**（未传 budgetId）→ `save` 无注解且手工校验被 `budgetId==null` 短路（`MachineContractService.java:118`）→ **创建 `200`**；`submit` 的 `@BudgetCheck(MACHINE)` 空转 → **`200`、`SUBMITTED`**。
  - **#5 空转**：SUBCONTRACT 预算 1,000,000，分包合同 **5,000,000** → 创建 `200`；`submit` 空转 → **`200` 且直接 `EFFECTIVE`**（分包无 BPMN，直批）。
  - **#6 空转**：其他支付 **5,000,000** → `POST /api/v1/finance/other-payment` **`200`「操作成功」**（BLOCK 下未拦截）。
  - **BLOCK 配置本身可用**：`GET /v1/budget-control-configs/project/{pid}` 对新项目回落种子默认 `BLOCK/80`，说明拦截能力存在，只是**挂载点取不到参数**。

- **P3（预算控制配置 DTO 语义不一致）**：`BudgetControlConfigDTO.java:15` 注释「项目ID（可空表示系统默认）」，但 `save` 不写 `isDefault`，语义落空（见 P1-2）。

---

## 阶段 6：支出合同（采购 / 劳务 / 机械 / 分包）

### 后端逻辑链路

- **数据模型**（四张独立表，字段高度同构）

  | 类型 | 表（`@TableName`） | status 字段 | 金额字段 | 审批 |
  |---|---|---|---|---|
  | 采购 | `biz_purchase_contract`（`BizPurchaseContract.java:16`） | `:102` | `contractAmount:72` | BPMN |
  | 劳务 | `biz_labor_contract`（`BizLaborContract.java:16`） | `:71` | `contractAmount:56` | BPMN |
  | 机械 | `biz_machine_contract`（`BizMachineContract.java:16`） | `:68` | `contractAmount:53` + `unitPrice` | BPMN |
  | 分包 | `biz_subcontract`（`BizSubcontract.java:17`） | `:63` | `contractAmount:48` | 无（直批） |

  status 取值：`DRAFT` →（采购/劳务/机械）`SUBMITTED` → `EFFECTIVE`；分包 `DRAFT` → `EFFECTIVE`。

- **状态机（四类路径各不相同，实测确认——任务书描述需修正）**

  | 类型 | 创建 | 提交 | 审批→生效 | BPMN key / 节点 |
  |---|---|---|---|---|
  | 采购 | `PurchaseContractService.save:72`（DRAFT，`@BudgetCheck` 生效） | `submit:124`（起 `purchase_contract_approval`，置 SUBMITTED） | `onApproved:150`（监听器驱动）置 EFFECTIVE | `purchase_contract_approval.bpmn20.xml`：**2 个 userTask**（`:17-20` 项目负责人审批、`:26-29` 财务审核），**两级均 `assignee=${initiator}`** |
  | 劳务 | `LaborContractService.save:57`（DRAFT，`@BudgetCheck` 生效） | `submit:78`（起 `labor_contract_approval`，置 SUBMITTED） | `onApproved:102` 置 EFFECTIVE | `labor_contract_approval.bpmn20.xml`：2 个 userTask，`candidateGroups` **PROJECT_MANAGER（:11）→ FINANCE_STAFF（:15）** |
  | 机械 | `MachineContractService.save:110`（DRAFT，仅 `budgetId!=null` 时手工校验 `:118-139`） | `submit:149`（起 `machine_contract_approval`，置 SUBMITTED） | `onApproved:81`（**本类自带 `@EventListener`**，非独立 Listener 类）置 EFFECTIVE | `machine_contract_approval.bpmn20.xml`：2 个 userTask，`candidateGroups` **PROJECT_MANAGER（:8）→ FINANCE_STAFF（:10）** |
  | 分包 | `SubcontractService.save:51`（DRAFT，仅 `budgetId!=null` 时手工校验 `:57-79`） | `submit:89` → **直接置 EFFECTIVE（:92）**，**无 BPMN** | — | 无流程定义 |

  > **任务书 §二 描述纠正**：① 「采购走 BPMN 自动完成」**不成立**——当前部署的 `purchase_contract_approval`（ACT_RE_PROCDEF v5）需 **2 次人工办理**（实测提交后 `status=SUBMITTED`，待办 2 个：项目负责人审批→财务审核）；② 「机械直批」**不成立**——机械同样走 2 级 BPMN；③ 只有**分包**是直批。

- **API（双向核对一致，四类同构）**

  | 类型 | 前端 `src/api/*.ts` | 后端 Controller |
  |---|---|---|
  | 采购 | `purchase.ts:25-27` `POST /v1/purchase/contract`；`:33-35` `POST /{id}/submit` | `PurchaseContractController.java:22` 基路径 + `:46`/`:58` |
  | 劳务 | `labor.ts:19-21` `POST /v1/labor/contract`；`:29-31` `POST /{id}/submit` | `LaborContractController.java:18` + `:42`/`:60` |
  | 机械 | `machine.ts` `POST /v1/machine/contract`、`/{id}/submit` | `MachineContractController.java:18` + `:41`/`:59` |
  | 分包 | `subcontract.ts` `POST /v1/subcontract/contract`、`/{id}/submit` | `SubcontractController.java:18` + `:42`/`:60` |

  方法/路径一致；四类 `page` 端点均 `@RequestParam page/size`（前端视图传 `pageNum/pageSize`，由 `src/utils/request.ts:60-68` 统一映射为 `page/size`，**非缺陷**——已实测确认映射存在）。
- **调用链**：`submit` → `ApprovalService.startProcess`（`:82-113`，写 `businessType/businessId/initiator` 变量 + businessKey）→ 审批完成 → `ProcessCompleteListener` 发 `ApprovalCompleteEvent` → 各模块监听器：`PurchaseApprovalListener`（筛 `PURCHASE_CONTRACT`）、`LaborApprovalListener`（筛 `LABOR_CONTRACT`）、`MachineContractService.onApproved`（自带 `@EventListener`）→ 置 EFFECTIVE。分包无此链（`submit` 内直接置位）。
- **跨模块联动**：**四类支出合同的创建/提交均不回写其他模块的表**（`biz_purchase_contract` 等的 `save`/`submit`/`onApproved` 只写本表 status/`workflowInstanceId`）。与施工合同（回写项目合同额+项目状态）不同——**支出合同对项目的唯一影响是「被预算控制查询占用」**（`BudgetOccupiedMapper` 只读 `EFFECTIVE` 合同金额）。因此**没有批次 1 的 P0-1 那类回写联动断裂**。
- **校验拦截**（负向实测）
  - 采购 `save` 超预算 → `code 500`「科目[MATERIAL]预算执行率110.00%，已超预算」（BLOCK 生效）。
  - 非 DRAFT 重提交 → `code 500`「仅草稿状态可提交」（四类一致）。
  - 机械 `validate:43-57`：项目/合同额/单价必填且>0、`rentalType ∈ {SHIFT,台班,MONTHLY,月租,包月,VOLUME,工作量}`、起止日期有效 → 违反抛业务异常。
  - 分包 `save:55-57`：合同金额必须>0。
  - 采购/劳务/机械 `save` 带 `@BlacklistCheck`（供应商黑名单）。
- **判定：部分通**
  - 正向（真机）：采购 `DRAFT→SUBMITTED→(2 级审批)→EFFECTIVE`（admin 可自审，因 `PURCHASE_CONTRACT` 不在防自审名单）；劳务/机械 `DRAFT→SUBMITTED`（审批任务为候选组任务，admin 不可办理，见下）；分包 `DRAFT→EFFECTIVE`（直批）。
  - 缺陷：机械/分包/采购 `submit` 预算校验空转（P1-1）；劳务/机械审批在单账号下不可闭环（P2-5）。

### 前端操作流程

1. **入口**（菜单 → 路由 → 视图）
   - 采购：「采购管理 > 采购合同」→ `/purchase/contract`（`router/index.ts:509-512` → `views/purchase/contract.vue`）
   - 劳务：「劳务管理 > 劳务合同」→ `/labor/contract`（`router/index.ts:536-539` → `views/labor/contract.vue`）
   - 机械：「机械管理 > 机械合同」→ `/machine/contract`（`router/index.ts:644-647` → `views/machine/contract.vue`）
   - 分包：「分包管理 > 分包合同」→ `/subcontract/contract`（`router/index.ts:701-704` → `views/subcontract/contract.vue`）
2. **实测点击序列**（四类同构，临时 UI 用例真机无头）：进列表 → 点「新增XX合同」→ 弹窗选项目（remote ProjectSelector）→ 填合同名称/供应商(或分包方/施工队伍/设备名称)→ 填金额（机械另填计价单价+租赁方式+起止日期）→ 点「确定」→ 列表刷新 → 行内点「提交审批」→ 确认框「确定」。
3. **实际请求**（`waitForResponse` 抓包，四类均通过）
   - 采购：`POST /api/v1/purchase/contract → 200 code=200`；`POST .../{id}/submit → 200 code=200`；回查 `status=SUBMITTED`；列表标签 **`审批中`**；提交后「提交审批」按钮消失（`submitBtnCount=0`）。
   - 劳务：`POST /api/v1/labor/contract → 200`；`submit → 200`；回查 `status=SUBMITTED`；**列表标签 `草稿`（错）**。
   - 机械：`POST /api/v1/machine/contract → 200`；`submit → 200`；回查 `status=SUBMITTED`；**列表无状态列（`NO STATUS TAG COLUMN`）**。
   - 分包：`POST /api/v1/subcontract/contract → 200`；`submit → 200`；回查 `status=EFFECTIVE`；列表标签 **`生效`**。
4. **边界**：行内「提交审批」仅 `status==='DRAFT'` 渲染（采购 `contract.vue:46`、劳务 `:50`、机械 `:35`、分包 `:40`）——实测提交后按钮消失，条件渲染正确。客户端必填：采购 `:105-110`（项目/名称/供应商/金额）、劳务 `:117`（项目/名称/队伍）、机械 `:93`（项目/名称/供应商/设备名）、分包 `:86`（项目/名称/分包方/金额>0）——与后端校验基本对齐（机械/劳务金额未标必填，但后端/切面会兜底）。
5. **判定：部分通**
   - 新增+提交主链四类均通（真机抓包证据）。
   - 缺口：劳务列表状态回显错误（P1-3）、机械列表无状态列（P2-4）。

### 断裂点

- **P1-3（劳务合同列表状态回显错误：SUBMITTED 显示「草稿」）**
  - 静态：`src/views/labor/contract.vue:42-44` 标签三元只判 `EFFECTIVE ? '生效' : '草稿'`，**无 SUBMITTED 分支**。
  - 动态：真机实测 `[LABOR_CONTRACT-tag] tag="草稿"`，而同一条记录 `GET /api/v1/labor/contract/{id}` → `status=SUBMITTED`。
  - 影响：用户提交后列表仍显示「草稿」，无法判断是否已提交（易误点删除/重复提交）。对照采购 `purchase/contract.vue:38-40` 有正确的三态映射。

- **P2-4（机械合同列表无「状态」列）**
  - 静态：`src/views/machine/contract.vue:22-45` 列集合为 合同编号/名称/供应商/设备名/金额/租赁方式/开始日期/结束日期/操作，**无状态列**（对照采购 `:36-42`、分包 `:32-34` 均有）。
  - 动态：真机 `[MACHINE_CONTRACT-tag] NO STATUS TAG COLUMN`。
  - 影响：无法从列表判断 DRAFT/SUBMITTED/EFFECTIVE（仅能靠「提交审批」按钮是否出现间接推断）。

- **P2-5（劳务/机械合同审批在单账号下无法闭环——防自审 + 候选组双重约束）**
  - 静态：`ApprovalService.SELF_APPROVAL_GUARDED_TYPES:61-65` 含 `MACHINE_CONTRACT`、`LABOR_CONTRACT`；`assertNotInitiator:868-875` 在 **SUPER_ADMIN 放行之前**执行（`assertTaskAssignee:937-962` 顺序为 `assertNotInitiator` → 再判 SUPER_ADMIN），故**发起人即便超管也被拒**。
  - 动态：劳务/机械合同由 admin 提交后，其候选组任务在 `GET /workflow/approval/todo` 中**不可见**（候选组任务不入 assignee 待办，0 条）；直接从 `ACT_RU_TASK` 取 taskId 调 `POST /workflow/approval/complete` → **`code 403`「发起人不能审批自己的单据」**。
  - 影响：只要「提交人」与「唯一可用审批账号」是同一人（本验收的 admin 场景），**劳务/机械合同永远停在 SUBMITTED**。生产环境需另一个持 `PROJECT_MANAGER`/`FINANCE_STAFF` 角色的用户办理。这是**防自审设计**（防串通）与**单账号可操作性**的固有冲突，非代码 bug，但验收/演示环境会卡死，且 L4 脚本（tenant 9999）能过是因为它用 `ACT_RU_TASK` 直取 taskId + 独立测试租户账号。
  - 对照：采购合同两级 `assignee=${initiator}` 且 `PURCHASE_CONTRACT` **不在**防自审名单 → admin 自审可过（实测 2 级均 `code 200` → EFFECTIVE）。

- **P2-6（机械/分包「手工预算校验」因缺 `budgetId` 而实际不生效）**
  - 静态：`MachineContractService.save:118-139`、`SubcontractService.save:57-79` 的预算校验均包在 `if (contract.getBudgetId() != null)` 内；而两个前端表单**均无 budgetId 字段**（`machine/contract.vue:56-65`、`subcontract/contract.vue:53-58`），API 调用也不传 budgetId。
  - 动态：机械/分包各 5,000,000 合同对 1,000,000 预算**创建成功**。
  - 影响：机械/分包合同的**创建**阶段实际无任何预算约束（叠加 P1-1 的 `submit` 空转 → 全程无约束）。

- **P3-8（分包列表状态标签语义不完整）**：`subcontract/contract.vue:32-34` 同劳务，仅 `EFFECTIVE/草稿`。当前分包无 SUBMITTED 态故不误显，但若未来引入审批会重演 P1-3。

- **P1-4（`b5-purchase.spec.ts` 前提过期，L5 套件失败）**
  - 静态：`e2e/tests/real/b5-purchase.spec.ts:112` 断言提交后 `status === 'EFFECTIVE'`（注释与 describe 标题均写「BPMN 自动完成至 EFFECTIVE」）。
  - 动态：真机运行 `npx playwright test --project=e2e-real --no-deps b5-purchase.spec.ts --workers=1 --retries=0` → **`1 failed / 7 did not run`**，失败点 `Expected: "EFFECTIVE" / Received: "SUBMITTED"`（serial 模式首个失败即中断后续 7 例）。
  - 根因：该用例写于「部署的 `purchase_contract_approval` 为自动完成版本」时期；当前部署版本（ACT_RE_PROCDEF v5）为 2 级人工审批。**属测试与实现脱节**（与 AGENTS.md「测试同步守卫」动因同类），非产品缺陷。

---

## 跨阶段横切发现

### X-1：a4-budget 全套通过，b5-purchase 首例即败

| Spec | 结果 | 性质 |
|---|---|---|
| `a4-budget.spec.ts`（10 例） | **10 passed** | 预算主链 + 变更链 + 控制配置 CRUD 全部可用（API 级） |
| `b5-purchase.spec.ts`（8 例） | **1 failed / 7 did not run** | 用例前提过期（P1-4，非产品缺陷） |
| `consistency/budget.spec.ts` | **failed**（「种子数据应保证列表非空」，received 0） | **环境/用例脆弱**：`/budget/list` 自动选中「项目列表首项」（`budget/index.vue:331-338`），而 `/v1/project/list` 按创建时间倒序，首项是**其它批次并发 E2E 项目**（实测首项 `E2E_TEST_B4_UI_...`）无预算 → 列表空。**非产品缺陷**（`GET /v1/budget/page?projectId=90001` 正常返回种子 92001） |
| `consistency/purchase.spec.ts` | **passed** | 采购列表字段级一致 |

### X-2：四类合同「列表状态回显」实现质量不均

采购正确三态；劳务/分包两态（劳务缺 SUBMITTED → 误显草稿）；机械完全无状态列。建议统一抽一个状态标签组件。

---

## 按严重度排序的修复清单

| # | 严重度 | 问题 | 位置 | 建议修复 | 影响阶段 |
|---|---|---|---|---|---|
| 1 | **P1** | `@BudgetCheck` 6 处中 4 处空转：3 处挂 `submit(Long id)`（裸 Long 取不到 projectId）、1 处挂无科目实体（`BizOtherPayment` 无 category getter）。机械/分包/其他支付 500 万合同对 100 万预算**全程放行** | `PurchaseContractService.java:122`、`MachineContractService.java:147`、`SubcontractService.java:87`、`OtherPaymentService.java:43` | 参照 `PaymentApplyService.submit:220` 的既有范式：移除 `submit(Long)` 上的死注解，改在方法内显式查实体后调用 `BudgetControlConfigService.checkBudget(projectId, category, amount)`；`OtherPayment` 若确无科目可映射，则明确标注「不参与预算控制」并从注解语义中剥离，避免误导 | 5 / 6 |
| 2 | **P1** | 「全局默认」预算控制配置不可用：`save` 恒 `isDefault=0`，`getEffectiveConfig` 查 `is_default=1`；且 `projectId=null` 时重复预检失效（`project_id=NULL` 恒不匹配），可无限重复创建死行 | `BudgetControlConfigService.java:100`（写）、`:157-159`（读）、`:90-94`（预检） | 三选一：① `save` 在 `projectId==null` 时置 `isDefault=1` 并加「唯一默认」守卫（含 `project_id IS NULL` 的重复预检改用 `isNull()`）；② 前端「留空=全局默认」改为显式「设为系统默认」开关；③ 若产品决定「全局默认只由种子维护」，则删除 UI 的「留空表示全局默认配置」文案（`control-config/index.vue:68`）与列表「全局默认」回退文案（`:28`） | 5 |
| 3 | **P1** | 劳务合同列表 SUBMITTED 显示「草稿」 | `src/views/labor/contract.vue:42-44` | 补 SUBMITTED 分支（对齐 `purchase/contract.vue:38-40` 三态映射） | 6 |
| 4 | **P1** | `b5-purchase.spec.ts` 前提过期（断言采购 submit 自动 EFFECTIVE），首例失败导致 7 例未跑 | `e2e/tests/real/b5-purchase.spec.ts:112`（及 `:98` 标题/注释、`:113`） | 更新为「提交→SUBMITTED→完成 2 级待办→EFFECTIVE」（参照 `a4-budget.spec.ts:85-97` 的 `completeAllTodos` 范式）；并评估 serial 首败中断改为同文件内不阻断 | 6（横切） |
| 5 | **P2** | 机械合同列表无「状态」列 | `src/views/machine/contract.vue:22-45` | 增加状态列（三态标签），与采购/劳务/分包对齐 | 6 |
| 6 | **P2** | 劳务/机械合同审批在单账号环境无法闭环：候选组任务 + 防自审（发起人含超管被 403） | `ApprovalService.java:61-65`（名单）、`:868-875`（防自审先于超管放行）、`labor_contract_approval.bpmn20.xml:11/15`、`machine_contract_approval.bpmn20.xml:8/10` | 属设计取舍。建议：① 验收/演示环境提供第二个持 `PROJECT_MANAGER`+`FINANCE_STAFF` 的账号；或 ② 明确「防自审仅约束业务角色，超管作为流程兜底可代办」并调整 `assertNotInitiator` 的超管豁免顺序（需产品决策，涉及合规） | 6 |
| 7 | **P2** | 机械/分包合同「手工预算校验」因前端不传 `budgetId` 而永不执行 | `MachineContractService.java:118-139`、`SubcontractService.java:57-79`；`machine/contract.vue:56-65`、`subcontract/contract.vue:53-58` | 二选一：① 前端补 budgetId 选择（或自动取项目当前 ORIGINAL 预算）；② 删除该手工分支，统一改走按 `projectId+category` 的 `checkBudget`（与采购/劳务一致） | 6 |
| 8 | **P2** | 任务书对四类审批路径的描述与实现不符（采购非「自动完成」、机械非「直批」） | `docs/全链路26阶段验收任务书.md` §二 阶段 6 | 更正为：采购=2 级 BPMN（自审 assignee）、劳务=2 级 BPMN（候选组）、机械=2 级 BPMN（候选组）、分包=直批 | 6 |
| 9 | **P2** | `consistency/budget.spec.ts` 依赖「项目列表首项有预算」的脆弱前提，被并发 E2E 项目污染 | `e2e/consistency/budget.spec.ts:34-37` + `src/views/budget/index.vue:331-338` | 用例改为显式选种子项目（`90001`）后再断言非空；或页面「未选项目」时不自动选中首项而展示「请选择项目」 | 5（横切） |
| 10 | **P3** | 分包列表状态标签仅两态（潜在同类隐患） | `src/views/subcontract/contract.vue:32-34` | 补 SUBMITTED 分支或抽公共状态标签组件 | 6 |
| 11 | **P3** | 预算列表状态标签含死分支 `APPROVING`（`BudgetService` 从不置该值） | `src/views/budget/index.vue:37-38` | 移除死分支或统一状态枚举 | 5 |
| 12 | **P3** | `BudgetControlConfigDTO.projectId` 注释「可空表示系统默认」与 `save` 语义不符 | `dto/BudgetControlConfigDTO.java:15` | 随 #2 一并处理 | 5 |

> 注：本次**未发现 P0 级阻断**（无 500 卡死、无跨模块回写断裂）。阶段 5/6 的支出合同不回写其他模块表，故不存在批次 1 阶段 4 的 `UnexpectedRollbackException` 类问题。

---

## 未验证 / 受阻项（如实列出）

| # | 项 | 分类 | 原因 | 影响 | 处置 |
|---|---|---|---|---|---|
| U1 | 劳务/机械合同审批**闭环到 EFFECTIVE 的 UI 实测**未完成 | DATA/ENV | 两者 BPMN 用候选组任务且受防自审约束，admin（唯一 token）既看不到候选组待办、又被 403 拒绝办理 | 只能证明到 SUBMITTED；EFFECTIVE 未真机验证 | 需第二账号（持 PROJECT_MANAGER/FINANCE_STAFF）方可闭环；**未用任何替代手段伪造** |
| U2 | `consistency/budget.spec.ts` 失败 | ENV | 并发批次 E2E 项目占据 `/v1/project/list` 首位，页面自动选中无预算项目 | 该用例无法在当前并行环境给出确定结论 | 已用 `GET /v1/budget/page?projectId=90001` 证明预算列表模块本身正常（返回种子 92001）；建议改用例（清单 #9） |
| U3 | L4（`lifecycle-sim-v2.sh`）在 tenant 9999 的复跑 | ENV/纪律 | 任务书明确「别在 tenant 1 跑它」；本批次仅授权 tenant 1，且 4 个批次并行，未在隔离租户执行以免与其它批次冲突 | 未取得 L4 层面的 stage_5/stage_6 PASS 记录 | 以本报告的 tenant 1 真机证据为准 |
| U4 | `npm run test` / `npm run build` 前端全量 | — | 本次未改动任何生产代码（仅临时 UI 用例，已删除），无编译/单测回归面 | 无 | 不适用 |

---

## 附录：动态验证证据索引

| 证据 | 命令 / 用例 | 关键输出 |
|---|---|---|
| 阶段 5 预算主链（创建+提交+回写） | Node `fetch` 探针（已删） | `POST /v1/budget → 200 code 200`；`submit → 200`；`status=APPROVED total=4000000`；`project.budgetAmount=4000000` |
| 阶段 5 重复 ORIGINAL 拦截 | 同上 | `code 500`「该项目已存在原始预算，不可重复创建」 |
| 阶段 5 全局默认配置失效 | 同上 | 以 `projectId=null` 连建 2 条配置均 `code 200`（未触发重复预检）；两条 `isDefault=0`；`GET /budget-control-configs/project/{pid}` 仍返回 `id=1 BLOCK/80 isDefault=1`（新建的「全局默认」未被采用） |
| 阶段 5 BLOCK 正向拦截（#1 生效） | 同上 | 第二份采购合同 600000 → `code 500`「科目[MATERIAL]预算执行率110.00%，已超预算」 |
| 阶段 5 `submit` 注解空转（#3） | 同上 | 两份 600000 采购合同；A 审批至 EFFECTIVE 后提交 B → `200 code 200`、`status=SUBMITTED`（若注解生效应 BLOCK） |
| 阶段 6 采购审批路径 | 同上 | submit → `SUBMITTED`（非自动生效）；完成 2 个待办（项目负责人审批→财务审核）后 → `EFFECTIVE` |
| 阶段 6 劳务/机械审批路径 | 同上 + 真库 | submit → `SUBMITTED`；`ACT_RU_TASK` 中任务 `ASSIGNEE_=NULL`（候选组）；`complete` → `code 403`「发起人不能审批自己的单据」 |
| 阶段 6 机械/分包预算空转（#4/#5） | 同上 | 机械 5000000（对 1000000 预算）创建+提交均 `200`；分包 5000000 创建 `200`、提交后直接 `EFFECTIVE` |
| 阶段 6 其他支付空转（#6） | 同上 | `POST /v1/finance/other-payment` 5000000 → `200 code 200` |
| 阶段 5/6 UI 全流程（真机无头 Chrome） | 临时用例 `_b2-ui.spec.ts`（已删），`npx playwright test --project=e2e-real --no-deps _b2-ui.spec.ts --workers=1 --retries=0` | **5 passed**：预算 `POST /v1/budget→200`、`submit→200`、tag「已批准」、`project.budgetAmount=500000`；采购 `→SUBMITTED` tag「审批中」；劳务 `→SUBMITTED` **tag「草稿」**；机械 `→SUBMITTED` **无状态列**；分包 `→EFFECTIVE` tag「生效」；四类提交后「提交审批」按钮均消失 |
| 既有 L5 套件 | `npx playwright test --project=e2e-real --no-deps a4-budget.spec.ts b5-purchase.spec.ts --workers=1 --retries=0` | `a4-budget` **10 passed**；`b5-purchase` **1 failed / 7 did not run**（`Expected "EFFECTIVE" / Received "SUBMITTED"`） |
| 一致性套件 | `--project=consistency-real budget.spec.ts purchase.spec.ts` | purchase **passed**；budget **failed**（首项项目无预算，环境所致） |
| 流程终止（应用层） | `POST /v1/workflow/approval/withdraw-by-business` | 劳务/机械/采购/分包流程均 `code 200 data=true`（**未直删任何 `ACT_*` 表**） |

**数据卫生（收尾自查）**
- ✅ 业务行（`deleted=0`）全部为 0：`biz_project` / `biz_budget` / `biz_budget_detail` / `biz_purchase_contract` / `biz_labor_contract` / `biz_machine_contract` / `biz_subcontract` / `biz_other_payment`（均按 `E2E_TEST_B2_%` 或本人 projectId 精确筛选，**未使用无差别前缀删除**）；
- ✅ 我创建的 2 条「全局默认」预算控制配置已删（`sys_budget_control_config` 中 `project_id IS NULL AND id<>1` = 0）；
- ✅ Flowable 侧：我的 6 个流程实例对应 `ACT_RU_TASK`=0、未结束 `ACT_HI_PROCINST`=0；`ACT_RU_TASK WHERE TENANT_ID_='1'` 总量 = **215**（与批次 1 基线一致，本人净增 0）；**全程未对 `ACT_*` 表执行任何 DELETE/UPDATE**；
- ✅ 临时文件已删：`keys/_b2_probe*.mjs`、`keys/_b2_cleanup*.mjs`、`zw-insight-web/e2e/tests/real/_b2-ui.spec.ts`、`zw-insight-web/test-results/`；
- ✅ 被一致性运行改写的受版本控制报告（`e2e/consistency/reports/{budget,purchase}.{json,md}`）已 `git checkout` 还原；
- ✅ `git status` 仅余**非本人**的未跟踪文件：`audit-reports/chain-verification-batch-1-2026-10-12.md`、`docs/全链路26阶段验收任务书.md`（批次 1 产物）、`e2e/tests/real/_b4-ui.spec.ts`、`_b5-ui.spec.ts`（批次 4/5 在跑的临时用例）——均未触碰。
