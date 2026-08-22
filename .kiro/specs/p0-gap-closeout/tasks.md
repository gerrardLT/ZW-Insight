# Tasks Document

> 对应 requirements.md 的 8 个 Requirement。勾选制推进，每项完成后本地验证再勾选。

## 1. 批量导入导出接线与扩展（Requirement 1）

- [x] 1.1 `api/batch.ts` importData 签名扩展（params 透传）+ `BatchImportDialog.vue` 增 extraQuery props
- [x] 1.2 `views/labor/roster.vue` 接 BatchImportDialog（LABOR_ROSTER，projectId+teamId 透传）
- [x] 1.3 `views/basedata/material.vue` 接 BatchImportDialog（MATERIAL）；`views/machine/ledger.vue` 接（MACHINE_LEDGER）
- [x] 1.4 ModuleCode 枚举新增 PROJECT/CONTRACT/PAYROLL/BUDGET_DETAIL/LABOR_CONTRACT
- [x] 1.5 ProjectBatchHandler（zw-project）+ Excel DTO + 行级校验 + 单测
- [x] 1.6 ContractBatchHandler（zw-contract）+ Excel DTO + 单测
- [x] 1.7 PayrollBatchHandler + LaborContractBatchHandler（zw-labor）+ Excel DTO + 单测
- [x] 1.8 BudgetDetailBatchHandler（zw-budget，明细行→预算科目关联校验）+ 单测
- [x] 1.9 对应 5 个页面接 BatchImportDialog（项目报备/施工合同/工资单/预算编制/劳务合同）
- [x] 1.10 导出 Handler（CONTRACT/OUTPUT_REPORT/STOCK/PAYMENT_RECEIVED/LABOR_ROSTER）实现 exportData
- [x] 1.11 施工合同/产值上报/库存查询/回款登记/花名册页面接 AsyncExportDialog
- [x] 1.12 前端 vitest：BatchImportDialog extraQuery 透传用例

## 2. 质安整改闭环（Requirement 2）

- [x] 2.1 RectificationController 补 GET /by-inspection/{inspectionId} + Service + 单测（正常+无记录）
- [x] 2.2 InspectionController.page 增 rectificationStatus 可选筛选参数 + Service/Mapper 调整 + 单测
- [x] 2.3 api/site.ts 补 getRectifications 函数；检查列表页接「指派整改」弹窗 + 状态筛选
- [x] 2.4 检查详情页整改记录区 + 提交整改表单 + 复查通过按钮（状态门禁）
- [x] 2.5 迁移 49 中为 biz_rectification 补 attachment_ids（若 BizRectification 无附件字段）
- [x] 2.6 移动端 inspection-detail 页整改提交 + 复查确认；quality-check/safety-check 页整改入口（save 返回 id + 字段映射修正）
- [x] 2.7 移动端检查拍照上传（复用 construction-log chooseImage 模式 + uploadRectificationPhoto 真实上传 attachmentIds）
- [x] 2.8 后端单测齐备；PC/移动端错误消息透传检查（不吞错）

## 3. 统计分析面板（Requirement 3）

- [x] 3.1 ContractStatisticsController（amount-summary + output-trend）+ Service + VO + 单测
- [x] 3.2 LaborStatisticsController（payroll-trend + cost-ratio）+ Service + VO + 单测
- [x] 3.3 FinanceStatisticsController（collection-rate + fund-plan）+ Service + VO + 单测
- [x] 3.4 预算编制页引用 /dashboard/budget-execution 面板（echarts）
- [x] 3.5 项目报备页组合看板（先核 company-overview 结构，不足补 /project/portfolio）
- [x] 3.6 合同/产值/工资/劳务成本/回款/付款页面 echarts 面板（空态处理）
- [x] 3.7 前端 vitest：统计面板空态用例

## 4. 回款认领核销与库存预警补全（Requirement 4）

