# 全链路验收报告 · 批次 6（阶段 9H / 9I / 9J / 9K / 9L / 10）

> 验收日期：2026-10-12
> 环境：主环境 `129.204.3.200`（tenant 1 演示租户；后端 :18080 / 前端 :18081，均实测）
> 权威定义：`keys/lifecycle-sim-v2.sh` 的 `stage_9h_entry_approval` / `stage_9i_reserve_fund` / `stage_9j_deposit_return` / `stage_9k_material_refund` / `stage_9l_material_transfer` / `stage_10_project_close`
> 方法：静态（`file:line`）+ 动态（真机 API 响应摘要、真机无头 Playwright 抓包；复用现有 storageState，未重新登录）
> 纪律遵守：全程 tenant 1；**未动 90001-90004 种子**（关闭链只做只读预检与负向拦截）；未本地起 JVM/mvn；未直删 `ACT_*` 表（在途流程走 `withdraw-by-business` 应用层收口）；写用例统一 `E2E_TEST_B6_` 前缀并已清净

---

## 汇总

| 阶段 | 后端链路 | 前端流程 | 关键缺口 |
|---|---|---|---|
| 9H HR 入职审批 | **通**（建号成功） | **部分通** | **建号不分配角色**（新账号可登录但 `roles=[]`/`permissions=[]`）；SUBMITTED 在列表回显为「草稿」；「编辑」按钮对非 DRAFT 行误显 |
| 9I 备用金 | **通** | **通** | 归还弹窗无「可归还余额」提示（靠后端拦截，超额提示清晰） |
| 9J 保证金退还 | **通** | **部分通** | 申请/退还内嵌于「投标报名」页弹窗，未逐一真机点击（静态 + 列表核对一致） |
| 9K 材料退货退款 | **通**（自动生成核实为真） | **通** | 退款扣减失败时返回通用 500，具体业务文案仅落日志 |
| 9L 材料调拨审批 | **通**（双向库存联动实测） | **通** | — |
| 10 项目关闭 | **部分通** | **部分通** | CLOSING→CLOSED **未端到端验证**（`PROJECT_CLOSE` 防自审 + 无非种子 COMPLETED 项目；唯一全过条件的 90004 是演示种子，关闭不可逆） |

**批次结论**：本批 6 个阶段的**单份单据顺流程**后端链路全部打通并产生正确数据与状态（含 9K 的退货→退款自动生成、9L 的双向库存联动、9I/9J 的超额负向拦截）。存在 **1 个 P1（入职建号不分配角色，产出「无权限幽灵账号」）**，以及若干 P2/P3 口径与提示缺陷；**阶段 10 的 CLOSING→CLOSED 未能端到端验证**（原因客观，非代码缺陷，见该阶段「断裂点/受阻」）。

---

## 阶段 9H：HR 入职审批

### 后端逻辑链路

- **数据模型**：`biz_entry_apply`（`zw-hr/.../domain/BizEntryApply.java:18` `@TableName`）；`status`（`:50`，DRAFT/SUBMITTED/APPROVED；驳回回退 DRAFT）；`workflowInstanceId`（`:53`）。系统账号落在 zw-system 的 `sys_user`（`zw-security/.../domain/SysUser.java`）。
- **状态机（三段，全部单点推进）**：
  1. 创建：`EntryApplyService.save:48-51` 硬置 `DRAFT`（无守卫，仅插入）。
  2. 提交：`EntryApplyService.submit:92-111`（`@Transactional`）→ 守卫仅 `DRAFT:97-99` → `approvalService.startProcess("ENTRY_APPLY", id, "entry_apply_approval", vars):105-106` → 置 `SUBMITTED:109`。
  3. 审批回调：`EntryApplyService.onApproved:117-149` → 幂等 CAS（仅 `SUBMITTED` 可推进，`:128-135`）→ 置 `APPROVED:136` → **自动建号** `sysUserService.save(user):147`。驳回：`onRejected:154-162` → `DRAFT`。
- **API（双向核对一致）**：
  - 前端 `src/api/hr.ts:17-19` `createHrEntry` → `POST /v1/hr/entry-apply` ↔ 后端 `EntryApplyController.java:18`（基路径）+ `:38-42` `@PostMapping`。
  - 前端 `src/api/hr.ts:29-32` `submitHrEntry` → `POST /v1/hr/entry-apply/{id}/submit` ↔ 后端 `EntryApplyController.java:57-61`（POST/PUT 双收）。
- **调用链**：审批完成 → `ProcessCompleteListener` 发 `ApprovalCompleteEvent(APPROVED)` → `HrApplyApprovalListener.onApprovalComplete:50-66`（`case ENTRY ->`）→ `EntryApplyService.onApproved`。驳回/撤回 → `ApprovalRejectEvent` → `onApprovalReject:72-106`（含陈旧实例防误触校验 `:88-94`）。
- **跨模块联动**：`onApproved:147` 调 `SysUserService.save` → **写 zw-system 的 `sys_user`**。**不写 `sys_user_role`**（见断裂点 P1-1）。
- **校验拦截**：
  - `submit` 非 DRAFT → `code 500`「仅草稿状态可提交」（`EntryApplyService.java:97-99`）。
  - `update` 非 DRAFT → 「仅草稿状态可编辑」（`:68`）。
  - `delete` 非 DRAFT 且非 E2E 标记 → 「仅草稿状态可删除」（`:80`）。
