# Design Document

## 概述

本设计覆盖 P0 差距清单 20 项的落地方案，核心原则：**复用已核验的既有基础设施（通用批量框架 / 整改闭环后端 / 批量审批端点 / 预警任务），仅新建真实缺口**。全部接口对接真实后端，禁止 mock 与静默降级。

## 架构决策

### D1 批量导入导出：扩展既有 Batch_Framework，不新建并行体系

- `ModuleCode` 枚举新增 5 值：`PROJECT` / `CONTRACT` / `PAYROLL` / `BUDGET_DETAIL` / `LABOR_CONTRACT`（各带模板文件名）
- Handler 落位于各业务模块（与 `MaterialBatchHandler` 模式一致）：
  - `zw-project/.../batch/ProjectBatchHandler.java`
  - `zw-contract/.../batch/ContractBatchHandler.java`、`BudgetDetailBatchHandler` 位于 `zw-budget/.../batch/`
  - `zw-labor/.../batch/PayrollBatchHandler.java`、`LaborContractBatchHandler.java`
- 每个 Handler 实现 `BatchModuleHandler`：`getModuleCode()` / `createImportListener(projectId)` / 导出查询方法；Excel DTO 用 `@ExcelProperty` 列映射
- 行级校验（必填/金额/关联存在性）失败写入 `ImportResult.errors[row, message]`，框架已支持，不吞错
- **前端组件扩展**：`BatchImportDialog.vue` props 增加 `extraQuery?: Record<string, string | number>`（透传到 importData 的 query），`api/batch.ts importData` 签名扩展为 `(moduleCode, file, params?: Record<string, any>)`——projectId 并入 params，保持向后兼容（现有调用方仅组件内一处 + 测试）
- 导出：各导出 Handler 实现 `exportData(params)` 返回行数据 + `@ExcelProperty` DTO；`AsyncExportDialog` 已具备任务轮询/下载能力，页面仅传 moduleCode+params

### D2 质安整改闭环：前端接线为主，后端仅补查询端点

- 后端新增：`RectificationController GET /by-inspection/{inspectionId}`（返回 `List<BizRectification>` 按创建时间倒序）+ Service 方法 + 权限沿用类级 `site:view`
- PC 列表页：指派整改弹窗（责任人下拉调既有人员查询接口 + 期限日期选择）→ `assignRectification`；整改状态筛选传 `rectificationStatus` 参数（`InspectionController.page` 需增加该可选参数——后端小改）
- PC 详情页：整改记录时间线 + 提交整改表单（整改描述 + 附件可选）+ 复查通过按钮；状态门禁按 `inspection.rectificationStatus` 渲染
- 移动端：quality-check/safety-check 页提交整改入口（跳 inspection-detail 页执行）；inspection-detail 页加复查确认按钮；拍照复用 `construction-log.vue` 的 `uni.chooseImage` + 文件上传模式（附件关联字段以 BizRectification 现有结构为准，缺附件字段则在迁移 49 中为 `biz_rectification` 补 `attachment_ids` VARCHAR(500)）
- 状态机不改动后端既有语义：PENDING → SUBMITTED → APPROVED

### D3 统计端点：集中放在各业务模块，遵循 SalaryStatisticsController 范式

- 新 Controller：
  - `zw-contract/.../controller/ContractStatisticsController.java`：`GET /api/v1/contract/statistics/amount-summary`、`GET /api/v1/contract/statistics/output-trend?projectId=`
  - `zw-labor/.../controller/LaborStatisticsController.java`：`GET /api/v1/labor/statistics/payroll-trend?projectId=`、`GET /api/v1/labor/statistics/cost-ratio?projectId=`
  - `zw-finance/.../controller/FinanceStatisticsController.java`：`GET /api/v1/finance/statistics/collection-rate?projectId=`、`GET /api/v1/finance/statistics/fund-plan`
- 预算执行：预算编制页直接引用既有 `GET /api/v1/dashboard/budget-execution`（zw-dashboard），不新建
- 项目组合看板：核验 `GET /api/v1/dashboard/company-overview` 返回结构后引用；不足再在 `ProjectController` 补 `GET /project/portfolio`
- VO 统一 `XxxStatsVO` 放各模块 vo 包；聚合用 Mapper 自定义 SQL（`@Select` 或 XML，与模块现状一致），租户隔离依赖 MyBatis-Plus tenant 插件自动注入
- 跨模块数据（fund-plan 需付款申请的期望付款月份字段）：以 zw-finance 自有表聚合为准，不跨库直查他模块表；确需他模块数据时走既有模块间调用方式（实现时核查 zw-finance 现有依赖惯例）
- 前端：每页增「统计分析」el-card + echarts（折线/饼图），仿 `dashboard/index.vue` 的 init/setOption/dispose 生命周期管理；无数据显示空状态文案