- [x] 4.1 迁移 `49_V2026_47__p0_gap_closeout.sql`（幂等：payment_received claim 字段 + labor_roster 进退场字段 + rectification 附件字段）
- [x] 4.2 PaymentReceivedController claim/write-off 端点 + Service 状态机 + 单测（含非法流转）
- [x] 4.3 payment-received.vue 认领状态列/筛选/门禁操作按钮
- [x] 4.4 ProjectMaterialStockService.page 返回 minStock（join 配置表，无配置取全局默认）+ 单测
- [x] 4.5 stock.vue 修复分页参数契约（page/size）
- [x] 4.6 StockWarningConfigController CRUD + Service + 单测；库存页「预警配置」弹窗
- [x] 4.7 StockWarningTask.sendWarning 接 MessageService 站内信（替代 log TODO）+ 单测
- [x] 4.8 库存导出含预警状态列（1.10/1.11 的 STOCK Handler 内实现）

## 5. 花名册进退场（Requirement 5）

- [x] 5.1 LaborRosterController entry/exit 端点 + Service 状态冲突校验 + 单测
- [x] 5.2 page 增 entryStatus 筛选参数
- [x] 5.3 roster.vue 进退场状态列/日期/操作按钮/筛选

## 6. 移动端扫码出入库（Requirement 6）

- [x] 6.1 zw-basedata MaterialController GET /by-code + Service + 单测（含未找到）
- [x] 6.2 移动端 api 封装 getMaterialByCode
- [x] 6.3 inbound.vue/outbound.vue 扫码按钮（条件编译）+ H5 手输编码明示入口 + 编码不存在提示

## 7. 工作台硬化（Requirement 7）

- [x] 7.1 workbench/index.vue 消除空 catch：三分区失败态 + 重试按钮
- [x] 7.2 待办 60s 轮询（onShow/onHide 生命周期管理）
- [x] 7.3 待办列表区（前 5 条）+ 跳转审批详情

## 8. 移动端批量审批（Requirement 8）

- [x] 8.1 api/common.ts 增 batchApprove 封装
- [x] 8.2 approval/index.vue 待办多选 + 批量同意（未选禁用）+ 状态 tab 筛选
- [x] 8.3 消除空 catch，失败提示

## 9. 验收（阶段 6）

- [ ] 9.1 受影响后端模块 mvn test 全绿（budget/contract/labor/material/finance/site/basedata/project/file）→ 受阻（用户指令禁止启动 java/openjdk，2026-08-22 登记）
- [x] 9.2 前端 vitest 全绿（zw-insight-web 102文件/1085用例 + zw-insight-app 16文件/119用例，2026-08-22）
- [x] 9.3 一致性审计 npm run dev 无新增 Critical（2026-08-22：Critical=0；15 项 Major HTTP_METHOD_MISMATCH 为存量 submit 类接口与扫描口径差异，HEAD 基线前已存在，非本期引入）
- [ ] 9.4 覆盖率基线更新（新增类登记 tests/coverage-baseline.json，只升不降）→ 受阻（需 JaCoCo/JVM，同上）
- [x] 9.5 重跑账本 scan+report：levelFinal 复核 133/133 保持，20 项上调已在 HEAD 落定；本期实现的 6 条 gapNotes 同步更新为已闭环事实（2026-08-22）
- [ ] 9.6 迁移脚本远程导入验证 + L3 脚本抽检（test-api-material/finance/labor/site）→ 受阻（需远程 Java 服务，同上）
- [ ] 9.7 临时文件清理自查 + 分阶段提交

---

## 受阻项登记表

| 日期 | 层级 | 测试项 | 分类 | 原因 | 影响范围 | 处置决策 | 决策人 | 状态 |
|---|---|---|---|---|---|---|---|---|
| 2026-08-22 | L1 | p0-gap-closeout 9.1 受影响后端模块 mvn test | ENV | 用户指令禁止启动 java/openjdk，无法运行 surefire | 后端 9 模块单测无法本地复跑（zw-labor/zw-basedata 已于 8-16/8-21 会话实测全绿） | 延期至 Java 可用时补跑 | 用户 | 待执行 |
| 2026-08-22 | 覆盖率 | p0-gap-closeout 9.4 JaCoCo 覆盖率基线实测 | ENV | 同上（JaCoCo 依赖 JVM） | 新增类（MaterialService.getByCode 等）覆盖率未实测入 baseline | 延期至 Java 可用时补测登记 | 用户 | 待执行 |
| 2026-08-22 | L3 | p0-gap-closeout 9.6 远程迁移导入+L3 脚本抽检 | ENV | 同上（依赖远程 Java 服务） | 迁移 49 material_code 列与 L3 契约未远程验证 | 延期至 Java 可用时补验 | 用户 | 待执行 |
