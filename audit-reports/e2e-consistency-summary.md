# 前后端数据一致性 E2E 测试 —— 汇总不一致清单

> 生成时间：2026-07-23
> 测试方式：Playwright 真实模式（channel: chrome），连真实联调服务器
> 前端 `http://129.204.3.200:18081` · 后端 `http://129.204.3.200:18080` · admin
> 方法论：`page.waitForResponse` 抓取页面真实调用的 API JSON，再将 DOM 渲染值与响应字段逐一断言（列值 / 枚举翻译 / 金额·日期格式化 / 分页 total / 详情回显 / 表单回显）

## 一、总览

| 指标 | 数值 |
|------|------|
| 覆盖模块 | 20 |
| 覆盖页面 | 53 |
| 测试用例 | 54 passed / 0 failed / 0 did-not-run |
| 字段级完全一致页面 | 33 |
| **真实缺陷（需修复）** | **7** |
| 非缺陷（测试租户空列表 `__empty__`） | 12 |
| 备注类（`__note__`） | 1 |

> 说明：为保证「全页覆盖」，基础设施级发现（接口 500、列表绑定错误、静默 fallback）在测试基座中**记录进报告但不抛异常**，避免 `describe.serial` 组内后续页面被跳过；字段级不一致仍为硬失败。因此 54 passed 不代表零缺陷，真实缺陷见下表。

## 二、真实缺陷清单（需修复）

### 2.1 接口 500 —— 页面表格恒空（6 项，Critical）

> 以下根因均已通过 **SSH 连服务器抓取 zwi-backend 真实异常栈 + 查部署库 information_schema 实际列** 实证确认（非推测）。

| # | 模块 | 页面 | 接口 | 服务器实证 Cause | 根因 |
|---|------|------|------|------------------|------|
| 1 | archive | `/archive/index` | `GET /v1/archive/project/0` | `Unknown column 'cumulative_output'`（级联 biz_subcontract） | 同 #4，聚合查询查到 biz_subcontract 时缺列爆出；另见 2.2 接口语义/假 stub |
| 2 | basedata | `/basedata/supplier-evaluation` | `GET /v1/basedata/supplier-evaluation` | `Unknown column 'evaluation_type' in 'field list'` | 实体 `BizSupplierEvaluation.evaluationType` 对应列 `evaluation_type` 在部署库缺失，**db-init 无任何脚本创建它** |
| 3 | labor | `/labor/contract` | `GET /v1/labor/contract/page` | `Unknown column 'cumulative_output' in 'field list'` | 迁移 `V2026_22`（补 biz_labor_contract.cumulative_output）**未在部署库执行** |
| 4 | subcontract | `/subcontract/contract` | `GET /v1/subcontract/contract/page` | `Unknown column 'cumulative_output' in 'field list'` | 迁移 `V2026_22`（补 biz_subcontract.cumulative_output）**未执行** |
| 5 | platform | `/platform/storage` | `GET /v1/file/storage` | `Table 'zw_insight.file_storage' doesn't exist` | 实体 `FileStorage` 对应表 `file_storage` 从未被建，**db-init 无任何建表脚本** |
| 6 | workflow | `/workflow/rollback` | `GET /v1/workflow/rollback/logs` | `Unknown column 'tenant_id' in 'where clause'` | `TenantLineInnerInterceptor` 给所有表追加 `tenant_id=1`，但 `biz_approval_rollback_log` 无 tenant_id 列（该实体不继承 BaseEntity）→ 分页 COUNT 报错 |

> 影响：以上 6 个页面加载后表格永久为空、用户无法看到任何数据，属阻断级缺陷。前端未显式提示错误（列表接口异常被吞），体验上表现为「空列表」而非「加载失败」。

#### 2.1.1 共性根因 —— 部署库迁移落后于应用代码

