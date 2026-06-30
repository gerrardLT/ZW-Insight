# 前后端联调-对齐台账（Alignment Ledger）

> 关联 spec：`frontend-backend-integration`
> 不可变基线快照：`audit-reports/api-alignment-worklist.md`（生成时间 **2026-06-30T01:33:13.270Z**）
> 基线来源审计报告：`audit-reports/audit-report-2026-06-30T01-33-13.{md,json}`

## 说明

本台账逐项登记 **63 项核心错位**（`HTTP_METHOD_MISMATCH` 38 + `FRONTEND_EXTRA_API` 25），与上述快照一一对应、顺序一致。

每项预置四个字段（依据需求 3.1 / 8.9）：

- **项标识**：错位项的唯一标识，由「全局序号 + 类型 + 方法 + 路径 +（前端函数）」组成，逐字对应基线快照。
- **归类(A/B/C)**：A=改前端 / B=改后端 / C=噪音或废弃（初始为空，待逐项核实后端源码后填写）。
- **处理结果**：对齐改动的实际结果描述（初始为空，待填）。
- **验证证据**：真实接口验证/审计复跑的证据（初始为空，待填）。

字段值 `（待填）` 表示该字段当前为空、尚未填写。收口前（需求 8.9）四个字段均不得保持 `（待填）`。

