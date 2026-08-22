# Requirements Document

## Introduction

按功能深度账本（`audit-reports/feature-ledger/feature-ledger-report.md`）ROI 差距清单落地全部 20 项 P0 缺口。阶段 0 实况核验（2026-08-22）确认：批量导入导出通用框架（`/api/v1/batch/*` + BatchModuleHandler 策略模式）、质安整改后端闭环、审批批量端点、预算执行看板端点均已就绪但前端未接线——本 spec 以「接线优先、新建其次」为原则，全部对接真实接口，禁止 mock/静默降级。

## Glossary

- **Ledger**：功能深度账本，L0-L4 成熟度评分 + 八维缺口（效/查/状/追/通/权/异/值）
- **Batch_Framework**：`zw-file/batch` 通用批量导入导出框架（`BatchImportExportController`、`BatchModuleHandler` 策略接口、`ModuleCode` 枚举、Redis 异步导出任务、Minio 存储）
- **BatchImportDialog**：PC 通用批量导入弹窗组件（`zw-insight-web/src/components/BatchImportDialog.vue`）
- **AsyncExportDialog**：PC 通用异步导出弹窗组件（`zw-insight-web/src/components/AsyncExportDialog.vue`）
- **Rectification_Loop**：质安整改闭环状态机 PENDING → SUBMITTED → APPROVED（`RectificationService` 已实现，含 @Scheduled 超期催办）
- **Inspection_Page**：PC 质安检查列表页（`/site/inspection`）与详情页（`/site/inspection/detail/:id`）
- **Mobile_App**：uni-app 移动端（`zw-insight-app`）

---

## Requirements

### Requirement 1: 批量导入导出接线与扩展（账本「效」维度 8 项）

**User Story:** 作为业务人员，我需要在花名册、材料字典、机械台账、项目、施工合同、工资单、预算明细、劳务合同页面使用 Excel 批量导入与列表导出，以替代逐条手工录入。

#### Acceptance Criteria

1. THE BatchImportDialog SHALL 支持通过 props 透传额外业务参数（如花名册的 projectId/teamId）到 `/v1/batch/import` 的 query 参数；IF 组件现有 API 不支持，THEN 扩展 props 而非新建组件
2. THE 劳务花名册页面 SHALL 接入 BatchImportDialog（moduleCode=LABOR_ROSTER）与 AsyncExportDialog，导入调用既有 `POST /api/v1/batch/import?moduleCode=LABOR_ROSTER`
3. THE 材料字典页面 SHALL 接入 BatchImportDialog（moduleCode=MATERIAL）；THE 机械台账页面 SHALL 接入（moduleCode=MACHINE_LEDGER）
4. THE Batch_Framework SHALL 新增 PROJECT（项目报备）、CONTRACT（施工合同台账）、PAYROLL（工资单）、BUDGET_DETAIL（预算明细）、LABOR_CONTRACT（劳务合同）五个 ModuleCode，各业务模块实现 BatchModuleHandler Bean
5. 每个新 Handler SHALL 定义 Excel DTO（`@ExcelProperty` 列映射），导入时校验必填字段、金额格式、关联项目存在性，失败行计入 ImportResult.failedRows 并返回行级错误原因（禁止静默跳过）
6. 每个新 Handler SHALL 支持 `GET /api/v1/batch/template/{moduleCode}` 模板下载
7. THE 施工合同台账、产值上报、库存查询、回款登记、劳务花名册页面 SHALL 接入 AsyncExportDialog 走 `POST /api/v1/batch/export` 异步导出
8. IF 导入文件为空或非 xlsx/xls，THEN THE Batch_Framework SHALL 返回明确错误提示（既有行为保持）

### Requirement 2: 质安整改闭环前端接线（账本质量安全检查 + 检查详情 + 移动端检查 4 项）

**User Story:** 作为质安管理员，我需要在 PC 与移动端完成「检查 → 指派整改 → 提交整改 → 复查验收」全流程操作，使既有后端闭环能力真正可用。

#### Acceptance Criteria