查 `flyway_schema_history` 实证：执行历史从 `2026.21` 直接跳到 `2026.24`，**`V2026_22`（p0_subcontract_labor_cumulative_output）、`V2026_23`（p0_material_inventory_detail）、`V2026_26`（seed_demo_data）从未应用**；而部署 JAR 的实体已包含 `cumulative_output` / `evaluation_type` / `file_storage` / rollback 租户逻辑。即**部署应用的实体模型新于已应用的 DB 迁移版本**，导致 SELECT 引用不存在的列/表，被全局异常兜底为 500。

修复路径（按缺陷）——**已实施**：

> 关键前提：真正的 Flyway 迁移源是 `zw-insight-server/zw-app/src/main/resources/db/migration/`（`classpath:db/migration`），**不是** `deploy/db-init/`（后者仅在全新 MySQL 卷首次初始化时执行）。`V2026_22/23` 从未加入该类路径，故线上 Flyway 永远不会执行它们。又因 `application.yml` 中 `out-of-order=false`，**不能补低版本号**（会触发乱序校验失败），必须用高于当前最高已应用版本（`2026.25`，rank 28）的**前向新版本**。

- #3 #4 #1（cumulative_output）：新增 Flyway 迁移 `V2026_27__labor_subcontract_cumulative_output.sql`（幂等 ALTER 给 biz_labor_contract / biz_subcontract 补 cumulative_output）。
- #2（evaluation_type）：新增 `V2026_28__supplier_evaluation_type.sql`（幂等 `ALTER TABLE biz_supplier_evaluation ADD COLUMN evaluation_type VARCHAR(20)`）。
- #5（file_storage）：新增 `V2026_29__create_file_storage.sql`（`CREATE TABLE IF NOT EXISTS file_storage`，列与 `FileStorage` 实体严格对齐）。
- #6（rollback tenant_id）：改 `MybatisPlusConfig.tenantLineInnerInterceptor().ignoreTable()`，把 `biz_approval_rollback_log` 加入忽略清单（与既有 `msg_available_shortcut` 同一模式——实体不继承 BaseEntity、无 tenantId 列）。**不加列**：因实体无 tenantId 字段，加列会导致插入不填充、查询失配。
- 基线同步：`deploy/db-init/00_schema.sql` 同步补 `biz_supplier_evaluation.evaluation_type` 与 `file_storage` 表，保证全新安装基线自洽（cumulative_output 基线已含）。

生效方式：以上均随 **push 到 main → GitHub Actions（`mvn -B clean package` 构建 JAR + `docker compose build --no-cache backend` 重建后端容器）**部署；后端启动时 Flyway 自动应用 `V2026_27/28/29`，新拦截器忽略清单同时随新 JAR 生效。本地无 Maven，无法手工构建 JAR。

### 2.2 archive/index 结构与假实现问题（Critical）✅ 已修复

- **接口语义错位**：列表页调用 `getArchivePage → GET /v1/archive/project/{projectId||0}`，该接口返回「单项目档案聚合」对象，**而非分页结构** `{records,total}`。页面 `res.data?.records` 恒为 `undefined` → 即便接口正常，表格也永远为空。
- **查询条件失效**：顶部 5 个分类 tab / 档案名称 / 归档日期查询条件在该接口下完全不生效（接口只接收 `projectId`）。
- **假 stub 违约**：`api/archive.ts` 中 `createArchive/updateArchive/deleteArchive` 为 `Promise.resolve({code:200})` 假实现，**违反「真实接口、无静默 fallback」约定**。

> **已实施方案**：后端 `ArchiveController` 本质是「只读聚合视图，不产生新数据」，不存在通用档案 CRUD 实体/表，原页面的分类 tab + 新增/编辑/删除均为凭空虚构。已将 `archive/index.vue` 重写为**项目档案只读查看器**：顶部项目选择器（`getProjectList`）→ 选中后调 `getProjectArchive(projectId)` → 用 `el-descriptions` + `el-tabs` 只读渲染后端真实聚合字段（项目基本信息 / 项目成员 / 施工合同 / 付款记录 / 收款记录 / 分包合同 / 机械合同 / 资金汇总），表格列与后端实体字段严格对齐。同步删除 `api/archive.ts` 中 `getArchivePage/createArchive/updateArchive/deleteArchive` 四个假 stub。

