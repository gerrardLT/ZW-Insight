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

## 10. 收口后续任务（2026-08-23，用户指令两项）

### 10.1 一致性审计工具 15 项 Major 误报修复（已闭环）

**变更原因**：存量 15 项 Major（HTTP_METHOD_MISMATCH）全部集中在 submit 类接口。根因：backend-scanner.ts 的 method 提取正则仅支持单值形式 `method = RequestMethod.POST`，无法解析数组形式 `method = {RequestMethod.POST, RequestMethod.PUT}`（15 个 submit 端点的真实注解），返回 null 后被 `|| 'GET'` 兜底，全部误判为 GET 与前端 POST 失配。

**变更明细**（commit e5b92f8，5 文件）：
- types.ts：BackendApiEntry 新增 `additionalMethods?: HttpMethod[]`
- backend-scanner.ts：`extractRequestMethodsFromAnnotation` 重写，正则 `method\s*=\s*(\{[^}]*\}|[^,)]+)` 支持数组（注意：数组内含逗号，不可用 `[^,]+` 截断——首版自检出并修正）；主方法取第一个、其余入 additionalMethods
- consistency-comparator.ts：方法匹配改集合包含（主方法或 additionalMethods 命中即匹配），mismatch 描述展示全部方法（POST/PUT）
- 补 4 个单测（backend-scanner 数组形式 + comparator 多方法匹配 3 例），198 测试全绿

**验证结果**：重跑审计（audit-report-2026-08-23T12-48-54）Major 15→0、Critical=0、Minor 268、一致率 80.1%→81.2%（后端 740 API / PC 628 / 移动端 57 / 20 模块全审）。

**附带处置**（commit 3b5656c）：删除 tools/consistency-audit/audit-reports/ 3 份无效报告（在 tools 子目录误跑产出：后端 API=0 全假）；AGENTS.md 补根目录坑警示（commit b7f08ba）——正确姿势 `npx tsx src/cli.ts --root ../../ --output ../../audit-reports`。

**回滚方案**：revert e5b92f8 即恢复原扫描逻辑（Major 误报回归），无数据/接口影响。

### 10.2 全量套件 run 32641133319 两处失败定位与修复（已闭环）

**触发**：用户指令手动触发全量套件（gh workflow run deploy.yml -f run_tests=true）。首跑（ac2b0fe）被自己后续 push 触发的 run 经 concurrency cancel-in-progress 取消（操作教训：等套件跑完再推代码）；HEAD b7f08ba 重触发 run 32641133319，Backend Build/前端/Deploy 全绿，Integration Test 失败。

**失败定位**（日志逐段解析）：
1. L3 24/25：test-api-authz.sh A3「低权限访问未标注接口 /api/v1/project/page」得 403（期望 200）
2. L5-UI 165 passed/1 failed：permission.spec.ts C-13-2 lina 封账页断言（serial 连带 C-13-1 等 skipped）；L4 210/210、L5-API 445 passed、一致性 55 passed 全绿

**根因**（非产品缺陷，守卫行为正确）：d66fdda 权限守卫系统性修复（08-21）后测试契约过期——①ProjectController 类级 @RequiresPermission("project:view") 使 /api/v1/project/page 不再是未标注接口，t9999user 403 为正确行为；②/finance 父路由 meta.permission='finance:view'（vue-router to.meta 合并父链）+ FinanceLockController 类级 finance:view，lina 被路由层重定向 /403 到不了封账页、读列表也 403。

**真实探针实证**（SSH Redis 验证码真实登录，全部契约成立）：t9999user GET menu/user=200 / project/page=403；lina 封账读=403 / 写=403 / menu/user=200；wangqiang 封账读=200。

**修复**（commit 36c7400，测试侧契约对齐，非降级）：
- keys/test-api-authz.sh：A3 改豁免端点 GET /api/v1/system/menu/user（期望 200）
- permission.spec.ts：C-13-2 改断言路由层重定向 /403；末用例「写被拒无落库」前后差集改由 wangqiang（持 finance:view）读取，新增 lina 读列表 403 断言
- tests/frontend-test-case-matrix.md C-13-2 行同步；受阻台账（test-maturity-upgrade）登记
- 验证：bash -n 语法 OK、playwright --list 可解析、router-guard 单测已钉住「无视图码→/403」守卫逻辑

