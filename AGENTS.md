# AGENTS.md - AI 代理人工作约定

本项目采用 **Kiro**（AI 开发代理）进行 Spec 驱动开发，覆盖需求分析、设计、任务拆解、代码实现与联调验证全流程。

## Spec 驱动开发
项目采用 Kiro 的 Spec 工作流：**Requirements → Design → Tasks**，每个 feature 对应一个独立目录，包含 `requirements.md`、`design.md`、`tasks.md` 三个文件。

### Spec 文件位置

```
.kiro/specs/
```

### 已完成的 Spec 列表

| Spec 名称 | 说明 |
|-----------|------|
| `zw-insight-platform` | 平台整体架构与模块划分 |
| `p0-core-features` | P0 核心功能（CRUD、审批、流程） |
| `p0-data-permission-overdue` | P0 数据权限 & 逾期提醒 |
| `p1-system-integrity` | P1 系统完整性（补全缺失功能） |
| `p1-business-completion` | P1 业务补充（劳务/分包/机械等） |
| `p2-quick-wins` | P2 快速优化项 |
| `p2-experience-enhancement` | P2 体验增强 |
| `p2-business-enhance` | P2 业务增强 |
| `p2-advanced` | P2 高级功能 |
| `frontend-backend-integration` | 前后端联调对齐（63 项核心错位） |
| `consistency-audit` | 一致性审计工具开发 |

## 联调验证基座

用于验证远程联调服务器上的真实接口是否正常工作。

```powershell
# Windows：PowerShell .\keys\verify.ps1
```

```bash
# Linux / SSH
bash keys/verify-base.sh
```

验证基座会依次调用核心 API 端点，确认 HTTP 状态码和响应结构正确。凭证文件 `keys/zwinsight.pem` 已纳入 `.gitignore`。

## L4 全生命周期测试（隔离租户 9999）

`keys/lifecycle-sim-v2.sh` 在隔离测试租户（tenant_id=9999，账号 t9999admin）上跑通 19 个阶段的全业务闭环：报备→立项→投标→中标→施工合同→预算（含 BLOCK 拦截负向用例）→四类支出合同→材料入出库→机械/劳务/分包执行与结算→产值→开票收款→采购结算→付款闭环→驳回分支（退回重审/终止重提）→竞工结算→结案。每阶段含状态/金额硬断言，退出码严格反映结果。

```bash
# 首次：初始化测试租户（幂等）+ 部署 BPMN 到租户 9999
bash keys/init-test-tenant.sh
ZWI_USER=t9999admin ZWI_PASS=123456 ZWI_TENANT_ID=9999 bash keys/deploy-bpmn.sh

# 运行 L4 + 清理验收（biz_表 tenant 9999 残留应为 0）
bash keys/lifecycle-sim-v2.sh
bash keys/verify-l4-clean.sh
```

关键约束：测试租户编号规则用 `T9` 前缀（业务编号唯一键为全局唯一，防与租户 1 撞号）；兜底清理仅限 `biz_%` 表（严禁误删 sys_user/serial_number_rule 等含 tenant_id 的系统表）；待办驱动仅 ACT_RU_TASK 加 taskId（候选组任务不入 assignee 待办；SUPER_ADMIN 可完成任意任务）。

## 演示种子数据

用于快速填充默认租户（`tenant_id=1`）的全模块演示数据，登录系统即可看到完整的项目、合同、预算、财务等业务链路。

### 脚本位置

```
deploy/db-init/31_V2026_26__seed_demo_data.sql
```

### 设计要点

- **租户**：全部记录 `tenant_id=1`，为持久化演示数据
- **ID 段***：固定使用 `90001-99999`，避免与业务雪花 ID 及已有种子（`900001-900005` 编号规则）冲突
- **幂等**：全部 `INSERT IGNORE`，可重复执行不报错（重复错 1062 被忽略）
- **依赖顺序**：按 Layer 0-14 从底层到顶层插入（基础数据→项目→投标→合同→预算→产值→材料→机械→劳务→分包→现场→财务→询价→消息→评价），覆盖 55+ 张业务表
- **数据闭环**：3 个不同生命周期项目――`90001 滨江花园一期`（施工中，全模块）、`90002 城南市政道路改造`（已竣工，结算/质保金）、`90003 高新区产业园二期`（已报备，投标）；金额按「合同→产值→开票→收款→预算→各支出合同→结算→付款」逻辑自洽