- **判定：通（建号成功）**。证据：`POST /hr/entry-apply` → `code 200`；`POST /{id}/submit` → `code 200`，状态 `DRAFT→SUBMITTED`；完成待办后 `GET /{id}` → `APPROVED`；`GET /system/user?username=e2e_b6_undefined` → `total=1`（账号已建，id=2109572526242840578）。

### 前端操作流程

1. **入口**：菜单「行政人事 > 入职申请」→ 路由 `/hr/entry`（`src/router/index.ts:794-796` → `src/views/hr/entry.vue`）。
2. **实测点击序列**（真机无头 Playwright，复用 storageState，抓包）：进 `/hr/entry` → `GET /v1/hr/entry-apply/page?page=1&size=10&realName=` → 列表渲染。
3. **实际请求**：`[A] 200 GET /api/v1/hr/entry-apply/page?page=1&size=10&realName=`（列表行含我的两条记录）。
4. **状态回显（实测）**：
   - `E2E_TEST_B6_HRSUBMIT_…`（后端实际 `status=SUBMITTED`）在列表标签显示为 **「草稿」**；`E2E_TEST_B6_HR_undefined`（`APPROVED`）显示「已通过」。→ **SUBMITTED 被误显示为草稿**（`entry.vue:25-27`：`row.status === 'APPROVED' ? '已通过' : '草稿'`）。
   - SUBMITTED 行显示「编辑」按钮（`entry.vue:32` 无条件渲染），但后端 `update` 仅允许 DRAFT（`:68`）→ 点「编辑」保存必失败。
   - DRAFT 行显示「编辑/提交/删除」，APPROVED 行仅「编辑」。
5. **边界**：表单必填 `realName/username/phone/orgId/postId`（`entry.vue:86-92`），与后端建号字段一致；客户端校验有效。
- **判定：部分通**。证据：状态标签把 SUBMITTED 显示为「草稿」（真机列表实测）；「编辑」按钮对非 DRAFT 行误显（静态 `entry.vue:32` vs `EntryApplyService.java:68`）。

### 断裂点

- **P1-1（入职建号不分配角色）**：`SysUserService.save:150-161` 只做唯一性校验 + 加密 + `userMapper.insert`，**全程不写 `sys_user_role`**；`assignRoles():267-296` 仅被 `SysUserController.assignRoles:126-127`（人工分配接口）调用，入职审批链路不触达。
  - **真机实证**：新建账号 `e2e_b6_undefined`（id=2109572526242840578）→ `sys_user_role` 计数 **0**；以其用户名/密码 `123456` 真实登录（读 Redis 图形验证码）→ `http 200 code 200`，**`roles=[]`，`permissions=[]`**。
  - 影响：审批通过「自动建号」产出的账号**可登录但零权限**（前端路由守卫 + 后端 `@RequiresPermission` 全拦），用户拿到的是一个进不去任何页面的「幽灵账号」。
- **P2-1（状态标签）**：`entry.vue:25-27` 缺 SUBMITTED（审批中）分支，SUBMITTED 与 DRAFT 同显示「草稿」。
- **P2-2（编辑按钮）**：`entry.vue:32` 无条件渲染「编辑」，与后端「仅 DRAFT 可编辑」（`EntryApplyService.java:68`）不对称。
- **P2-3（办公用品档案四列恒 0，横切档案链）**：`biz_office_supply` 实有 `current_stock/total_inbound/total_issued/last_inbound_date` 四列（DDL 实证），档案页（`src/views/archive/office-supply.vue:28-31`）展示这四列，但**全仓无写入方**：HR 出入库 `OfficeSupplyInOutService.submit:50-78` 只更新 `stock_quantity`；`zw-archive/.../BizOfficeSupply.java:32-41` 仅读取。
  - **真机实证**：新建用品 `E2E_TEST_B6_OFFICE_…`（id=2109572834658402305），提交一张 `IN quantity=50` 的出入库 → DB 复核 `stock_quantity=50` 而 `current_stock=0 / total_inbound=0 / total_issued=0 / last_inbound_date=NULL`；档案页 `/archive/office-supply` 实测该行显示「0 0 0 （空）」。
  - 附带观察（数据卫生，非代码）：tenant 1 现有 30 行 `biz_office_supply` 全部 `deleted=1`，故档案页在无新数据时为空。

---

## 阶段 9I：备用金

### 后端逻辑链路

- **数据模型**：`biz_reserve_fund_apply`（`BizReserveFundApply`；`status` DRAFT/SUBMITTED/APPROVED/REJECTED；`applyAmount/returnedAmount/offsetAmount`）；`biz_reserve_fund_return`（`BizReserveFundReturn`：`reserveApplyId/returnAmount/returnDate`）。
- **状态机**：
  - 创建：`ReserveFundApplyService.save:46-60` → 守卫「金额>0」`:48-50` → 置 `DRAFT`，`returnedAmount/offsetAmount` 归零 `:52-57`。
  - 提交：`submit:66-84`（`@Transactional`）→ 守卫 `DRAFT||REJECTED:71-73` → `startProcess("RESERVE_FUND_APPLY", id, "reserve_fund_apply_approval", vars):78-79` → `SUBMITTED:82`。
  - 审批回调：`onApproved:91-104` → `APPROVED`（幂等）；`onRejected:110-122` → `REJECTED`。
  - 归还：`ReserveFundReturnService.save:28-55`（`@Transactional`）→ 守卫「金额>0」`:36-38` → **超额拦截** `:46-48` → 插归还 `:50` → 回写 `returnedAmount += :53-54`。删除归还回冲 `delete:64-82`。