1. THE Inspection_Page 列表 SHALL 提供「指派整改」操作，调用既有 `POST /api/v1/site/inspection/{id}/assign`（弹窗选择责任人 + 整改期限），成功后刷新列表整改状态
2. THE Inspection_Page 列表 SHALL 支持按整改状态（PENDING/SUBMITTED/APPROVED）筛选
3. THE RectificationController SHALL 补充 `GET /api/v1/site/rectification/by-inspection/{inspectionId}` 查询端点返回该检查单的整改记录列表
4. THE 检查详情页 SHALL 展示整改记录区（调用 Requirement 2.3 端点）、整改提交表单（调既有 `POST /api/v1/site/rectification/{inspectionId}/submit`）与复查通过按钮（调既有 `POST /api/v1/site/rectification/{id}/approve`）
5. THE 检查详情页 SHALL 按状态门禁显示操作：仅 PENDING 状态显示指派/提交整改入口，仅 SUBMITTED 状态显示复查通过按钮
6. THE Mobile_App 质量检查与安全检查页 SHALL 支持提交整改动作（同套真实端点）；THE 移动端检查详情页 SHALL 支持复查确认
7. THE Mobile_App 检查页 SHALL 支持拍照上传检查现场附件，复用 `construction-log.vue` 的 uni.chooseImage + 文件上传既有模式
8. IF 整改操作接口返回失败，THEN 前端 SHALL 显示后端返回的错误消息，不静默吞错

### Requirement 3: 统计分析面板（账本「值」维度 8 项）

**User Story:** 作为项目/财务/劳务管理者，我需要在预算、合同、产值、工资、劳务成本、回款、付款页面看到统计图表面板，以掌握执行率、趋势与占比。

#### Acceptance Criteria

1. THE 预算编制页面 SHALL 引用既有 `GET /api/v1/dashboard/budget-execution` 展示执行率/偏差面板（后端已就绪，禁止新建重复端点）
2. THE zw-contract SHALL 提供 `GET /api/v1/contract/amount-summary`（合同金额汇总与付款比例，可按项目筛选）与 `GET /api/v1/contract/output/{projectId}/trend`（产值完成率按月趋势）
3. THE zw-labor SHALL 提供 `GET /api/v1/labor/payroll/{projectId}/trend`（工资发放月度趋势）与 `GET /api/v1/labor/cost-ratio`（劳务成本占项目成本比例）
4. THE zw-finance SHALL 提供 `GET /api/v1/finance/payment/collection-rate`（回款率：已收/应收，按项目）与 `GET /api/v1/finance/payment/fund-plan`（未来 6 个月按月应付预测，聚合未付付款申请）
5. THE 项目报备页面 SHALL 展示项目组合看板（状态 × 金额分布）；IF `GET /api/v1/dashboard/company-overview` 已覆盖该数据，THEN 直接引用不新建端点
6. 所有新统计端点 SHALL 遵循 SalaryStatisticsController 范式：GET + 聚合查询 + `@RequiresPermission` 模块权限 + 租户隔离（MyBatis-Plus tenant 插件既有机制）
7. 对应 8 个前端页面 SHALL 以 echarts 渲染统计面板（仿 `dashboard/index.vue` 模式），带项目/时间筛选与空数据状态提示
8. IF 统计查询无数据，THEN 面板 SHALL 显示「暂无统计数据」而非渲染错误

### Requirement 4: 回款认领核销与库存预警补全（账本回款登记 + 库存查询 2 项）

**User Story:** 作为财务人员，我需要对回款记录进行认领与核销跟踪；作为材料管理员，我需要库存预警的状态列真实生效并可维护预警配置。

> 阶段 0 核验：库存预警后端已实现（`BizStockWarningConfig` 配置表、`StockWarningTask` @Scheduled 每日扫描、`page(warning=LOW/NORMAL)` 过滤含单测），前端 stock.vue 已接 warning 筛选。剩余缺口为下述 4.3-4.6。

#### Acceptance Criteria