### 导入与验证

```bash
# 导入种子并统计行数（幂等）
bash keys/verify-seed.sh import

# 从 DB 行数统计 / 从 API 抽检 / 完整验证
bash keys/verify-seed.sh db
bash keys/verify-seed.sh api
bash keys/verify-seed.sh
```

验证脚本 `keys/verify-seed.sh` 复用 `verify-base.sh` 的真实登录能力，抽检 `project/page`、`contract/page`、`finance/payment-apply/page` 等分页接口，并直连 MySQL 校验固定 ID 段行数。

## 一致性审计工具

位于 `tools/consistency-audit/`，是一个 Node.js CLI 工具，自动扫描后端 Controller、PC 前端 api/*.ts、移动端 api/*.ts，生成三端一致性审计报告。

### 使用方法

```bash
cd tools/consistency-audit
npm install
npm run dev          # 运行审计（开发模式，tsx 直接执行）
npm run build        # 编译 TypeScript
npm test             # 运行属性测试（fast-check）
```

> ⚠️ **必须在项目根目录运行**（或显式传 `--root <项目根>`）。CLI 默认以 `process.cwd()` 为扫描根目录，在 tools 子目录下运行会扫不到任何 API，产出「后端 API: 0 + 174 Critical」的无效报告（2026-07-24、2026-08-23 两次踩坑）。正确姿势：
>
> ```bash
> # 在 tools/consistency-audit 目录内运行时须显式指定根目录与输出目录
> npx tsx src/cli.ts --root ../../ --output ../../audit-reports
> ```
>
> 验证报告有效性：确认摘要中「后端 API: 740 / 已审核模块: 20/20」非零后再采信结论。

### 审计输出

- 报告自动生成到 `audit-reports/` 目录（JSON + Markdown 双格式）
- 包含模块级别的不一致项分类：`FEATURE_MISSING`、`HTTP_METHOD_MISMATCH`、`FRONTEND_EXTRA_API`、`BACKEND_ORPHAN_API` 等
- 严重程度：Critical > Major > Minor

## 功能深度账本工具

位于 `tools/feature-ledger/`，是一个 Node.js CLI 工具，对 PC + 移动端全部功能页面做 L0-L4 成熟度评分与八维缺口标注，产出可排期的 ROI 差距清单。与一致性审计正交：审计评接口对齐，账本评功能能力成熟度。

```bash
cd tools/feature-ledger
npm install
npm run dev -- scan     # 扫描 → data/ledger-data.json（manual 人工字段不受影响）
npm run dev -- report   # 生成 audit-reports/feature-ledger/feature-ledger-report.md
npm test                # 规则/评分/合并单元测试
```

账本数据 `data/ledger-data.json` 按条目分 auto（脚本覆写）/ manual（人工填写保留）两层；人工判断填 `levelFinal` / `gapNotes` / `benchmarkNote` / `roi`。信号规则集中在 `src/signals.ts`，改动后须将 `SIGNAL_VERSION` +1。详见 `tools/feature-ledger/README.md`。

## Steering 规则

项目级 Steering 规则位于：

```
~/.kiro/steering/base.md        # 全局用户级规则
```

当前无项目级 `.kiro/steering/` 目录，规则通过全局配置生效。

## 开发约定

以下约定在 AI 代理协作开发中必须遵守：

### 1. 真实接口，不用假数据

所有业务开发必须对接真实后端接口。禁止使用 mock 数据或静默 fallback。可以有备选方案，但不能用完全不真实的数据来实现功能。

### 2. 后端 Controller 为 Source of Truth

前端 API 定义必须与后端 Controller 注解严格一致：

- HTTP 方法（GET/POST/PUT/DELETE）
- 路径（`@RequestMapping` 值）
- 请求/响应字段名

当出现分歧时，以后端 Controller 为准修改前端。

### 3. RESTful 约定

- 路径格式：`/api/v1/{module}/{resource}`
- 分页查询：GET + Query Params（`page`、`size`）
- 创建：POST
- 更新：PUT `/{id}`
- 删除：DELETE `/{id}`
- 详情：GET `/{id}`
- 批量操作：POST `/{resource}/batch`

详细规范见 `audit-reports/rest-convention.md`。

### 4. 前后端一致性检查

每次涉及接口变更的开发完成后，运行一致性审计：

```bash
cd tools/consistency-audit && npm run dev
```

确保新增/修改的接口不引入 Critical 级别的不一致项。

### 5. 数据库变更

- 增量迁移脚本放入 `deploy/db-init/`，按序号命名
- 字段使用规范：`BigDecimal`/`DECIMAL(18,2)`（金额）、`deleted`（逻辑删除）、`version`（乐观锁）、`tenant_id`（租户隔离）

### 6. 技术方案调研

进行技术方案选型时，优先查找官方文档了解最新用法，寻找稳定可靠的开源项目进行对比选择。

### 7. 改造记录

进行项目优化改造时，需完整记录改造的详细信息（变更原因、影响范围、回滚方案），确保后续能从上下文恢复。

### 8. 测试开发规划

以下规则在开发新功能或修复 Bug 时必须遵守：

#### 新模块必须包含单元测试

- 每个新建 Service 类的 public 方法至少编写 1 个正常路径 + 1 个异常路径测试
- 使用 `@ExtendWith(MockitoExtension.class)` + Mockito Mock 所有外部依赖
- 覆盖率治理（2026-08-13 实测校准）：**实际生效的门槛是 `tests/coverage-baseline.json` 基线对比（只升不降，CI 逐模块比对，回落即构建失败）**。pom 中 jacoco check 0.80 绑定 verify 阶段，但 CI 跑 `mvn package` 不触发 verify，故 80% 门槛目前未实际强制执行（目标值，待阶段三达成后将 CI 命令切到 verify 启用）。各模块实际行覆盖率 14.9%~85%（基线 JSON 为千分比），实测明细见 `tests/TESTING-MATURITY.md` 附录 A

#### 集成测试使用 tenant_id=9999

- 所有集成测试数据必须使用 `tenant_id=9999`（自动化测试租户）
- 严禁在测试中使用真实租户 ID 或操作生产数据
- `@AfterAll` 必须调用 `TestDataCleaner.cleanByTenantId(9999L)` 清理测试数据
- Redis 测试键使用 `test:t9999:` 前缀，测试后清除

#### PR 前运行 L1 单元测试

- 提交 PR 前必须在本地运行 `mvn test` 确认单元测试通过（注意：勿在本地跑全量并行构建 `mvn -T 1C clean package`，22 模块并行 fork 几十个 JVM 会卡死机器，重型验证一律 CI/服务器执行）
- CI backend job 执行 `mvn -B -T 1C clean package`（运行全量单元测试 + JaCoCo 报告），并与 `tests/coverage-baseline.json` 对比：任一模块覆盖率回落即构建失败。注：-T 1C 在 2 vCPU runner 上实测无提速效果（510s vs 串行 503s，2026-08-14 run 31762169585），保留仅为多核 runner 兼容；实测提速来自 L2 门控（省 302s）与前端镜像缓存（省 ~50s）
- **CI 测试套件触发策略（2026-08-13 用户决策，2026-08-14 扩展）**：push 默认只跑 Backend Build（L1 单测 + 覆盖率基线）→ Deploy → 部署冒烟（健康检查 + API 文档收敛断言），**L2 Testcontainers、Integration Test（L3/L4/L5）与 k6 默认不跑**（全量约 40 分钟，L2 单项实测 302s）。全量套件唯一触发入口：手动 workflow_dispatch 选 `run_tests=true`（`gh workflow run deploy.yml -f run_tests=true`）
- pom jacoco check（minimum 0.80，BUNDLE LINE）绑定 verify 阶段，CI 当前跑 package 不触发；阶段三目标达成后 CI 切到 verify 强制（见 spec 3.3）

#### L3 API 契约验证

- 9 个独立验证脚本位于 `keys/`：`test-api-{project,labor,machine,subcontract,material,purchase,finance,quote,message}.sh`
- 每个脚本使用 `jq` 进行字段结构断言（分页 `.code==200/records 数组/total 数字 + 详情`.data.id 存在）
- 服务器需预装 `jq`（`apt-get install jq`）
- 断言不满足一律 FAIL，不静默跳过

#### 测试受阻汇报规则（强制，AI 代理与人均适用）

测试因环境或其他原因无法执行时（Docker 未启、网络不可达、凭证失效、工具装不上、覆盖率采集失败等）：

1. **禁止静默跳过、禁止标记为通过、禁止用 mock/假数据降级替代真实验**（与本项目"真实接口真实流程"原则一致）
2. 必须立即：① 停止该项执行 ② 在 `.kiro/specs/test-maturity-upgrade/tasks.md` 末尾"受阻项登记表"追加一行（日期/层级/测试项 / 分类 ENV|DEP|NET|CRED|DATA|OTHER/原因/影响范围/处置决策/决策人 / 状态）③ 向用户汇报（受阻原因 + 影响范围 + 三选项：修复环境/延期/缩减范围）
3. 用户决策后回填台账"处置决策 / 决策人"列；禁止 AI 自行决定降级方案
4. 汇报模板与历史案例见 `tests/TESTING-MATURITY.md` 附录 B；本期 JaCoCo 中文路径坑需加 `-Djacoco.destFile=<ASCII 路径>`

#### 测试体系文档

- 详细的测试架构、执行方式、添加新测试指南见 `tests/README.md`
- 测试常量定义见 `zw-insight-server/zw-common/src/test/java/com/zwinsight/common/base/TestConstants.java`
- 统一编排脚本：`bash tests/run-all-tests.sh`

### 9. 临时文件与文档管理

AI 代理在开发、调试、评审过程中产生的临时产物必须遵循以下规则：

#### 命名约定

- 所有临时文件必须以 `_` 前缀命名（如 `_review_dashboard.png`、`_test4.log`）
- `.gitignore` 已配置 `keys/_*`、`*.log`、`test-results/`、`**/eng.traineddata` 等排除规则
- 新增临时文件类型时，同步更新 `.gitignore`

#### 生命周期

- **任务结束即清理**：每次任务（开发/调试/评审/验证）完成后，必须删除本次产生的所有临时文件
- **不得跨会话残留**：截图、日志、诊断输出、验证码图片等不得遗留在工作区
- **构建产物按需保留**：`dist/`、`deploy/zw-insight-app.jar` 等可再生产物可保留，但不应主动创建冗余副版

#### 禁止事项

- 禁止在项目根目录或 `src/` 下创建任何非源码临时文件
- 禁止将调试截图、OCR 训练数据、Playwright trace 等大体积二进制文件留在工作区
- 禁止创建用于"跟踪进度"的 `.md` 文档（如 `TODO.md`、`PROGRESS.md`）

#### 清理检查清单

任务完成后自查：

```
✅ 根目录无 _*.png / _*.log 残留
✅ keys/ 无新增 __* 诊断产物
✅ zw-insight-web/ 无 test-results/ 或 eng.traineddata
✅ 无新增未纳入 .gitignore 的临时文件
```

---

**测试开发约定**：
- **实际生效门槛：覆盖率基线只升不降**（CI 逐模块比对 coverage-baseline.json，回落即失败）
- pom jacoco check 0.80（verify 阶段）为目标门槛，当前 CI 跑 package 未触发，阶段三达成后启用
- 新增模块必须同步登记覆盖率基线（实测值入 baseline JSON）才允许合并
- 存量模块按阶段目标逐步提升：当前实际 14.9%~85%，目标 80%