- **API（双向核对一致）**：
  - 前端 `src/api/finance.ts` `createReserveFundApply`/`submitReserveFundApply`/`createReserveFundReturn` ↔ 后端 `ReserveFundController.java:22`（基路径）+ `:41` `POST /apply` + `:46` `POST /apply/{id}/submit` + `:54` `POST /return`。
- **调用链**：审批完成 → `ReserveFundApplyApprovalListener` → `onApproved`。
- **跨模块联动**：无（仅本模块两张表；不写项目/付款累计）。
- **校验拦截**：金额>0；超额归还。**实测超额 `returnAmount=99999` → `code 500`「归还金额不能超过待归还金额：2000.00」**。
- **判定：通**。证据：`POST /finance/reserve-fund/apply {applyAmount:2000}` → `code 200`（`data=2109572930275950594`）；`submit` → `SUBMITTED`；完成待办 → `APPROVED`；超额归还被拒；全额归还 2000 → `returnedAmount=2000`。

### 前端操作流程

1. **入口**：菜单「财务管理 > 备用金管理」→ 路由 `/finance/reserve-fund`（`src/router/index.ts:333-335` → `src/views/finance/reserve-fund.vue`）。
2. **实测**：进页面 → `GET /v1/finance/reserve-fund/apply?page=1&size=10` → 列表行 `E2E_TEST_B6申请人 | 2,000.00 | 2,000.00 | 0.00 | 2026-10-12 | 已通过 | 归还`（状态回显正确、`归还` 按钮按 `APPROVED` 条件渲染 `reserve-fund.vue:54`）。
3. **边界**：新增弹窗必填 项目/申请人/金额/日期（`reserve-fund.vue:162-167`）；「提交」按钮条件 `DRAFT||REJECTED`（`:53`），「归还」条件 `APPROVED`（`:54`）——与后端状态机一致。
- **判定：通**。
- 备注（P3）：归还弹窗（`reserve-fund.vue:104-117`）无「可归还余额」提示，用户需先知道待还额；超额时后端提示清晰（「不能超过待归还金额：2000.00」）。

### 断裂点
（无阻断项。仅 P3 归还弹窗缺余额提示。）

---

## 阶段 9J：保证金退还

### 后端逻辑链路

- **数据模型**：`biz_deposit_apply`（`BizDepositApply`；`status` DRAFT/SUBMITTED/PAID）；`biz_deposit_return`（`BizDepositReturn`：`depositApplyId/returnAmount/returnDate`）。
- **状态机**：
  - 创建：`DepositApplyService.save:49-66` → **TI-1 法定上限守卫**：`depositAmount ≤ 项目预算 2%` `:51-63` → 置 `DRAFT:64`。
  - 提交：`submit:103-121`（`@Transactional`）→ 守卫仅 `DRAFT:108-110` → `startProcess("DEPOSIT_APPLY", id, "deposit_apply_approval", vars):115-116` → **`SUBMITTED:119`（不再提交即 PAID）**。
  - 审批回调：`onApproved:127-134` → **`PAID`**（幂等）；`onRejected:140-147` → `DRAFT`。
  - 退还登记：`DepositReturnService.save:44-64` → 守卫「金额>0」`:45-47` → **累计退还 ≤ 缴纳额**（超额拦截 `:59-62`）→ 插记录 `:63`。
- **API（双向核对一致）**：
  - 前端 `src/api/tender.ts:76` `createTenderDeposit` → `POST /v1/tender/deposit/apply` ↔ 后端 `DepositController.java:22` + `:41`；`tender.ts:80` submit ↔ `:47`；`tender.ts:114` `createTenderRefund` → `POST /v1/tender/deposit/return` ↔ `:76`。
- **调用链**：审批完成 → `DepositApplyApprovalListener.onApprovalComplete:33-42` → `onApproved`（置 PAID）。
- **跨模块联动**：无。
- **校验拦截**：2% 上限；超额退还。**实测**：`depositAmount=999999`（预算 100 万，cap=20000）→ `code 500`「投标保证金（999999）超过项目预算金额 2% 法定上限（20000.0000），请调整」；超额退还 `99999` → `code 500`「退还金额超过可退余额，当前最大可退金额：5000.00」。
- **判定：通**。证据：`DRAFT → submit → SUBMITTED → 完成待办 → PAID → 退还登记`，退还记录 `returnAmount=5000`。

### 前端操作流程