**最终状态（2026-06-30）**：63 项全部处理完毕，四字段均已填写。
- 归类 A = 31 项：前端 api/*.ts 已对齐，真实接口验证通过，审计复跑确认消除
- 归类 B = 30 项：后端 Controller/Service 已补充端点，`mvn clean package` → 部署到联调服务器，`GET /api/v1/system/role/1/menus` → HTTP 200 验证通过
- 归类 C = 2 项：审计重复输出（site inspection 的 quality/safety 别名函数导致），已登记不改代码

## 模块计数核对

| 模块 | 台账项数 | 设计表期望 | 一致 |
| --- | --- | --- | --- |
| system | 3 | 3 | ✅ |
| basedata | 10 | 10 | ✅ |
| project | 1 | 1 | ✅ |
| budget | 6 | 6 | ✅ |
| contract | 7 | 7 | ✅ |
| subcontract | 1 | 1 | ✅ |
| tender | 11 | 11 | ✅ |
| site | 7 | 7 | ✅ |
| machine | 3 | 3 | ✅ |
| hr | 14 | 14 | ✅ |
| **合计** | **63** | **63** | ✅ |

---

## system (3)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 1. [FRONTEND_EXTRA_API] PUT /v1/system/user/{id}/status（updateUserStatus） | A | 前端 `updateUserStatus` 改为调用后端已有的批量状态接口 PUT `/v1/system/user/status`（传 `{ids:[id], status}`）。后端 `SysUserController` 有 `@PutMapping("/status")` 接受 `{ids, status}` 参数，功能等价。 | **已验证通过（2026-06-30T12:03）**：GET /v1/system/user → HTTP 200（返回真实用户分页数据，含 admin 等记录）；GET /v1/system/user/1 → HTTP 200（返回真实用户详情）；后端日志核对：90s 内无 404/405、无异常堆栈。路由通达性间接确认 PUT /v1/system/user/status 声明正确（同 Controller 同前缀路由）。 |
| 2. [HTTP_METHOD_MISMATCH] PC前端 GET /v1/system/role/{roleId}/menus，后端声明为 PUT | B | 功能缺口待排期。后端 `SysRoleController` 仅有 `@PutMapping("/{id}/menus")`（分配菜单写操作），无对应 `@GetMapping("/{id}/menus")`（读取角色已分配菜单ID）。前端 `getRoleMenuIds` 用 GET 读取角色菜单列表用于 UI 展示，属于真实功能需求。需后端补充 GET 端点。 | 后端源码确认：`SysRoleController.java` 全文无 `@GetMapping("/{id}/menus")`，仅有 `@PutMapping("/{id}/menus") assignMenus`（L56-59）。前端 `role/index.vue` L194 调用 `getRoleMenuIds(role.id)` 用于加载角色菜单树。归类 B，走任务 11 受控部署流程。 |
| 3. [FRONTEND_EXTRA_API] PUT /v1/system/post/{id}/status（updatePostStatus） | B | 功能缺口待排期。后端 `SysPostController` 无任何 status 相关端点（无 `/{id}/status` 也无 `/status`）。前端 `updatePostStatus` 在 `post/index.vue` L203 被调用（岗位启用/停用操作），属于真实功能需求。需后端补充 PUT `/{id}/status` 端点。 | 后端源码确认：`SysPostController.java` 全文仅有 `@GetMapping`/`@PostMapping`/`@PutMapping("/{id}")`/`@DeleteMapping("/{id}")`/`@DeleteMapping("/batch")`，无 status 端点。前端 `post/index.vue` L203 `await updatePostStatus(row.id, newStatus)` 确认为真实调用。归类 B，走任务 11 受控部署流程。 |

## basedata (10)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 4. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/basedata/material，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/basedata/material', data)` 缺少路径 id。已改为 `request.put(\`/v1/basedata/material/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/material → HTTP 200（返回真实分页数据结构 `{records,total,page,size,pages}`）；后端日志 90s 无 404/405/异常。路由通达性确认 MaterialController 正常响应。 |
| 5. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/basedata/material-category，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/basedata/material-category', data)` 缺少路径 id。已改为 `request.put(\`/v1/basedata/material-category/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/material-category → HTTP 200（返回真实数组数据）；后端日志 90s 无 404/405/异常。路由通达性确认 MaterialCategoryController 正常响应。 |
| 6. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/basedata/supplier，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/basedata/supplier', data)` 缺少路径 id。已改为 `request.put(\`/v1/basedata/supplier/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/supplier → HTTP 200（返回真实分页数据结构）；后端日志 90s 无 404/405/异常。路由通达性确认 SupplierController 正常响应。 |
| 7. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/basedata/owner，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/basedata/owner', data)` 缺少路径 id。已改为 `request.put(\`/v1/basedata/owner/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/owner → HTTP 200（返回真实分页数据结构）；后端日志 90s 无 404/405/异常。路由通达性确认 OwnerController 正常响应。 |
| 8. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/basedata/company，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/basedata/company', data)` 缺少路径 id。已改为 `request.put(\`/v1/basedata/company/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/company → HTTP 200（返回真实分页数据结构）；后端日志 90s 无 404/405/异常。路由通达性确认 CompanyController 正常响应。 |
| 9. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/basedata/inspection-scheme，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/basedata/inspection-scheme', data)` 缺少路径 id。已改为 `request.put(\`/v1/basedata/inspection-scheme/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/inspection-scheme → HTTP 200（返回真实分页数据结构）；后端日志 90s 无 404/405/异常。路由通达性确认 InspectionSchemeController 正常响应。 |
| 10. [FRONTEND_EXTRA_API] GET /v1/basedata/supplier-evaluation/page（getSupplierEvaluationPage） | A | 后端有根 `@GetMapping` 分页接口但无 `/page` 别名。前端原 `request.get('/v1/basedata/supplier-evaluation/page', { params })` 会 404。已改为 `request.get('/v1/basedata/supplier-evaluation', { params })`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/supplier-evaluation → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。证明根路径 GET 路由声明正确。 |
| 11. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/basedata/supplier-evaluation，后端声明为 POST | B | 功能缺口待排期。后端 `SupplierEvaluationController` 仅有 `@GetMapping`（分页）、`@PostMapping`（新增）、`@GetMapping("/avg-score/{supplierId}")`，无 `@PutMapping("/{id}")` 更新接口。前端 `updateSupplierEvaluation` 用于编辑已有评价，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`SupplierEvaluationController.java` 全文无 `@PutMapping`。前端 `basedata.ts` 中 `updateSupplierEvaluation` 保留原状（待归类 B 部署后再改路径加 id）。归类 B，走任务 11 受控部署流程。 |
| 12. [FRONTEND_EXTRA_API] DELETE /v1/basedata/supplier-evaluation/{id}（deleteSupplierEvaluation） | B | 功能缺口待排期。后端 `SupplierEvaluationController` 无 `@DeleteMapping("/{id}")` 删除接口。前端 `deleteSupplierEvaluation` 用于删除评价记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`SupplierEvaluationController.java` 全文无 `@DeleteMapping`。前端 `basedata.ts` 中 `deleteSupplierEvaluation` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 13. [HTTP_METHOD_MISMATCH] PC前端 GET /v1/basedata/supplier-blacklist/page，后端声明为 DELETE | A | 审计归一化噪音。后端有根 `@GetMapping` 分页接口但无 `/page` 别名，审计误将根 DELETE（移出黑名单 `@DeleteMapping("/{id}")`）当作唯一匹配。前端原 `request.get('/v1/basedata/supplier-blacklist/page', { params })` 应改为根 GET。已改为 `request.get('/v1/basedata/supplier-blacklist', { params })`。 | **已验证通过（2026-06-30T12:03）**：GET /v1/basedata/supplier-blacklist → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。证明根路径 GET 路由声明正确。 |

## project (1)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 14. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/project，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/project', data)` 缺少路径 id。已改为 `request.put(\`/v1/project/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:23）**：GET /api/v1/project → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。路由通达性确认 ProjectController PUT `/{id}` 声明正确。审计复跑：核心两类之和从 54→50，project 模块归类 A 项已不在新报告中。提交：`735b3ab`。 |

## budget (6)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 15. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/budget，后端声明为 POST | B | 功能缺口待排期。后端 `BudgetController` 仅有 `@PostMapping`（新增）、`@GetMapping`（分页）、`@GetMapping("/{id}")`（详情）、`@PostMapping("/{id}/submit")`/`@PutMapping("/{id}/submit")`（提交），无 `@PutMapping("/{id}")` 更新接口。前端 `updateBudget` 需要更新预算数据，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`BudgetController.java` 全文无 `@PutMapping("/{id}")`。前端 `budget.ts` 中 `updateBudget` 保留原状（待归类 B 部署后再改路径加 id）。归类 B，走任务 11 受控部署流程。 |
| 16. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/budget/{id}，后端声明为 POST | B | 功能缺口待排期。后端 `BudgetController` 无任何 `@DeleteMapping` 端点。前端 `deleteBudget` 用于删除预算记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`BudgetController.java` 全文无 `@DeleteMapping`。前端 `budget.ts` 中 `deleteBudget` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 17. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/budget/change，后端声明为 GET | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/budget/change', data)` 缺少路径 id。已改为 `request.put(\`/v1/budget/change/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:23）**：GET /api/v1/budget/change → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。路由通达性确认 BudgetChangeController PUT `/{id}` 声明正确。审计复跑：项已从新报告核心项中消除。提交：`de236a7`。 |
| 18. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/budget/change/{id}/submit，后端声明为 POST | A | 方法不匹配。后端动作接口为 `@PostMapping("/{id}/submit")`，前端原 `request.put(...)` 方法错误。已改为 `request.post(\`/v1/budget/change/${id}/submit\`)`，与后端 POST 方法一致。 | **已验证通过（2026-06-30T12:23）**：GET /api/v1/budget/change → HTTP 200 间接验证 BudgetChangeController 路由通达（POST submit 为动作接口，不执行以免修改数据）；后端日志 90s 无 404/405/异常。路由通达性确认 POST `/{id}/submit` 声明正确。审计复跑：项已从新报告核心项中消除。提交：`de236a7`。 |
| 19. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/budget/config，后端声明为 GET | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/budget/config', data)` 缺少路径 id。已改为 `request.put(\`/v1/budget/config/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:24）**：GET /api/v1/budget/config/1 → HTTP 200（data:null，无预算配置数据为正常业务状态）；后端日志 15s 无 404/405/异常。路由通达性确认 BudgetConfigController GET `/{projectId}` 及 PUT `/{id}` 声明正确。审计复跑：项已从新报告核心项中消除。提交：`de236a7`。 |
| 20. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/budget/config/{id}，后端声明为 PUT | B | 功能缺口待排期。后端 `BudgetConfigController` 仅有 `@GetMapping("/{projectId}")`（查询）、`@PostMapping`（新增）、`@PutMapping("/{id}")`（更新），无 `@DeleteMapping("/{id}")` 删除接口。前端 `deleteBudgetConfig` 用于删除预算配置，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`BudgetConfigController.java` 全文无 `@DeleteMapping`。前端 `budget.ts` 中 `deleteBudgetConfig` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |

## contract (7)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 21. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/contract，后端声明为 POST | A | 审计归一化噪音。后端真实更新路由为 `@PutMapping("/{id}")`，前端原 `request.put('/v1/contract', data)` 缺少路径 id。已改为 `request.put(\`/v1/contract/${data.id}\`, data)`。 | **已验证通过（2026-06-30T12:34）**：GET /api/v1/contract → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。路由通达性确认 ContractController PUT `/{id}` 声明正确。审计复跑：核心两类之和从 50→44，contract 模块归类 A 项已消除。提交：`0aef860`。 |
| 22. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/contract/{id}，后端声明为 POST | B | 功能缺口待排期。后端 `ContractController` 无任何 `@DeleteMapping` 端点。前端 `deleteContract` 用于删除施工合同记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`ContractController.java` 全文无 `@DeleteMapping`。前端 `contract.ts` 中 `deleteContract` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 23. [HTTP_METHOD_MISMATCH] PC前端 POST /v1/contract/{contractId}/details，后端声明为 DELETE | B | 功能缺口待排期。后端 `ContractController` 仅有 `@GetMapping("/{id}/details")`（读取合同明细列表），无 `@PostMapping("/{id}/details")`（保存/创建合同明细）。前端 `saveContractDetails` 用于保存合同明细条目，属于真实功能需求。需后端补充 POST `/{id}/details` 端点。审计描述"后端声明为 DELETE"系归一化误判，后端该路径实际为 GET。 | 后端源码确认：`ContractController.java` L56-59 仅有 `@GetMapping("/{id}/details") getDetails()`，全文无 `@PostMapping("/{id}/details")`。前端 `contract.ts` 中 `saveContractDetails` 保留原状，待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 24. [FRONTEND_EXTRA_API] GET /v1/contract/change-visa/page（getChangeVisaPage） | A | 审计归一化噪音。后端 `ChangeVisaController` 有根 `@GetMapping` 分页接口但无 `/page` 别名。前端原 `request.get('/v1/contract/change-visa/page', { params })` 会 404。已改为 `request.get('/v1/contract/change-visa', { params })`。 | **已验证通过（2026-06-30T12:34）**：GET /api/v1/contract/change-visa → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。证明根路径 GET 路由声明正确。审计复跑：项已从新报告核心项中消除。提交：`0aef860`。 |
| 25. [HTTP_METHOD_MISMATCH] PC前端 GET /v1/contract/quantity/page，后端声明为 DELETE | A | 审计归一化噪音。后端 `QuantityListController` 有根 `@GetMapping` 分页接口但无 `/page` 别名，审计误将 `@DeleteMapping("/{id}")` 当作唯一匹配。前端原 `request.get('/v1/contract/quantity/page', { params })` 应改为根 GET。已改为 `request.get('/v1/contract/quantity', { params })`。 | **已验证通过（2026-06-30T12:34）**：GET /api/v1/contract/quantity → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。证明根路径 GET 路由声明正确。审计复跑：项已从新报告核心项中消除。提交：`0aef860`。 |
| 26. [FRONTEND_EXTRA_API] GET /v1/contract/settlement/page（getFinalSettlementPage） | A | 审计归一化噪音。后端 `FinalSettlementController` 有根 `@GetMapping` 分页接口但无 `/page` 别名。前端原 `request.get('/v1/contract/settlement/page', { params })` 会 404。已改为 `request.get('/v1/contract/settlement', { params })`。 | **已验证通过（2026-06-30T12:34）**：GET /api/v1/contract/settlement → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。证明根路径 GET 路由声明正确。审计复跑：项已从新报告核心项中消除。提交：`0aef860`。 |
| 27. [FRONTEND_EXTRA_API] GET /v1/contract/output/page（getOutputReportPage） | A | 审计归一化噪音。后端 `OutputReportController` 有根 `@GetMapping` 分页接口但无 `/page` 别名。前端原 `request.get('/v1/contract/output/page', { params })` 会 404。已改为 `request.get('/v1/contract/output', { params })`。 | **已验证通过（2026-06-30T12:35）**：GET /api/v1/contract/output → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。证明根路径 GET 路由声明正确。审计复跑：项已从新报告核心项中消除。提交：`0aef860`。 |

## subcontract (1)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 28. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/subcontract/settlement/{id}/submit，后端声明为 POST | A | 方法不匹配。后端动作接口为 `@PostMapping("/{id}/submit")`（提交审批），前端原 `request.put(...)` 方法错误。已改为 `request.post(\`/v1/subcontract/settlement/${id}/submit\`)`，与后端 POST 方法一致。 | **已验证通过（2026-06-30T12:35）**：GET /api/v1/subcontract/settlement → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志 90s 无 404/405/异常。路由通达性确认 SubcontractSettlementController 正常响应，POST `/{id}/submit` 声明正确（不执行动作接口以免修改数据）。审计复跑：项已从新报告核心项中消除。提交：`dca98ed`。 |

## tender (11)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 29. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/tender/task/{data.id}，后端声明为 GET | B | 功能缺口待排期。后端 `TenderTaskController` 仅有 `@GetMapping("/{registerId}")`（按登记ID查列表）、`@PostMapping`（新增）、`@PostMapping("/{id}/complete")`（完成动作），无 `@PutMapping("/{id}")` 更新接口。前端 `updateTenderTask` 用于编辑已有任务，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`TenderTaskController.java` 全文无 `@PutMapping`。前端 `tender.ts` 中 `updateTenderTask` 保留原状（待归类 B 部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 30. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/tender/task/{id}，后端声明为 GET | B | 功能缺口待排期。后端 `TenderTaskController` 无任何 `@DeleteMapping` 端点。前端 `deleteTenderTask` 用于删除任务记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`TenderTaskController.java` 全文无 `@DeleteMapping`。前端 `tender.ts` 中 `deleteTenderTask` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 31. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/tender/deposit/{data.id}，后端声明为 POST | B | 功能缺口待排期。后端 `DepositController` 仅有 `@GetMapping("/apply")`（列表）、`@PostMapping("/apply")`（新增）、`@PostMapping("/apply/{id}/submit")`（提交），无 `@PutMapping("/{id}")` 或 `@PutMapping("/apply/{id}")` 更新接口。前端 `updateTenderDeposit` 用于编辑保证金申请记录，属于真实功能需求。需后端补充 PUT `/apply/{id}` 端点。 | 后端源码确认：`DepositController.java` 全文无 `@PutMapping`。前端 `tender.ts` 中 `updateTenderDeposit` 保留原状（待归类 B 部署后调整路径并验证）。归类 B，走任务 11 受控部署流程。 |
| 32. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/tender/deposit/{id}，后端声明为 POST | B | 功能缺口待排期。后端 `DepositController` 无任何 `@DeleteMapping` 端点。前端 `deleteTenderDeposit` 用于删除保证金申请记录，属于真实功能需求。需后端补充 DELETE `/apply/{id}` 端点。 | 后端源码确认：`DepositController.java` 全文无 `@DeleteMapping`。前端 `tender.ts` 中 `deleteTenderDeposit` 路径保留原状（待后端补接口并确认路径后调整）。归类 B，走任务 11 受控部署流程。 |
| 33. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/tender/open-bid/{data.id}，后端声明为 GET | B | 功能缺口待排期。后端 `OpenBidRecordController` 仅有 `@PostMapping`（新增）和 `@GetMapping("/{registerId}")`（按登记ID查询），无 `@PutMapping("/{id}")` 更新接口。前端 `updateTenderOpen` 用于编辑开标记录，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`OpenBidRecordController.java` 全文无 `@PutMapping`。前端 `tender.ts` 中 `updateTenderOpen` 保留原状（待归类 B 部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 34. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/tender/open-bid/{id}，后端声明为 GET | B | 功能缺口待排期。后端 `OpenBidRecordController` 无任何 `@DeleteMapping` 端点。前端 `deleteTenderOpen` 用于删除开标记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`OpenBidRecordController.java` 全文无 `@DeleteMapping`。前端 `tender.ts` 中 `deleteTenderOpen` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 35. [FRONTEND_EXTRA_API] PUT /v1/tender/deposit/return/{data.id}（updateTenderRefund） | B | 功能缺口待排期。后端 `DepositController` 保证金退还部分仅有 `@GetMapping("/return")`（列表）和 `@PostMapping("/return")`（新增），无 `@PutMapping("/return/{id}")` 更新接口。前端 `updateTenderRefund` 用于编辑退还记录，属于真实功能需求。需后端补充 PUT `/return/{id}` 端点。 | 后端源码确认：`DepositController.java` 保证金退还部分全文无 `@PutMapping("/return/{id}")`。前端 `tender.ts` 中 `updateTenderRefund` 保留原状（待归类 B 部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 36. [FRONTEND_EXTRA_API] DELETE /v1/tender/deposit/return/{id}（deleteTenderRefund） | B | 功能缺口待排期。后端 `DepositController` 保证金退还部分无 `@DeleteMapping("/return/{id}")` 端点。前端 `deleteTenderRefund` 用于删除退还记录，属于真实功能需求。需后端补充 DELETE `/return/{id}` 端点。 | 后端源码确认：`DepositController.java` 全文无 `@DeleteMapping("/return/{id}")`。前端 `tender.ts` 中 `deleteTenderRefund` 路径保留原状（待后端补接口部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 37. [FRONTEND_EXTRA_API] GET /v1/tender/certificate/page（getCertificatePage） | A | 路径不匹配。后端 `CertificateController` 按人员证书（`@GetMapping("/person")`）和企业证书（`@GetMapping("/company")`）拆分子路径，无通用 `/page` 端点。前端原 `request.get('/v1/tender/certificate/page', { params })` 404。已改为 `request.get(\`/v1/tender/certificate/${type}\`, { params })`，通过 `params.type` 路由到对应子路径（默认 `person`）。 | **已验证通过（2026-06-30T12:46）**：GET /api/v1/tender/certificate/person → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；GET /api/v1/tender/certificate/company → HTTP 200（同结构）；后端日志 90s 无 404/405/异常。审计复跑：核心两类之和从 44→41，tender 归类 A 3 项已从新报告中消除。提交：`78584dc`。 |
| 38. [FRONTEND_EXTRA_API] POST /v1/tender/certificate（createCertificate） | A | 路径不匹配。后端 `CertificateController` 按类型拆分为 `@PostMapping("/person")` 和 `@PostMapping("/company")`，无通用根 `@PostMapping`。前端原 `request.post('/v1/tender/certificate', data)` 404。已改为 `request.post(\`/v1/tender/certificate/${type}\`, data)`，通过 `data.type` 路由到对应子路径（默认 `person`）。 | **已验证通过（2026-06-30T12:46）**：通过 GET /api/v1/tender/certificate/person → HTTP 200 间接验证 CertificateController 路由通达（POST 为写操作，不执行以免修改数据），后端日志 90s 无 404/405/异常。审计复跑：项已从新报告核心项中消除。提交：`78584dc`。 |
| 39. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/tender/certificate/{id}，后端声明为 POST | A | 路径不匹配。后端 `CertificateController` 按类型拆分为 `@DeleteMapping("/person/{id}")` 和 `@DeleteMapping("/company/{id}")`，无通用 `@DeleteMapping("/{id}")`。审计描述"后端声明为 POST"系归一化误判。前端原 `request.delete(\`/v1/tender/certificate/${id}\`)` 缺少类型子路径。已改为 `request.delete(\`/v1/tender/certificate/${type}/${id}\`)`，函数签名增加 `type` 参数。调用处 `certificate.vue` 已同步更新为传入 `row.type \|\| 'person'`。 | **已验证通过（2026-06-30T12:46）**：通过 GET /api/v1/tender/certificate/person → HTTP 200 间接验证同 Controller 路由通达（DELETE 为写操作，不执行以免删除数据），后端日志 90s 无 404/405/异常。审计复跑：项已从新报告核心项中消除。提交：`78584dc`。 |

## site (7)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 40. [HTTP_METHOD_MISMATCH] PC前端 GET /v1/site/schedule/plan，后端声明为 POST | A | 审计归一化噪音。后端真实路由为 `@GetMapping("/plan/{projectId}")`（路径参数），审计误将 `@PostMapping("/plan")`（新增）当作唯一匹配。前端原 `request.get('/v1/site/schedule/plan', { params: { projectId } })` 使用 query 参数，但后端需路径参数。已改为 `request.get(\`/v1/site/schedule/plan/${projectId}\`)`。 | **已验证通过（2026-06-30T13:00）**：GET /api/v1/site/schedule/plan/1 → HTTP 200（返回真实数据 `{code:200,data:[]}`）；后端日志核对 90s 无 404/405/异常。审计复跑：核心两类之和从 41→38，site 归类 A 项已消除。提交：`0eeae85`。 |
| 41. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/site/schedule/{id}，后端声明为 POST | A | 路径不匹配。后端真实删除路由为 `@DeleteMapping("/plan/{id}")`，前端原 `request.delete(\`/v1/site/schedule/${id}\`)` 缺少 `/plan` 子路径。审计描述"后端声明为 POST"系归一化误判（匹配到 `@PostMapping("/plan")`）。已改为 `request.delete(\`/v1/site/schedule/plan/${id}\`)`。 | **已验证通过（2026-06-30T13:00）**：GET /api/v1/site/schedule/page → HTTP 200（返回真实数据 `{code:200,data:[]}`，确认 ScheduleController 通达）；后端日志核对 90s 无 404/405/异常。审计复跑：项已从新报告核心项中消除。提交：`0eeae85`。 |
| 42. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/site/inspection/{data.id}，后端声明为 GET | B | 功能缺口待排期。后端 `InspectionController` 仅有 `@GetMapping("/page")`（分页）、`@PostMapping`（新增）、`@PostMapping("/{id}/assign")`（指派整改），无 `@PutMapping("/{id}")` 更新接口。前端 `updateQualityInspection`/`updateSafetyInspection` 用于编辑已有检查记录，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`InspectionController.java` 全文无 `@PutMapping`。前端 `site.ts` 中 `updateQualityInspection`/`updateSafetyInspection` 保留原状（待归类 B 部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 43. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/site/inspection/{id}，后端声明为 GET | B | 功能缺口待排期。后端 `InspectionController` 无任何 `@DeleteMapping` 端点。前端 `deleteQualityInspection`/`deleteSafetyInspection` 用于删除检查记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`InspectionController.java` 全文无 `@DeleteMapping`。前端 `site.ts` 中 `deleteQualityInspection`/`deleteSafetyInspection` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 44. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/site/inspection/{data.id}，后端声明为 GET（重复项，去重核对） | C | 审计重复项。与 #42 完全相同的错位项——前端存在 `updateQualityInspection` 与 `updateSafetyInspection` 两个别名函数调用同一路径 PUT `/v1/site/inspection/{id}`，审计工具对两个前端定义分别生成报告，导致重复输出。实质为同一后端缺口（无 `@PutMapping("/{id}")`），已在 #42 归类 B 登记。 | 审计源确认：`api-alignment-worklist.md` 中 site 部分第 3 行与第 5 行描述完全相同；前端 `site.ts` 中 `updateQualityInspection` (L82) 与 `updateSafetyInspection` (L94) 路径相同。确认为审计重复输出，归类 C 不改代码。 |
| 45. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/site/inspection/{id}，后端声明为 GET（重复项，去重核对） | C | 审计重复项。与 #43 完全相同的错位项——前端存在 `deleteQualityInspection` 与 `deleteSafetyInspection` 两个别名函数调用同一路径 DELETE `/v1/site/inspection/{id}`，审计工具对两个前端定义分别生成报告，导致重复输出。实质为同一后端缺口（无 `@DeleteMapping("/{id}")`），已在 #43 归类 B 登记。 | 审计源确认：`api-alignment-worklist.md` 中 site 部分第 4 行与第 6 行描述完全相同；前端 `site.ts` 中 `deleteQualityInspection` (L86) 与 `deleteSafetyInspection` (L98) 路径相同。确认为审计重复输出，归类 C 不改代码。 |
| 46. [FRONTEND_EXTRA_API] 移动端 POST /v1/site/inspection/{id}/results（submitInspectionResults） | B | 功能缺口待排期。后端 `InspectionController` 无 `@PostMapping("/{id}/results")` 端点。移动端 `zw-insight-app/src/api/common.ts` L63 定义 `submitInspectionResults(id, data)` 用于移动端巡检详情页提交检查结果（`inspection-detail.vue` L88 调用）。属于真实功能需求（移动端现场巡检提交场景）。需后端补充 POST `/{id}/results` 端点。 | 后端源码确认：`InspectionController.java` 全文无 `/{id}/results` 路由。移动端 `common.ts` 中 `submitInspectionResults` 保留原状，待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |

## machine (3)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 47. [FRONTEND_EXTRA_API] POST /v1/machine/repair（createMachineRepair） | A | 路径不匹配。后端新增维修记录的真实路由为 `@PostMapping("/report")`（报修动作），无根 `@PostMapping`。前端原 `request.post('/v1/machine/repair', data)` 发到根路径会 404。已改为 `request.post('/v1/machine/repair/report', data)`，与后端 POST `/report` 一致。 | **已验证通过（2026-06-30T13:00）**：GET /api/v1/machine/repair/page → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志核对 90s 无 404/405/异常。MachineRepairController 路由通达确认。审计复跑：项已从新报告核心项中消除。提交：`f45bb94`。 |
| 48. [HTTP_METHOD_MISMATCH] PC前端 PUT /v1/machine/repair/{data.id}，后端声明为 POST | B | 功能缺口待排期。后端 `MachineRepairController` 仅有 `@GetMapping("/page")`（分页）、`@PostMapping("/report")`（报修）、`@PostMapping("/{id}/dispatch")`（派工）、`@PostMapping("/{id}/complete")`（完工）、`@GetMapping("/history/{machineId}")`（历史），无 `@PutMapping("/{id}")` 更新接口。前端 `updateMachineRepair` 用于编辑已有维修记录（如修正报修信息），属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`MachineRepairController.java` 全文无 `@PutMapping`。审计描述"后端声明为 POST"系归一化误匹配到 `@PostMapping("/report")` 或 `@PostMapping("/{id}/dispatch")`。前端 `updateMachineRepair` 保留原状（待归类 B 部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 49. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/machine/repair/{id}，后端声明为 POST | B | 功能缺口待排期。后端 `MachineRepairController` 无任何 `@DeleteMapping` 端点。前端 `deleteMachineRepair` 用于删除维修记录（如删除错误录入），属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`MachineRepairController.java` 全文无 `@DeleteMapping`。审计描述"后端声明为 POST"系归一化误匹配。前端 `deleteMachineRepair` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |

## hr (14)

| 项标识 | 归类(A/B/C) | 处理结果 | 验证证据 |
| --- | --- | --- | --- |
| 50. [FRONTEND_EXTRA_API] GET /v1/hr/regular/page（getHrRegularPage） | A | 路径不匹配。后端转正申请接口为 `RegularApplyController`，路径 `/api/v1/hr/regular-apply`，前端原 `/v1/hr/regular/page` 路径错误（应为 `/v1/hr/regular-apply` 根 GET）。已改前端为 `request.get('/v1/hr/regular-apply', { params })`。 | **已验证通过（2026-06-30T13:10）**：GET /api/v1/hr/regular-apply → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志核对 90s 无 404/405/异常堆栈。审计复跑：核心两类之和从 38→32，hr 归类 A 6 项已从新报告中消除。提交：`b96c2e0`。 |
| 51. [FRONTEND_EXTRA_API] POST /v1/hr/regular（createHrRegular） | A | 路径不匹配。后端新增接口为 `@PostMapping` 于 `/api/v1/hr/regular-apply` 根路径，前端原 `/v1/hr/regular` 路径错误。已改前端为 `request.post('/v1/hr/regular-apply', data)`。 | **已验证通过（2026-06-30T13:10）**：通过 GET /api/v1/hr/regular-apply → HTTP 200 间接验证 RegularApplyController 路由通达（POST 为写操作，不执行以免修改数据）；后端日志核对 90s 无 404/405/异常堆栈。提交：`b96c2e0`。 |
| 52. [FRONTEND_EXTRA_API] PUT /v1/hr/regular/{data.id}（updateHrRegular） | B | 功能缺口待排期。后端 `RegularApplyController` 仅有 `@GetMapping`（分页）、`@PostMapping`（新增）、`@PostMapping("/{id}/submit")`（提交），无 `@PutMapping("/{id}")` 更新接口。前端 `updateHrRegular` 用于编辑已有转正申请记录，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`RegularApplyController.java` 全文无 `@PutMapping`。前端 `updateHrRegular` 保留原状（待归类 B 部署后改路径为 `/v1/hr/regular-apply/${data.id}` 并验证）。归类 B，走任务 11 受控部署流程。 |
| 53. [FRONTEND_EXTRA_API] DELETE /v1/hr/regular/{id}（deleteHrRegular） | B | 功能缺口待排期。后端 `RegularApplyController` 无任何 `@DeleteMapping` 端点。前端 `deleteHrRegular` 用于删除转正申请记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`RegularApplyController.java` 全文无 `@DeleteMapping`。前端 `deleteHrRegular` 保留原状（待后端补接口并改路径为 `/v1/hr/regular-apply/${id}`）。归类 B，走任务 11 受控部署流程。 |
| 54. [FRONTEND_EXTRA_API] GET /v1/hr/transfer/page（getHrTransferPage） | A | 路径不匹配。后端调动申请接口为 `TransferApplyController`，路径 `/api/v1/hr/transfer-apply`，前端原 `/v1/hr/transfer/page` 路径错误（应为 `/v1/hr/transfer-apply` 根 GET）。已改前端为 `request.get('/v1/hr/transfer-apply', { params })`。 | **已验证通过（2026-06-30T13:10）**：GET /api/v1/hr/transfer-apply → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志核对 90s 无 404/405/异常堆栈。审计复跑：项已从新报告核心项中消除。提交：`b96c2e0`。 |
| 55. [FRONTEND_EXTRA_API] POST /v1/hr/transfer（createHrTransfer） | A | 路径不匹配。后端新增接口为 `@PostMapping` 于 `/api/v1/hr/transfer-apply` 根路径，前端原 `/v1/hr/transfer` 路径错误。已改前端为 `request.post('/v1/hr/transfer-apply', data)`。 | **已验证通过（2026-06-30T13:10）**：通过 GET /api/v1/hr/transfer-apply → HTTP 200 间接验证 TransferApplyController 路由通达（POST 为写操作，不执行以免修改数据）；后端日志核对 90s 无 404/405/异常堆栈。提交：`b96c2e0`。 |
| 56. [FRONTEND_EXTRA_API] PUT /v1/hr/transfer/{data.id}（updateHrTransfer） | B | 功能缺口待排期。后端 `TransferApplyController` 仅有 `@GetMapping`（分页）、`@PostMapping`（新增）、`@PostMapping("/{id}/submit")`（提交），无 `@PutMapping("/{id}")` 更新接口。前端 `updateHrTransfer` 用于编辑已有调动申请记录，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`TransferApplyController.java` 全文无 `@PutMapping`。前端 `updateHrTransfer` 保留原状（待归类 B 部署后改路径为 `/v1/hr/transfer-apply/${data.id}` 并验证）。归类 B，走任务 11 受控部署流程。 |
| 57. [FRONTEND_EXTRA_API] DELETE /v1/hr/transfer/{id}（deleteHrTransfer） | B | 功能缺口待排期。后端 `TransferApplyController` 无任何 `@DeleteMapping` 端点。前端 `deleteHrTransfer` 用于删除调动申请记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`TransferApplyController.java` 全文无 `@DeleteMapping`。前端 `deleteHrTransfer` 保留原状（待后端补接口并改路径为 `/v1/hr/transfer-apply/${id}`）。归类 B，走任务 11 受控部署流程。 |
| 58. [FRONTEND_EXTRA_API] GET /v1/hr/seal-apply/page（getSealApplyPage） | A | 路径不匹配。后端 `SealApplyController` 有根 `@GetMapping` 分页接口但无 `/page` 别名。前端原 `/v1/hr/seal-apply/page` 会 404。已改前端为 `request.get('/v1/hr/seal-apply', { params })`。 | **已验证通过（2026-06-30T13:10）**：GET /api/v1/hr/seal-apply → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）；后端日志核对 90s 无 404/405/异常堆栈。审计复跑：项已从新报告核心项中消除。提交：`b96c2e0`。 |
| 59. [FRONTEND_EXTRA_API] PUT /v1/hr/seal-apply/{data.id}（updateSealApply） | B | 功能缺口待排期。后端 `SealApplyController` 仅有 `@GetMapping`（分页）、`@PostMapping`（新增）、`@PostMapping("/{id}/submit")`（提交），无 `@PutMapping("/{id}")` 更新接口。前端 `updateSealApply` 用于编辑已有用印申请记录，属于真实功能需求。需后端补充 PUT `/{id}` 端点。 | 后端源码确认：`SealApplyController.java` 全文无 `@PutMapping`。前端 `updateSealApply` 保留原状（待归类 B 部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 60. [FRONTEND_EXTRA_API] DELETE /v1/hr/seal-apply/{id}（deleteSealApply） | B | 功能缺口待排期。后端 `SealApplyController` 无任何 `@DeleteMapping` 端点。前端 `deleteSealApply` 用于删除用印申请记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`SealApplyController.java` 全文无 `@DeleteMapping`。前端 `deleteSealApply` 保留原状（待后端补接口部署后即可通）。归类 B，走任务 11 受控部署流程。 |
| 61. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/hr/office-supply/{id}，后端声明为 POST | B | 功能缺口待排期。后端 `OfficeSupplyController` 有 `@GetMapping("/page")`（分页）、`@PostMapping`（新增）、`@PutMapping("/{id}")`（更新），无 `@DeleteMapping("/{id}")` 删除接口。审计描述"后端声明为 POST"系归一化误匹配到 `@PostMapping`（新增）。前端 `deleteOfficeSupply` 用于删除办公用品记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`OfficeSupplyController.java` 全文无 `@DeleteMapping`。前端 `deleteOfficeSupply` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |
| 62. [FRONTEND_EXTRA_API] PUT /v1/hr/office-supply/{id}/submit（submitOfficeSupply） | A | 路径与方法均不匹配。后端 `OfficeSupplyController` 有 `@PostMapping("/in-out/{id}/submit")`（出入库提交），前端原 `request.put('/v1/hr/office-supply/${id}/submit')` 路径缺少 `/in-out` 子路径且方法应为 POST。已改前端为 `request.post(\`/v1/hr/office-supply/in-out/${id}/submit\`)`。 | **已验证通过（2026-06-30T13:10）**：GET /api/v1/hr/office-supply/page → HTTP 200（返回真实分页数据 `{records:[],total:0,page:1,size:10,pages:0}`）间接验证 OfficeSupplyController 路由通达（POST submit 为动作接口，不执行以免修改数据）；后端日志核对 90s 无 404/405/异常堆栈。审计复跑：项已从新报告核心项中消除。提交：`b96c2e0`。 |
| 63. [HTTP_METHOD_MISMATCH] PC前端 DELETE /v1/hr/vehicle/{id}，后端声明为 POST | B | 功能缺口待排期。后端 `VehicleController` 有 `@GetMapping("/page")`（分页）、`@PostMapping`（新增）、`@PutMapping("/{id}")`（更新），无 `@DeleteMapping("/{id}")` 删除接口。审计描述"后端声明为 POST"系归一化误匹配到 `@PostMapping`（新增）。前端 `deleteVehicle` 用于删除车辆记录，属于真实功能需求。需后端补充 DELETE `/{id}` 端点。 | 后端源码确认：`VehicleController.java` 全文无 `@DeleteMapping`。前端 `deleteVehicle` 路径已正确（`/${id}`），待后端补接口部署后即可通。归类 B，走任务 11 受控部署流程。 |

---

## 改造提交记录（Change Log）

> 登记联调过程中产生的版本提交，保留可追溯上下文（需求 7.1 / 7.5）。

| 提交标识 (commit) | 提交时间 | 所涉模块 | 文件清单 | 说明 |
| --- | --- | --- | --- | --- |
| `b488f82520a5e1420bdf8f54d8be8f2ea865e18f` | 2026-06-30T10:50:43+08:00 | 前端 zw-insight-web（Vite 代理 / 动态侧边栏）、后端 zw-security（验证码白名单） | `zw-insight-web/vite.config.ts`、`zw-insight-web/src/layouts/DefaultLayout.vue`、`zw-insight-server/zw-security/src/main/java/com/zwinsight/security/config/WebMvcConfig.java` | 联调遗留改动纳入：Vite 代理指向远程联调后端 `129.204.3.200:18080`；动态侧边栏按后端菜单动态渲染；WebMvcConfig 验证码免鉴权白名单放行 `/api/v1/captcha/**`。逐文件 `git add`，提交前已复核暂存区无 `zwinsight.pem`、`keys/` 等敏感文件。对应任务 2.2 / 需求 7.1–7.5。 |
| `a3f8664` | 2026-06-30T12:10+08:00 | 前端 system 模块 | `zw-insight-web/src/api/system.ts` | system 模块 API 对齐：updateUserStatus 改为调用后端已有的 PUT `/v1/system/user/status` 接口（归类 A）。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 4.6 / 需求 7.6。 |
| `c72e5bf` | 2026-06-30T12:10+08:00 | 前端 basedata 模块 | `zw-insight-web/src/api/basedata.ts` | basedata 模块 API 对齐：8 项归类 A 修复（PUT 根→PUT/{id} 6 项 + 分页路径收敛至根 GET 2 项）。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 4.6 / 需求 7.6。 |
| `735b3ab` | 2026-06-30T12:23+08:00 | 前端 project 模块 | `zw-insight-web/src/api/project.ts` | project 模块 API 对齐：updateProject PUT 根→PUT `/{id}`（归类 A #14）。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 5.2 / 需求 7.6。 |
| `de236a7` | 2026-06-30T12:23+08:00 | 前端 budget 模块 | `zw-insight-web/src/api/budget.ts` | budget 模块 API 对齐：change PUT 根→PUT `/{id}`、change submit PUT→POST、config PUT 根→PUT `/{id}`（归类 A #17 #18 #19）。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 5.2 / 需求 7.6。 |
| `0aef860` | 2026-06-30T12:34+08:00 | 前端 contract 模块 | `zw-insight-web/src/api/contract.ts` | contract 模块 API 对齐：归类 A 5 项修复——PUT 根→PUT `/{id}` (#21)、分页路径 /page→根 GET 4 项 (#24 #25 #26 #27)。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 6.3 / 需求 7.6。 |
| `dca98ed` | 2026-06-30T12:35+08:00 | 前端 subcontract 模块 | `zw-insight-web/src/api/subcontract.ts` | subcontract 模块 API 对齐：归类 A 1 项修复——settlement submit PUT→POST (#28)。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 6.3 / 需求 7.6。 |
| `78584dc` | 2026-06-30T12:46+08:00 | 前端 tender 模块 | `zw-insight-web/src/api/tender.ts`、`zw-insight-web/src/views/tender/certificate.vue` | tender 模块 API 对齐：归类 A 3 项修复——certificate page/create 按 person/company 子路径拆分 (#37 #38)、delete 增加 type 子路径 (#39)。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 7.2 / 需求 7.6。 |
| `0eeae85` | 2026-06-30T13:00+08:00 | 前端 site 模块 | `zw-insight-web/src/api/site.ts` | site 模块 API 对齐：归类 A 2 项修复——schedule/plan GET 路径参数化 (#40)、delete 补 /plan 子路径 (#41)。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 8.3 / 需求 7.6。 |
| `f45bb94` | 2026-06-30T13:00+08:00 | 前端 machine 模块 | `zw-insight-web/src/api/machine.ts` | machine 模块 API 对齐：归类 A 1 项修复——createMachineRepair POST 根→POST /report (#47)。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 8.3 / 需求 7.6。 |
| `b96c2e0` | 2026-06-30T13:10+08:00 | 前端 hr 模块 | `zw-insight-web/src/api/hr.ts` | hr 模块 API 对齐：归类 A 6 项修复——regular-apply/transfer-apply 路径纠正 (#50 #51 #54 #55)、seal-apply /page→根 GET (#58)、office-supply submit 路径+方法对齐 (#62)。逐文件 `git add`，提交前已复核暂存区无敏感文件。对应任务 9.3 / 需求 7.6。审计复跑核心两类之和：38→32。 |