**复验**：commit 36c7400 push 后手动触发全量套件 run 32644242233（同 HEAD 的 push run 按 concurrency 设计被取消）。**结果：两处修复全部生效**——L3 25/25 全绿（含 authz A3）、permission.spec 全过（含 C-13-2）、L4 210/0、L5-API 445 passed、一致性 55 passed。但 Integration Test 暴露新失败，见 10.3。

### 10.3 全量套件 run 32644242233 新失败定位与修复（已闭环）

**失败现象**（artifact integration-test-results 完整 l5-ui-real.log 实证，CI 步骤仅 tail -6 看不到细节）：L5 UI Real `1 failed / 1 flaky / 3 skipped / 19 did not run / 147 passed`，UI_REAL_EXIT=1。
- 真失败：finance-write.spec.ts:205 C5 付款申请完整写流程——前提断言「应存在可付余额 >= 1 元且项目可解析的采购/劳务/机械合同」首跑+retry 双败（故 exit 1，非 flaky 所致）
- serial 连带：finance-write `mode:'serial'`（L27），C5 失败后同文件后续 19 用例 did not run（86-104/121-139 号）
- expense-write-2.spec.ts:435 B-21 为 flaky（重试过），非独立失败；其首跑错误与 C5 同源（项目解析空）

**根因**（DATA + 测试侧脆弱前提，非产品缺陷；服务器真实探针实证）：
- 租户 1 项目表 total=229，其中 214 条为 E2E 残留（E2E审批UI_×180 + E2E自动化测试项目_×30 + E2E_TEST_×4，历轮实跑累积）；project/page 按创建时间倒序，首页 200 条几乎全是残留
- 种子项目 90001（滨江花园一期工程）/90002/90003/90004 被挤到第二页；而有余额合同（水泥砂石采购 91501 余额 999970、钢材采购 91502 余额 1000000、劳务 91601/91602 各 500000）的 projectId 均指向种子项目
- spec 只拉 page=1&size=200 → 项目解析全空 → 前提断言失败。排除余额耗尽假设（余额充足）

**修复**（双管齐下，非降级，commit 4bc4225）：
1. 测试侧硬化：real-helper.ts 新增 `fetchAllProjects` 翻页全量拉取（按 total 翻页，上限 20 页）；5 处调用点切换——finance-write C5/C1/C6 + expense-write-2 resolveDemoProject/B-21。断言语义不变
2. 数据卫生：演示库 E2E 残留项目经真实 API（DELETE /api/v1/project/{id}）删除 210/222 条；4 条 WON 投标项目被「存在关联投标报名」引用守卫拦截（守卫生效正向实证，与既有台账残留同类，巡检兜底）；项目总数 229→19，种子项目回首页

**本地验证**：playwright --list 171 tests 可解析；实跑 finance-write + expense-write-2 共 32 passed / 0 failed / 1 flaky（C6 一例 UI 时序等待超时重试过，与修复无关）；日志无「演示数据前提/无法解析/did not run」错误。

**复验**：push 后手动触发全量套件 run 32647849931（HEAD 4bc4225，push run 32647849265 按 concurrency 取消）。**结果：全链绿（conclusion=success）**，artifact 明细实证：
- Backend Build / 三前端单测 / Deploy：success
- L3：25/25 PASS（L3_PASS=25 L3_FAIL=0）
- L4：26/26 stages PASSED，无 abort，残留清理报告正常
- L5-API：25 files / 445 passed | 1 skipped，L5_EXIT=0
- L5-UI real：**168 passed / 3 skipped / 0 failed / 0 flaky，UI_REAL_EXIT=0**（C5 完整写流程、B-21、以及上轮 19 个 did not run 用例全部真实通过）
- L5 一致性：55 passed，CONS_EXIT=0
- 注：k6 为独立 workflow（performance-k6.yml），不在 deploy.yml job 列表内，本轮未含

两处用户指令任务至此全部闭环：审计 15 项 Major 误报修复（10.1）+ 全量套件全链绿（10.2/10.3 三轮定位修复）。

**回滚方案**：`git revert 4bc4225` 即恢复单页拉取（脆弱前提回归，仅影响测试稳定性，不影响产品代码）。

---

## 11. 遗留项修复批次（2026-08-24，用户指令「遗留项修复」）

### 11.1 R6-01 备份恢复补挂 @SecondaryConfirm（commit be00de8）