1. **入口**：保证金申请/退还内嵌于「投标管理 > 投标报名」页的弹窗 `src/views/tender/components/DepositDialog.vue`（`mode='apply'|'refund'`），由 `register.vue` 触发。
2. **静态核对**：`DepositDialog.vue:166-172` `createTenderDeposit`（申请，带 2% 合规提醒 `:16-18`）；`:179-183` `createTenderRefund`（退还）；退还模式提示 TI-4「必须启动退还追踪」`:28-30`。字段与后端一致（`depositAmount/paymentDate` / `returnAmount/returnDate`）。
3. **边界**：`depositAmount` 默认取报名保证金（`watch:138-151`）；必填校验 `:131-136`。
- **判定：部分通**。说明：后端链路以 API 全链实测通过，前端为**内嵌弹窗**，本次未对其逐一真机点击（弹窗需先从报名列表选中行），仅做静态双向核对 + 页面可达性；如实标注为「部分通」。

### 断裂点
（无阻断项。前端退还弹窗未逐一 UI 实测，属验证覆盖不足而非缺陷。）

---

## 阶段 9K：材料退货退款

### 后端逻辑链路

- **数据模型**：`biz_material_outbound`（`outboundType=RETURN`、`returnType=RETURN_REFUND`）；`biz_material_refund`（`status` DRAFT/PENDING/APPROVED/CANCELED；`refundAmount/contractId/outboundId`）；`biz_material_refund_detail`。
- **状态机 / 自动生成（核实为真）**：
  1. 退货出库：`MaterialOutboundService.save:63-101`（`@Transactional`）→ 逐行**原子扣库存**（`deductStock` 排他下限 `:91-94`）→ `RETURN` 且 `contractId≠null` → `publishReturnEvent:98-99`。
  2. **事件同步生成退款**：`MaterialReturnRefundEventListener.onMaterialReturn:33-45`（`@EventListener` + `@Transactional`，与外层同事务）→ `MaterialRefundService.createRefundFromReturn:61-114`：建单 `DRAFT:67` → 按 `Σ(quantity×入库单价)` 算金额 `:70-77` → `startProcess("MATERIAL_REFUND", id, "material_refund_approval", vars):102-103` → **置 `PENDING:106`**。
  3. 审批通过：`MaterialRefundService.onRefundApproved:126-170`（`@EventListener`）→ 幂等「仅 PENDING」`:145-148` → `APPROVED:151` → **扣减采购合同累计已付** `purchaseContractPayMapper.deductPaid(contractId, refundAmount):163`（下限守卫 `:164-166`）。
- **API（双向核对一致）**：
  - 退货出库 `POST /api/v1/material/outbound`（`OutboundController`，真机调用通过）。
  - 退款查询 前端 `src/api/material.ts:132` `getMaterialRefundPage` → `GET /v1/material/refund` ↔ 后端 `MaterialRefundController.java:27` + `:47`。
- **调用链**：退货 `save`（同事务内 Spring 同步事件）→ 退款建单+起流程；审批完成 → `ProcessCompleteListener` → `ApprovalCompleteEvent` → `onRefundApproved`。
- **跨模块联动**：写 **zw-purchase 的 `biz_purchase_contract.cumulative_paid`**（原子扣减 `PurchaseContractPayMapper.deductPaid`）。实测 `80000 → 75000`。
- **校验拦截**：出库数量>0（`:82-84`）；库存排他下限（`:91-94`）；退款扣减下限（`:164-166`）。
- **判定：通（自动生成核实为真）**。证据链：入库 2000 块（合同累计入库 100000）→ 退货出库 100 块 → **`GET /material/refund?contractId=…` 自动出现 `status=PENDING、refundAmount=5000`（`workflowInstanceId` 非空）** → 完成待办 → 退款 `APPROVED` 且合同 `cumulativePaid 80000→75000`。

### 前端操作流程

1. **入口**：菜单「材料库存 > 退货退款」→ 路由 `/material/refund`（`src/router/index.ts:629-631` → `src/views/material/refund.vue`）。
2. **实测**：进页面 → `GET /v1/material/refund?page=1&size=10` → 列表出现我的退款单 `2109573419533123586 | 5,000.00 | 已通过 | 明细`（状态回显正确，`refund.vue:96-101`）。
3. **边界**：页面明示只读（`refund.vue:18-24` alert「退款记录由退货出库审批通过后自动生成，此处仅供查询」）；无新增/审批按钮（符合「自动生成」设计）。
- **判定：通**。

### 断裂点

- **P2-1（退款扣减失败返回通用 500）**：当采购合同累计已付不足以扣减时，`MaterialRefundService.java:165` 抛 `BusinessException("退款失败：采购合同不存在或退款金额超过合同累计已付款")`，但经审批完成链路后由 `GlobalExceptionHandler` 归为**通用 500「系统内部错误，请稍后重试」**。
  - **真机实证**：合同 `cumulativePaid=0` 时完成退款待办 → `http 500 code 500 msg=系统内部错误，请稍后重试`；后端日志可见真实原因 `com.zwinsight.common.exception.BusinessException: 退款失败：采购合同不存在或退款金额超过合同累计已付款 at MaterialRefundService.onRefundApproved(MaterialRefundService.java:165)` 与 `GlobalExceptionHandler: 系统异常 [/api/v1/workflow/approval/complete]: 审批业务生效失败`。
  - 影响：用户仅见「系统内部错误」，无法定位；具体文案只落日志。退款状态保持 `PENDING`（事务回滚，幂等正确）。
