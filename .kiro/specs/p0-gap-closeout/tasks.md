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

- [x] 9.1 受影响后端模块单测全绿（用户决策 2026-08-22：Java 后端测试一律走 CI/远端）→ CI run 32584843669（HEAD a955cf5）Backend Build 全绿：23 模块 BUILD SUCCESS、约 3605 单测；含修复 a955cf5（BatchImportExportControllerTest 打桩与 Controller 四参 importData 透传同步）
- [x] 9.2 前端 vitest 全绿（zw-insight-web 102文件/1085用例 + zw-insight-app 16文件/125用例，2026-08-22；app 补测后 119→125）
- [x] 9.3 一致性审计 npm run dev 无新增 Critical（2026-08-22：Critical=0；15 项 Major HTTP_METHOD_MISMATCH 为存量 submit 类接口与扫描口径差异，HEAD 基线前已存在，非本期引入）
- [x] 9.4 覆盖率基线更新（新增类登记 tests/coverage-baseline.json，只升不降）→ 审计发现 CI 覆盖率门禁自引入起长期零输出空转（python3 错误被 `2>/dev/null || true` 吞掉）；artifact 复算 5 模块低于基线，回退原因调查完成（对比 run 32489568434 d66fdda vs run 32584843669 HEAD 逐类 JaCoCo 数据）：① zw-file -106‰：1ba8223 新增 5 个 ImportListener + 8 个 ExcelDTO 共约 248 行零覆盖；② zw-contract -18‰：ConstructionContractBatchHandler 45 行零覆盖；③ zw-budget -48‰：BudgetDetailBatchHandler 68 行仅 412‰（有测试但不足）；④ zw-project -11‰：ProjectBatchHandler 34 行零覆盖；⑤ zw-security -7‰：无实质回退，属 8-21 本地校准偏差（非代码回退）。用户决策（2026-08-22）：4 模块先补测达标再推门禁 + security 基线修正为实测值。执行：补测 9 文件 1193 行（d8db2a4：zw-file 5 Listener 测试 + ExcelDTO 测试、project/contract Handler 测试、budget 补充用例）+ 编译修复（e1044ed 补 Mockito.when 导入）+ security 基线 663→656（eb84e72，int 截断口径）；run 32587853740 全绿，artifact 复算 22 模块全部达标（file 647/budget 825/contract 818/project 635/security 656‰，均 ≥ 基线）；门禁修复 ca50cb5（grep 解析 + COMPARED==0 防空转守卫 + 未登记模块显式 warning），run 32604915058 Backend Build 实测输出 22 行 ✅ + 「基线比对完成：共 22 个模块参与比对」，门禁正式生效
- [x] 9.5 重跑账本 scan+report：levelFinal 复核 133/133 保持，20 项上调已在 HEAD 落定；本期实现的 6 条 gapNotes 同步更新为已闭环事实（2026-08-22）
- [x] 9.6 迁移脚本远程导入验证 + L3 脚本抽检（用户决策走远端 SSH）→ 迁移 49 远端导入 IMPORT_OK/VERIFY_OK（bd_material.material_code 列 + idx_material_code 索引实测存在）+ L3 抽检 4/4 PASS（material 49/labor 38/site 51/finance 全过，真实登录真实接口，2026-08-22/23）
- [x] 9.7 临时文件清理自查 + 分阶段提交 → 本期产生的临时文件全部清理：keys/_p0_*.sh（3 个）、_backend_build_log*.txt（3 个）、_bb_log4.txt/_bb_log5.txt（CI 日志）、_log_parse.js/_jacoco_diff.js（解析脚本）、_jacoco_new/_jacoco_check/_jacoco_old（artifact 复算目录）；远端 /tmp/49_p0_gap_closeout.sql + _p0_*.sh（3 个）已删除并 ls 确认；分阶段提交：业务代码 4 段 → tasks.md 41653e1 → 补测 d8db2a4 → 基线修正 eb84e72 → 编译修复 e1044ed → 门禁修复 ca50cb5

---

## 受阻项登记表

| 日期 | 层级 | 测试项 | 分类 | 原因 | 影响范围 | 处置决策 | 决策人 | 状态 |
|---|---|---|---|---|---|---|---|---|
| 2026-08-22 | L1 | p0-gap-closeout 9.1 受影响后端模块 mvn test | ENV | 用户指令禁止启动 java/openjdk，无法运行 surefire | 后端 9 模块单测无法本地复跑（zw-labor/zw-basedata 已于 8-16/8-21 会话实测全绿） | 用户决策改走 CI：run 32584843669 Backend Build 全绿（23 模块约 3605 单测） | 用户 | 已完成 |
| 2026-08-22 | 覆盖率 | p0-gap-closeout 9.4 JaCoCo 覆盖率基线实测 | ENV | 同上（JaCoCo 依赖 JVM） | 新增类（MaterialService.getByCode 等）覆盖率未实测入 baseline | 改走 CI artifact 复算：用户决策 4 模块先补测达标再推门禁（9 文件 1193 行 d8db2a4）+ security 修正为实测值 656（eb84e72）；门禁修复 ca50cb5，run 32604915058 实测输出 22 ✅，22 模块全达标 | 用户 | 已完成 |
| 2026-08-22 | L3 | p0-gap-closeout 9.6 远程迁移导入+L3 脚本抽检 | ENV | 同上（依赖远程 Java 服务） | 迁移 49 material_code 列与 L3 契约未远程验证 | 用户决策改走远端 SSH：迁移 49 IMPORT_OK/VERIFY_OK + L3 4/4 PASS | 用户 | 已完成 |