1. 迁移脚本 `deploy/db-init/49_V2026_47__p0_gap_closeout.sql` SHALL 为 `biz_payment_received` 增加 `claim_status`（VARCHAR(20) DEFAULT 'UNCLAIMED'，取值 UNCLAIMED/CLAIMED/WRITTEN_OFF）与 `claimed_by`（BIGINT）；为 `biz_labor_roster` 增加 `entry_status`（VARCHAR(20) DEFAULT 'ON_SITE'）、`entry_date`（DATE）、`exit_date`（DATE）；脚本 SHALL 幂等（先查 information_schema 再 ALTER）
2. THE zw-finance SHALL 提供 `POST /api/v1/finance/payment-received/{id}/claim` 与 `POST /api/v1/finance/payment-received/{id}/write-off`，状态流转 UNCLAIMED → CLAIMED → WRITTEN_OFF 严格校验，非法流转抛 BusinessException
3. THE 回款登记页面 SHALL 显示认领状态列与筛选，并按状态门禁提供认领/核销操作按钮
4. THE zw-material 库存分页接口 SHALL 返回 minStock（来自 BizStockWarningConfig 该 project+material 的安全库存，无配置时返回全局默认阈值）使前端状态列真实生效；THE stock.vue SHALL 修复分页参数契约（pageNum/pageSize → page/size，以后端 Controller 为准）
5. THE zw-material SHALL 提供安全库存配置管理端点（`/api/v1/material/stock-warning-config` CRUD：按 projectId+materialId 维护 safetyStock）；THE 库存查询页面 SHALL 提供「预警配置」入口弹窗维护
6. THE StockWarningTask 的 sendWarning SHALL 接入真实站内信（MessageService 既有能力）替代 log TODO；IF 消息服务不可用，THEN 抛出异常并记录失败，不静默降级
7. THE 库存查询页面 SHALL 导出含预警状态列（接 Requirement 1 异步导出框架）

### Requirement 5: 花名册进退场状态（账本劳务花名册「状」维度）

**User Story:** 作为劳务管理员，我需要记录工人进场与退场时间并按状态筛选，以掌握现场用工动态。

#### Acceptance Criteria

1. THE zw-labor SHALL 提供 `POST /api/v1/labor/roster/{id}/entry`（登记进场，写 entry_date=当日、entry_status=ON_SITE）与 `POST /api/v1/labor/roster/{id}/exit`（登记退场，写 exit_date、entry_status=OFF_SITE）
2. IF 对已退场工人再次退场或已进场工人再次进场，THEN 接口 SHALL 返回状态冲突错误
3. THE 劳务花名册页面 SHALL 显示进退场状态列、进退场日期与操作按钮，并支持按进退场状态筛选（page 接口增加 entryStatus 参数）

### Requirement 6: 移动端扫码出入库（账本材料入库 + 材料出库 2 项）

**User Story:** 作为现场材料员，我需要在移动端通过扫描材料条码快速带出入库材料信息，减少手工录入。

#### Acceptance Criteria

1. THE zw-basedata SHALL 提供 `GET /api/v1/basedata/material/by-code?code={code}` 按材料编码查询单个材料，未找到返回 404 语义的业务错误
2. THE Mobile_App 材料入库与出库页 SHALL 提供扫码按钮，调用 uni.scanCode 扫描条码后调用 Requirement 6.1 端点带出材料信息填充表单
3. IF 扫码返回的编码在材料字典中不存在，THEN 页面 SHALL 提示「材料编码不存在，请先维护材料字典」，不自动创建材料
4. WHEN 运行平台不支持 uni.scanCode（如 H5），THEN 页面 SHALL 显示手输编码输入框作为明示的替代入口（界面显式标注「手动输入编码」，非静默降级）

### Requirement 7: 移动端工作台硬化（账本工作台「异/通」维度）

**User Story:** 作为移动端用户，我需要在工作台数据加载失败时看到明确提示并可重试，且待办数量能自动更新。

#### Acceptance Criteria

1. THE 工作台页面 SHALL 消除全部空 `catch {}`：接口失败时显示失败态提示与「重试」按钮，重试重新发起同一请求
2. THE 工作台页面 SHALL 每 60 秒轮询刷新待办数量（`getTodoTasks`），页面 onHide 时停止轮询、onShow 时恢复
3. THE 工作台页面 SHALL 提供待办列表区（显示前 5 条待办），点击跳转审批详情页

### Requirement 8: 移动端批量审批（账本我的审批「异/效」维度）

**User Story:** 作为审批人，我需要在移动端一次勾选多条待办执行批量同意，并在接口失败时得到明确提示。

#### Acceptance Criteria

1. THE 我的审批页面 SHALL 在待办 tab 提供多选勾选与「批量同意」按钮，调用既有 `POST /api/v1/workflow/approval/batch-approve`（taskIds + comment）
2. WHEN 未勾选任何条目时，THE 批量同意按钮 SHALL 处于禁用状态
3. IF 批量审批部分失败，THEN 页面 SHALL 显示后端返回的失败信息并刷新列表（后端 batchApprove 行为以其现有实现为准）
4. THE 我的审批页面 SHALL 提供状态 tab 筛选（待办/已办/我发起的，对应既有 todo/done/my-initiated 端点）
5. THE 我的审批页面 SHALL 消除空 catch，失败时显示错误提示