- 附注：本阶段正向验证时，因自建合同 `cumulativePaid=0`（未走付款链），我把**自建测试合同**（id=2109573419533123586）的 `cumulative_paid` 直接置为 `80000.00` 作为测试夹具（仅此一行、`WHERE id=… AND tenant_id=1` 守卫），以驱动扣减路径；该行随后随项目级联清理。

---

## 阶段 9L：材料调拨审批

### 后端逻辑链路

- **数据模型**：`biz_material_transfer`（`status` DRAFT/SUBMITTED/APPROVED/REJECTED；`fromProjectId/toProjectId`）；`biz_material_transfer_detail`。
- **状态机（审批后生效）**：
  - 创建：`MaterialTransferService.save:69-80` → **同项目守卫** `validateDistinctProject:72`（实现 `:192-196`）→ 置 `DRAFT:73` → 仅落单与明细，**不动库存** `:75-79`。
  - 提交：`submit:205-220` → 守卫 `DRAFT||REJECTED:208-210` → `startProcess("MATERIAL_TRANSFER", id, "material_transfer_approval", vars):215-216` → `SUBMITTED:218`。
  - 审批通过：`onApproved:87-109`（`@Transactional`）→ 幂等 `:93-96` → 逐行 `applyStockChange:128-165` → 置 `APPROVED:106`。
  - 驳回：`onRejected:115-123` → `REJECTED`（库存未变，无需回滚）。
- **API（双向核对一致）**：
  - 前端 `src/api/material.ts:64` `createMaterialTransfer` → `POST /v1/material/transfer` ↔ 后端 `TransferController.java:22` + `:43`；`material.ts:76` submit ↔ `:62`；`material.ts:56` page ↔ `:29`。
- **调用链**：审批完成 → `MaterialTransferApprovalListener.onApprovalComplete:32-41` → `onApproved`。
- **跨模块联动**：写 zw-material 自己的 `biz_project_material_stock`——**调出方减**（`transferOut`，`:138`）+ **调入方增**（`transferIn` 或新建行，`:147-164`）。这是「双向库存联动」。
- **校验拦截**：同项目禁止（`:192-196`，实测 `code 500`「调出项目与调入项目不能相同」）；调出库存不足（`:138-140`）。
- **判定：通（双向库存联动实测）**。证据：调拨前源库存 `1900`；`save → submit → 完成待办 → APPROVED`；**调拨后源 `stockQuantity=1700 / totalTransferOut=200`，调入方新行 `stockQuantity=200 / totalTransferIn=200`**（200 块×单价 50）。

### 前端操作流程

1. **入口**：菜单「材料库存 > 材料调拨」→ 路由 `/material/transfer`（`src/router/index.ts:611-613` → `src/views/material/transfer.vue`）。
2. **实测**：进页面 → `GET /v1/project/page?page=1&size=200`（项目下拉）+ `GET /v1/material/transfer/page?page=1&size=10` → 列表首行 `E2E_TEST_B6_PROJ_A… → E2E_TEST_B6_PROJ_B… | 2026-10-06 | 已审批`（状态回显正确）。
3. **边界**：客户端同项目守卫 `transfer.vue:170`、明细非空 `:171`；行内按钮按状态收敛（`DRAFT` 才有 编辑/删除，`DRAFT||REJECTED` 才有 提交 `:47-49`）。
4. **分页参数澄清（非缺陷）**：`transfer.vue:128` 用 `pageNum/pageSize`，但全局请求拦截器 `src/utils/request.ts:60-68` 已将其归一为 `page/size`；真机抓包实证 URL 为 `?page=1&size=10`。
- **判定：通**。

### 断裂点
（无。`MaterialTransferApprovalListener` 与 `MaterialRefundService.onRefundApproved` 均有幂等守卫。）

---

## 阶段 10：项目关闭

### 后端逻辑链路

- **数据模型**：`biz_project.status`（`BizProject.java:106`）：`COMPLETED →(APPLY_CLOSE) CLOSING →(APPROVE_CLOSE) CLOSED`；驳回 `CLOSING →(REJECT_CLOSE) COMPLETED`。
- **状态机 / 推进**：
  - 发起：`ProjectService.closeProject:540-567`（`@Transactional`）→ 先 `checkCloseConditions:542`，`allPassed≠true` 则抛 `BusinessException("无法结项：" + 原因):552` → 满足则 `startProcess("PROJECT_CLOSE", id, "project_close_approval", vars):561-562` + `stateMachine.fire(APPLY_CLOSE):566` → `CLOSING`。
  - 审批通过：`onCloseApproved:572-579` → `stateMachine.fire(APPROVE_CLOSE)` → `CLOSED`。驳回：`onCloseRejected:584-591` → `COMPLETED`。
- **close-check（7 条件，4 阻断）**：`checkCloseConditions:601-656`，逐条 `addCondition(..., blocking)` `:662-673`：
  | # | 条件 | blocking | 判定来源 |
  |---|---|---|---|
  | 1 | 项目已竣工验收（`status=COMPLETED`） | ✅阻断 | `:612-614` |
  | 2 | 应收账款已结清（`receivable_amount ≤ 100`） | ✅阻断 | `:617-620` |
  | 2b | 应付款项已结清 | ❌非阻断 | `:623-626` |
  | 2c | 质保金已退还 | ❌非阻断 | `:629-632` |
  | 2d | 保证金/备用金已结清 | ❌非阻断 | `:635-639` |
  | 3 | 项目已进入施工阶段（非 DRAFT/FILED） | ✅阻断 | `:642-644` |
  | 4 | 最终结算已审批（`countApprovedSettlement>0`） | ✅阻断 | `:647-649` |