### D4 回款认领核销

- 迁移 49 加字段后：`PaymentReceivedController` 增加 claim/write-off 端点；Service 状态机校验（UNCLAIMED→CLAIMED→WRITTEN_OFF），claim 写 claimed_by=当前用户
- 核销前置条件：write-off 要求该记录已认领且关联 contract_id 非空（核销语义=回款对合同应收的勾销，应收基数以合同金额为准，实现时以 finance 模块现有应收口径为准并注释说明）
- 前端：payment-received.vue 增认领状态列/筛选/操作按钮（状态门禁）

### D5 花名册进退场

- 迁移 49 加 entry_status/entry_date/exit_date；`LaborRosterController` 增 `POST /{id}/entry`、`POST /{id}/exit`；page 增 entryStatus 参数
- 状态冲突抛 BusinessException（重复进退场）

### D6 移动端扫码

- 后端：`MaterialController`（zw-basedata）增 `GET /by-code?code=`，未找到抛 BusinessException（前端展示明确提示）
- 前端：inbound/outbound.vue 增扫码按钮；`uni.scanCode` 用 `#ifdef APP-PLUS || MP-WEIXIN` 条件编译，H5 平台渲染「手动输入编码」输入框 + 查询按钮（明示替代，非静默降级）

### D7 工作台与审批硬化

- 工作台：`loadData` 拆分为 overview/todo/projects 三个独立失败域，各自维护 error 状态与重试；setInterval 60s 轮询待办数，onHide clearInterval、onShow 恢复；待办列表取 getTodoTasks size=5 渲染，点击跳 `/pages/approval/detail?taskId=`
- 我的审批：待办 tab 增 checkbox 多选 + 底部固定「批量同意」栏（未选禁用）；调 `POST /v1/workflow/approval/batch-approve`；既有空 catch 全部改为 `uni.showToast` 错误提示
- 移动端 api 层：`zw-insight-app/src/api/common.ts` 增 `batchApprove` 封装

## 数据库迁移

`deploy/db-init/49_V2026_47__p0_gap_closeout.sql`，幂等模式（information_schema 检查后 ALTER）：

```sql
-- biz_payment_received: claim_status / claimed_by
-- biz_labor_roster: entry_status / entry_date / exit_date
-- biz_rectification: attachment_ids（仅当移动端整改附件需要时）
```

## 权限与安全

- 新端点全部挂模块级 `@RequiresPermission`（contract:view/labor:view/finance:view/material:view/basedata:view/site:view），与模块现有惯例一致
- 统计端点只读，沿用 view 权限；认领/核销/进退场/整改为写操作，实现时核查模块是否已有更细粒度权限点（如 finance:payment），有则用之
- 租户隔离：全部经 MyBatis-Plus tenant 插件，禁止手写 tenant_id 条件遗漏

## 测试策略

- 每个新 Service 方法：≥1 正常路径 + ≥1 异常路径（MockitoExtension，与模块现有测试同风格）
- 状态机测试：claim/write-off 非法流转、entry/exit 重复操作、整改提交非法状态
- Handler 测试：参考 `BoqServiceUploadFlowTest` 用 EasyExcel 真实写 xlsx 走解析链路（至少 PAYROLL 与 BUDGET_DETAIL 两个复杂 Handler）
- 前端：新增组件交互走 vitest（BatchImportDialog extraQuery 透传、统计面板空态）
- 集成验证：tenant_id=9999，遵守 AGENTS.md 测试约定

## 风险与回滚

| 风险 | 缓解 |
|---|---|
| api/batch.ts importData 签名变更影响现有调用 | 仅组件内与测试引用，同步修改；grep 验证无其他调用方 |
| InspectionController.page 增 rectificationStatus 参数 | 可选参数，向后兼容 |
| 迁移 49 与远程库冲突 | 幂等 ALTER + 执行前备份惯例 |
| fund-plan 期望付款月份字段缺失 | 以付款申请的 created_at/审批时间推导月份，spec 注释口径 |
| 回滚 | 各阶段独立提交；迁移提供逆向说明（删列） |