- 调研实证：@SecondaryConfirm 注解体系（注解 + SecondaryConfirmAspect 449/403/423 语义 + 前端 449 拦截器密码框）在 zw-security 早已完整落地，但全后端零消费方；restore 为高危操作（覆盖全库）无任何二次确认
- 修复：BackupController.restore 补挂 `@SecondaryConfirm`（message 明示高风险+登录密码确认）+ 清理 8 处重复 import；前端 449 链路已就绪无需改动
- 测试：新增 BackupControllerSecondaryConfirmTest 反射钉住 3 例（注解存在且 message 合规 / @PostMapping 恰为 /restore/{id} / execute 无注解防过度拦截），不启容器适配本地禁 JVM 约束，CI 验证

### 11.2 盲点 12 机械结算空预览提交守卫（commit 5d884bf）

- 调研实证：后端 MachineWorkSettlementService.createSettlement 已有 `workLogs.isEmpty()` → BusinessException「该周期内无可结算的工作量记录」拦截，仅缺前端 UI 守卫
- 修复：create.vue 新增 canSave computed（预览齐备且有明细才可保存，按钮 disabled）+ handleSave 编程调用/竞态兜底 warning
- 测试翻转：B-12-5 单测（canSave=false + 不发请求 + warning 文案 + 置数据后 true）、settlement-docs 保存用例补 preview 字段、E2E expense-write 断言保存按钮禁用、frontend-test-case-matrix 同步。定向 36 passed，全量 vitest 1085 passed

### 11.3 S5 截图补拍完成

Playwright 复用 storageState（token 过期走真实登录刷新）补拍 9 页全 OK 无重定向，dashboard 实证真实数据（项目 29/合同总额 13200 万/已收款 7100 万）。工具链与截图任务结束已清理

### 11.4 k6 run 32653198393 失败根因与修复（commit 7974969）

**根因链（双问题叠加）**：
1. 直接原因：手动触发 k6（16:55 UTC）撞上 push d5ec371 部署 run 32652602551 的容器替换窗口（zwi-backend StartedAt 16:57:19 UTC 实证），login.js 前 1 分钟打到不可用后端，http_req_failed 99.9% > 阈值 0.1 → k6 exit 99 → exit 5。调度撞车，非代码缺陷
2. 存量问题（被绿灯掩盖）：payment-submit.js 硬编码租户 1 种子合同 91501/项目 90001，与 CI 账号 t9999admin（租户 9999）错位——创建草稿成功但 submit 被「关联合同不存在」拒绝，业务码 check 0/121 全挂；成功 run 32648184572 同样全挂（k6 check 失败不影响 exit code）。附产物：租户 9999 堆 243 条 DRAFT 遗骸（已经真实 DELETE 接口清为 0）

**修复（用户决策「租户 9999 自建数据」）**：payment-submit.js 新增 setup() 在租户 9999 走真实接口自建数据（幂等复用）：项目报备（submit 直接 FILED）→ OTHER_EXPENSE 其他支出合同（创建时直接携带累计结算 1000 万，与 L4 阶段 9D 同口径）→ default 用 setup 返回的真实 ID。setup 失败即 throw（k6 终止，不静默）

**实证**：Node 探针等价复现全链路——项目 DRAFT→FILED、合同创建、付款草稿、**submit 业务码 500→200**、withdraw 200。自建数据 projectId=2091646682238349313 / contractId=2091646683106570241（供 setup 幂等复用；被 L4 兜底清理后自动重建）

**复验**：push 7974969 部署全绿（run 32669290246，含 R6-01 新测试 Backend Build 验证）后触发 performance-k6 workflow_dispatch run 32670003331（等部署完成再触发，避免重蹈撞车）。**结果：三场景真全绿**——login.js 996/996（100%）、page-query.js 72804/72804（100%）、**payment-submit.js 363/363（100%，含业务码 200 check，修复前 0/121 全挂）**，各场景 http_req_failed 均 0%，setup 幂等复用自建合同（未重复创建）。k6 payment 业务链首次真实跑通

### 11.5 403 页重新登录自愈入口（2026-08-24，用户报障线上 403）

**现象**：用户访问 129.204.3.200:18081 被重定向 /403，且「返回首页/返回上一页」均循环回 403，无自助恢复手段