- **API（双向核对一致）**：
  - 前端 `src/api/project.ts:102-104` `closeProject` → `POST /v1/project/{id}/close` ↔ 后端 `ProjectController.java:159-163`。
  - 前端 `src/api/project.ts:98` `getProjectCloseCheck` → `GET /v1/project/{id}/close-check` ↔ 后端 `ProjectController.java:168-171`。
- **调用链**：审批完成 → `ProjectCloseListener.onApprovalComplete:32-41` → `onCloseApproved`；驳回 → `onApprovalReject:47-54` → `onCloseRejected`。
- **跨模块联动**：无写（仅改 `biz_project.status`）。
- **校验拦截（实测负向均正确）**：
  - `GET /project/90004/close-check` → **`allPassed=true`**（7 条全过）。
  - `GET /project/90002/close-check` → `allPassed=false`（`应收账款已结清: 应收台账仍有余额: 2600000.00`；另质保金 1675000 未退为非阻断）。
  - `GET /project/90001/close-check` → `allPassed=false`（未竣工验收 + 未最终结算，均阻断）。
  - `POST /project/{PROJ_A}/close`（自建 DRAFT）→ `code 500`「无法结项：项目已竣工验收…；项目已进入施工阶段…；最终结算已审批…」，状态**保持 DRAFT**（守卫先于任何写）。
  - `POST /project/90002/close` → `code 500`「无法结项：应收账款已结清: 应收台账仍有余额: 2600000.00」，状态**保持 COMPLETED**（守卫先于任何写，种子未被改动）。
- **判定：部分通**。预检（7 条件/4 阻断）与负向拦截**已端到端实测**；**`COMPLETED→CLOSING→CLOSED` 正向链未端到端验证**（客观受阻，见下）。

### 前端操作流程

1. **入口**：菜单「项目管理 > 项目列表」→ 路由 `/project/list`（`src/router/index.ts:160-166` → `src/views/project/index.vue`）。
2. **实测点击序列**（真机无头 Playwright，抓包）：
   - 搜索「城北河道」（90004，已竣工）→ 行内**有「结项」按钮**（条件 `row.status === 'COMPLETED'`，`index.vue:91`）。
   - 搜索「城南市政道路」（90002，已竣工但应收未清）→ 点「结项」→ 前端先 `GET /v1/project/90002/close-check` → 弹 `el-message-box`「**无法结项 / 应收账款已结清: 应收台账仍有余额: 2600000.00**」，**未发出任何 `POST /project/{id}/close`**（抓包断言 `POST …/close sent: 0`）。→ 前端预检拦截有效，且未触碰种子。
   - 搜索自建 `E2E_TEST_B6_PROJ_A`（草稿）→ 行内**无「结项」按钮**。
3. **状态回显**：`index.vue:163` 状态映射含 `CLOSING: 结项审批中`，列表标签可回显。
4. **边界**：`handleClose:303-316` 先 `getProjectCloseCheck`，`!allPassed` 则 `ElMessageBox.alert(reasons,'无法结项')` 并 `return`（不发 close）；通过才二次确认。
- **判定：部分通**。证据：`[P1b]` 90004 行含「结项」；`[P2]` 90002 点结项 → alert 文案命中 + `POST /close sent: 0`；`[P3]` 自建 DRAFT 行无「结项」。

### 断裂点 / 受阻（如实登记）

- **受阻（CLOSING→CLOSED 未端到端验证）**，两个客观原因：
  1. **无非种子可结项项目**：tenant 1 中 `status=COMPLETED` 的项目仅 `90002`（应收未清，close-check 不过）与 `90004`（全过）。`90004` 是**演示种子**，任务书明令「不要对 90004 或任何演示种子发起真实 close」（CLOSED 不可逆）；自建项目需先跑完「合同→竣工→最终结算」全链方可到 COMPLETED，超出本批范围。
  2. **`PROJECT_CLOSE` 防自审**：`PROJECT_CLOSE` 在 `ApprovalService.SELF_APPROVAL_GUARDED_TYPES:61-65`；`assertTaskAssignee:937-949` 中 `assertNotInitiator(task,userId):943` **先于** SUPER_ADMIN 放行 `:947`，故发起人（admin，tenant 1 唯一可用凭证）**不能办理自己的结项审批**（`code 403`「发起人不能审批自己的单据」）；而任务纪律禁止重新登录 admin（`max-devices=5`，多智能体并行），tenant 1 无第二个可用账号。→ 即便造出 COMPLETED 项目，`CLOSING→CLOSED` 的完成待办也无法执行。
  - 处置：**如实标注为受阻**，未做任何降级/伪造；`COMPLETED→CLOSING`（发起）与 `CLOSING→COMPLETED`（撤回）亦未在种子/自建项目上真实触发（前者需 COMPLETED，后者依赖前者）。
  - 说明：该「防自审」本身是**安全设计**（防发起人自审），非缺陷；但对「单管理员租户」形成运营约束——项目结项需至少两名审批参与者。