### 2.3 dashboard 静默 fallback（Major）✅ 已修复

| 模块 | 页面 | 问题 |
|------|------|------|
| dashboard | `/dashboard` | `index.vue` 三处 `catch` 静默兜底空数据（loadStats / pie / bar），接口异常时无任何错误提示，**违反「无静默 fallback」约定** |

> **已实施方案**：`dashboard/index.vue` 三处 `catch`（loadStats / loadPieChart / loadBarChart）均加入 `ElMessage.error(...)` 显式错误提示，接口异常时用户可感知；同时保留空状态兜底 UI（置 0 / 空图）避免页面区域空白或卡死，兼顾「不静默」与可用性。

## 三、非缺陷 —— 测试租户空列表（`__empty__`，12 项）

> 这些页面接口正常（code=200），仅因**自动化测试租户下无种子数据**而列表为空，已跳过逐行比对。**非一致性缺陷**，如需字段级校验需补充种子数据。

| 模块 | 页面 | 接口 |
|------|------|------|
| archive | `/archive/other-expense-contract` | `GET /v1/archive/other-expense-contract` |
| archive | `/archive/other-income-contract` | `GET /v1/archive/other-income-contract` |
| platform | `/platform/tenant-type` | `GET /v1/platform/tenant-type` |
| workflow | `/workflow/approval` | `GET /v1/workflow/approval/todo` |
| workflow | `/workflow/process` | `GET /v1/workflow/process` |
| system | `/system/log` | `GET /v1/system/log/oper` |
| system | `/system/print-template` | `GET /v1/print-template/list` |
| system | `/system/version` | `GET /v1/system/version/list` |
| message | `/message/notice` | `GET /v1/message/notice` |
| message | `/message/push-config` | `GET /v1/message/push-config` |
| site | `/site/schedule` | `GET /v1/site/schedule/page` |

## 四、字段级完全一致模块（✅ 33 页）

| 模块 | 一致页面数 | 说明 |
|------|-----------|------|
| project | 3 | 列表 + 详情回显 + 编辑回显全部一致 |
| contract | 1 | 施工合同列表 |
| budget | 1 | 预算列表 |
| finance | 2 | 开票申请 / 付款申请 |
| purchase | 1 | 采购合同列表 |
| material | 1 | 材料入库列表 |
| machine | 1 | 机械合同列表 |
| hr | 4 | 入职 / 离职 / 车辆 / 办公用品 |
| site | 2 | 施工日志 / 质量检查 |
| tender | 2 | 证件管理 / 投标报名 |
| basedata | 6 | 公司 / 材料字典 / 甲方 / 供应商 / 黑名单 / 检查方案 |
| system | 5 | 用户 / 岗位 / 数据备份 / 编号规则 / 通用模板 |
| platform | 1 | 租户管理 |
| message | 2 | 公告管理 / 消息中心 |
| user | 1 | 登录设备 |

## 五、修复优先级建议

1. **P0（阻断）**：排查 6 个 500 接口的服务端异常栈，重点核对 labor/subcontract 合同表 schema 漂移（对比 `deploy/db-init` 迁移脚本与已部署库实际列），补齐缺失列。
2. **P0（阻断）**：修正 `archive/index` 列表接口语义 —— 改用真正的分页档案列表接口，或调整前端绑定；同时补全 `createArchive/updateArchive/deleteArchive` 真实实现。
3. **P1**：移除 dashboard `index.vue` 三处静默 catch，改为显式错误提示（Message.error / 空态提示）。
4. **P2（可选）**：为 12 个 `__empty__` 页面补充测试租户种子数据，以启用字段级逐行校验。

## 六、测试基座产物位置

- 各模块明细报告：`zw-insight-web/e2e/consistency/reports/*.md`（20 份）
- 测试规格：`zw-insight-web/e2e/consistency/*.spec.ts`（20 份）
- 复用基座：`zw-insight-web/e2e/consistency/consistency-helper.ts`
- 枚举基线：`zw-insight-web/e2e/consistency/enum-baseline.ts`
- 运行命令：`npx playwright test --project=consistency-real --reporter=list`