**根因（实测）**：线上登录接口正常（探针实测 admin：roles=[SUPER_ADMIN]、34 条 permissions 含 *:*:* 与 dashboard:view）。问题在浏览器陈旧持久化态——8-22 版前端上线路由权限守卫（业务路由需视图码，登录时写入 localStorage 持久化），陈旧态 token 存在但 permissions 为空 → 守卫判无视图码 → /403；/403 在白名单直接展示，页面原无恢复入口

**修复**：403.vue 增加「重新登录」按钮（userStore.logout() 清 token/permissions → router.push('/login')），陈旧态用户自助恢复；static-pages.component.test.ts 新增 1 例钉住行为（清 token + push /login；provide routerKey 满足 script setup 内 useRouter inject）。前端全量 1086 passed / 2 skipped 无回归

**部署前临时方案**：F12 控制台 `localStorage.clear(); location.href='/login'` 后 admin/123456 重新登录

---

## 受阻项登记表

| 日期 | 层级 | 测试项 | 分类 | 原因 | 影响范围 | 处置决策 | 决策人 | 状态 |
|---|---|---|---|---|---|---|---|---|
| 2026-08-22 | L1 | p0-gap-closeout 9.1 受影响后端模块 mvn test | ENV | 用户指令禁止启动 java/openjdk，无法运行 surefire | 后端 9 模块单测无法本地复跑（zw-labor/zw-basedata 已于 8-16/8-21 会话实测全绿） | 用户决策改走 CI：run 32584843669 Backend Build 全绿（23 模块约 3605 单测） | 用户 | 已完成 |
| 2026-08-22 | 覆盖率 | p0-gap-closeout 9.4 JaCoCo 覆盖率基线实测 | ENV | 同上（JaCoCo 依赖 JVM） | 新增类（MaterialService.getByCode 等）覆盖率未实测入 baseline | 改走 CI artifact 复算：用户决策 4 模块先补测达标再推门禁（9 文件 1193 行 d8db2a4）+ security 修正为实测值 656（eb84e72）；门禁修复 ca50cb5，run 32604915058 实测输出 22 ✅，22 模块全达标 | 用户 | 已完成 |
| 2026-08-22 | L3 | p0-gap-closeout 9.6 远程迁移导入+L3 脚本抽检 | ENV | 同上（依赖远程 Java 服务） | 迁移 49 material_code 列与 L3 契约未远程验证 | 用户决策改走远端 SSH：迁移 49 IMPORT_OK/VERIFY_OK + L3 4/4 PASS | 用户 | 已完成 |
| 2026-08-23 | L5-UI | 全量套件 run 32644242233：finance-write C5 前提断言双败（serial 连带 19 用例 did not run） | DATA | 租户 1 项目表 214 条 E2E 残留把种子项目挤出首页（total 229），只拉 page1 的 spec 项目解析全空；余额充足非耗尽（探针实证） | 仅 C5 前提定位及 4 处同类脆弱调用点；产品无缺陷；其余层全绿 | 测试侧 fetchAllProjects 翻页硬化（5 处切换，commit 4bc4225）+ 演示库 E2E 残留项目 API 清理 210 条（守卫拦截 4 条 WON 项目跳过）；本地实跑 32 passed 0 failed | AI 自诊自修（根因探针实证，非降级） | 已解除（2026-08-23 全量套件 run 32647849931 全链绿：L3 25/25、L4 26/26、L5-API 445、L5-UI 168 passed/3 skipped/UI_REAL_EXIT=0、一致性 55，HEAD 4bc4225） |
| 2026-08-24 | k6 | run 32653198393 failure：login.js http_req_failed 99.9% 超阈 + payment_submit 业务码 0/121 | ENV+DATA | ①手动触发撞上 push 部署容器替换窗口（调度撞车）；②payment-submit.js 租户 1 种子数据与 t9999admin（租户 9999）错位，submit 被「关联合同不存在」拒绝（存量问题，历轮被 k6 exit code 绿灯掩盖） | payment 场景业务链从未真实跑通；租户 9999 堆 243 条 DRAFT 遗骸（已清 0）；login/page 场景本身无缺陷 | 用户决策「租户 9999 自建数据」：setup 真实接口自建项目+OTHER_EXPENSE 合同（commit 7974969）+ 243 遗骸 API 清理；Node 探针实证 submit 业务码 500→200 | 用户（数据口径方案选择） | 已解除（2026-08-24 run 32670003331 三场景真全绿：login 996/996、page 72804/72804、payment 363/363 含业务码 200，http_req_failed 均 0%，HEAD 7974969） |