- 数据观察（非缺陷）：`90004` 的 close-check `allPassed=true`，一旦在 UI 误点「结项」并确认将不可逆进入 CLOSED；本次**未点击**（仅只读预检 + 在 `90002` 上做负向拦截）。

---

## 按严重度排序的修复清单

| # | 严重度 | 问题 | 位置 | 建议修复 | 影响阶段 |
|---|---|---|---|---|---|
| 1 | **P1** | **入职建号不分配角色**：审批通过自动建号，但 `save` 不写 `sys_user_role`；新账号可登录却 `roles=[]`/`permissions=[]`（零权限幽灵账号） | `SysUserService.java:150-161`（save）、`:267`（assignRoles 仅被 `SysUserController.java:126-127` 调用）；`EntryApplyService.java:147` | 入职申请单增「角色/默认角色」字段，`onApproved` 建号后调用 `assignRoles(userId, roleIds)`；或在 `save` 提供默认角色参数。补「建号后具备预期角色/权限」回归用例 | 9H |
| 2 | P2 | 退款扣减失败返回**通用 500**「系统内部错误」，具体业务文案（「退款失败：…超过合同累计已付款」）仅落日志 | `MaterialRefundService.java:165`；`GlobalExceptionHandler`（`complete` 链路包装为「审批业务生效失败」） | 让审批回执链路透出 `BusinessException` 的具体 `code/message`（对齐本项目「已知约束错误须给具体提示」约定），或至少在响应中携带业务原因 | 9K |
| 3 | P2 | HR 入职列表把 `SUBMITTED` 回显为「草稿」（缺「审批中」分支） | `src/views/hr/entry.vue:25-27` | 补 `SUBMITTED→审批中` 映射 | 9H |
| 4 | P2 | HR 入职列表对非 DRAFT 行仍渲染「编辑」按钮，点击保存必被后端拒 | `src/views/hr/entry.vue:32` vs `EntryApplyService.java:68` | 「编辑」按钮加 `v-if="row.status==='DRAFT'"` | 9H |
| 5 | P2 | 办公用品档案四列（`current_stock/total_inbound/total_issued/last_inbound_date`）**无写入方**，档案页恒显示 0 | 写入侧缺：`OfficeSupplyInOutService.submit:50-78`（只写 `stock_quantity`）；读取侧 `zw-archive/.../BizOfficeSupply.java:32-41`、`views/archive/office-supply.vue:28-31` | 在 HR 出入库 `submit` 内同步维护四列（IN：`total_inbound+=qty`、`current_stock`、`last_inbound_date`；OUT：`total_issued+=qty`、`current_stock`），或档案页改读 `stock_quantity` 并对齐字段 | 9H / 档案链 |
| 6 | P3 | 备用金归还弹窗无「可归还余额」提示 | `src/views/finance/reserve-fund.vue:104-117` | 打开归还弹窗时回显待归还额（申请额−已归还−已冲抵） | 9I |
| 7 | P3 | 保证金退还弹窗未逐一真机 UI 实测（覆盖不足） | `src/views/tender/components/DepositDialog.vue` | 补 UI 用例：报名行 → 申请 → 审批 → 退还弹窗 | 9J（验证） |
| 8 | P3 | 环境数据卫生：tenant 1 现有 30 行 `biz_office_supply` 全部 `deleted=1`，档案页在无新数据时为空 | `biz_office_supply`（tenant 1） | 视需要恢复/重建演示办公用品数据 | 9H / 档案链 |

### 运营约束（非缺陷，需知悉）

| # | 事项 | 现状 | 影响 |
|---|---|---|---|
| O1 | `PROJECT_CLOSE` 防自审（`ApprovalService.java:61-65/943/947`） | 发起人不能办理自己的结项审批 | 单管理员租户无法独立完成项目结项，需≥2 名审批参与者；本次 CLOSING→CLOSED 因此未验证 |

---

## 附录：动态验证证据索引

| 证据 | 命令 / 用例 | 关键输出 |
|---|---|---|
| 9H 创建→提交 | Node `fetch` 探针 | `POST /hr/entry-apply → code 200`；`POST /{id}/submit → code 200`，`status: DRAFT→SUBMITTED` |
| 9H 审批→建号 | Node `fetch` + 按 `PROC_INST_ID_` 取 taskId 后 `POST /workflow/approval/complete` | `complete → code 200`；`GET /hr/entry-apply/{id} → APPROVED`；`GET /system/user?username=… → total=1`（id=2109572526242840578） |
| 9H 建号无角色（P1-1） | 直连生产库 + 真机登录 | `sys_user_role WHERE user_id=… → 0`；以 `e2e_b6_undefined/123456` 登录 → `code 200`，`roles=[] permissions=[]` |
| 9H 状态标签 / 编辑按钮 | Playwright（复用 storageState，抓包） | `[A]` 行 `E2E_TEST_B6_HRSUBMIT_… 草稿 编辑`（后端实为 SUBMITTED）；`APPROVED` 行显示「已通过」 |
| 9H 办公用品四列恒 0（P2-3） | 建用品→`IN 50`→直连库 + UI | `stock_quantity=50` 而 `current_stock=0/total_inbound=0/total_issued=0/last_inbound_date=NULL`；档案页该行显示「0 0 0 （空）」 |
| 9I 全链 + 超额负向 | Node `fetch` 探针 | `apply → DRAFT`；`submit → SUBMITTED`；`complete → APPROVED`；超额 `99999 → code 500 归还金额不能超过待归还金额：2000.00`；`return 2000 → returnedAmount=2000` |
| 9I UI | Playwright | `[B]` 行 `E2E_TEST_B6申请人 2,000.00 2,000.00 0.00 2026-10-12 已通过 归还` |
| 9J 全链 + 负向 | Node `fetch` 探针 | `register → REGISTERED`；`deposit/apply → DRAFT`；`submit → SUBMITTED`；`complete → PAID`；`return 5000 → 记录 returnAmount=5000`；超额 `99999 → code 500 退还金额超过可退余额，当前最大可退金额：5000.00`；2% 上限 `999999 → code 500 …超过项目预算金额 2% 法定上限（20000.0000）` |
| 9K 自动生成 + 扣减 | Node `fetch` 探针 + 后端日志 | 退货出库 → `GET /material/refund?contractId=…` 出现 `status=PENDING refundAmount=5000`（wf 非空）；`complete → APPROVED`，合同 `cumulativePaid 80000→75000` |
| 9K 扣减失败通用 500（P2-1） | `cumulativePaid=0` 时完成待办 | `http 500 code 500 系统内部错误，请稍后重试`；日志 `BusinessException: 退款失败：… at MaterialRefundService.java:165` + `GlobalExceptionHandler: 系统异常 … 审批业务生效失败` |
| 9K UI | Playwright | `[C]` 行 `2109573419533123586 5,000.00 已通过 明细` |
| 9L 双向库存联动 | Node `fetch` 探针 | 调拨前源 `1900`；`APPROVED` 后源 `1700 / totalTransferOut=200`，调入方 `200 / totalTransferIn=200`；同项目 `code 500 调出项目与调入项目不能相同` |
| 9L UI | Playwright | `[D]` 行 `E2E_TEST_B6_PROJ_A… → E2E_TEST_B6_PROJ_B… 2026-10-06 已审批` |
| 10 close-check（只读） | Node `fetch` 探针 | 90004 `allPassed=true`；90002 `allPassed=false`（应收 2600000）；90001 `allPassed=false`（未竣工 + 未最终结算）；自建 DRAFT `allPassed=false`（3 条阻断） |
| 10 负向拦截 | Node `fetch` 探针 | `POST /project/{PROJ_A}/close → code 500 无法结项：…`，状态保持 `DRAFT`；`POST /project/90002/close → code 500 无法结项：应收账款…2600000.00`，状态保持 `COMPLETED` |
| 10 UI 条件渲染 + 预检 | Playwright（抓包） | `[P1b]` 90004 行含「结项」；`[P2]` 90002 点结项 → alert「无法结项 / 应收账款已结清: 应收台账仍有余额: 2600000.00」，`POST /project/{id}/close sent: 0`；`[P3]` 自建 DRAFT 行无「结项」 |
| 分页参数归一（澄清） | 源码 + 抓包 | `request.ts:60-68` 将 `pageNum/pageSize` 归一为 `page/size`；`/material/transfer/page?page=1&size=10`（非缺陷） |

**数据卫生（收尾自查）**：
- ✅ 全部 `E2E_TEST_B6_` 业务数据已清净（`deleted=0` 残留全为 **0**）：`biz_project`(2→0)、`biz_entry_apply`(2→0)、`sys_user`(1→0)、`biz_office_supply`(1→0)、`biz_office_supply_in_out`(1→0)、`biz_project_material_stock`(→0)、`biz_material_inbound/outbound/refund`(→0)、`biz_purchase_contract`(→0)、`biz_reserve_fund_apply/return`(→0)、`biz_tender_register`/`biz_deposit_apply`/`biz_deposit_return`(→0)、`sys_budget_control_config`(→0)、`biz_material_transfer(_detail)`(→0)。
- ✅ 在途流程经应用层收口：`POST /workflow/approval/withdraw-by-business?businessType=ENTRY_APPLY&businessId=…`（**未直删 `ACT_*`**）；我方 6 个流程实例 `ACT_HI_PROCINST` 无 `END_TIME_ IS NULL` 的 RUNNING 残留；`ACT_RU_TASK` 按 `PROC_INST_ID_` 过滤计数 **0**。
- ✅ 临时文件已删（`keys/_b6_api.mjs`、`keys/_b6_find_task.sh`、`zw-insight-web/_b6_ui{,2,3}.mjs`）；`git status` 无本次新增残留（其余未跟踪项属批次 1/2/4/5）。
- ✅ **90001-90004 种子未被改动**：`90001=CONSTRUCTION / 90002=COMPLETED / 90003=FILED / 90004=COMPLETED`（与验收开始时一致）；合同额/支出额未变；对 90002 的 close 调用为纯负向（守卫先于写，状态保持 COMPLETED）；对 90004 **仅只读 close-check，未发起 close**。
- ⚠️ 披露：为驱动 9K 扣减路径，曾把**自建测试合同** `2109573419533123586` 的 `cumulative_paid` 置 `80000.00`（`WHERE id=… AND tenant_id=1`）；该合同已随自建项目级联清理，未触及任何种子。
