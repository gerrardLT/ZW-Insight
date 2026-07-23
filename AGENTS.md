# AGENTS.md — AI 代理工作约定

本项目使用 **Kiro**（AI 开发代理）进行 Spec 驱动开发，覆盖需求分析、设计、任务拆解、代码实现与联调验证全流程。

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
| `p1-business-completion` | P1 业务补全（劳务/分包/机械等） |
| `p2-quick-wins` | P2 快速优化项 |
| `p2-experience-enhancement` | P2 体验增强 |
| `p2-business-enhance` | P2 业务增强 |
| `p2-advanced` | P2 高级功能 |
| `frontend-backend-integration` | 前后端联调对齐（63 项核心错位） |
| `consistency-audit` | 一致性审计工具开发 |

## 联调验证基座

用于验证远程联调服务器上的真实接口是否正常工作。

```powershell
# Windows（PowerShell）
.\keys\verify.ps1
```

```bash
# Linux / SSH
bash keys/verify-base.sh
```

验证基座会依次调用核心 API 端点，确认 HTTP 状态码和响应结构正确。凭证文件 `keys/zwinsight.pem` 已纳入 `.gitignore`。

## 演示种子数据

用于快速填充默认租户（`tenant_id=1`）的全模块演示数据，登录系统即可看到完整的项目、合同、预算、财务等业务链路。

### 脚本位置

```
deploy/db-init/31_V2026_26__seed_demo_data.sql
```

### 设计要点

- **租户**：全部记录 `tenant_id=1`，为持久化演示数据
- **ID 段**：固定使用 `90001-99999`，避免与业务雪花 ID 及已有种子（`900001-900005` 编号规则）冲突
- **幂等**：全部 `INSERT IGNORE`，可重复执行不报错（重复键 1062 被忽略）
- **依赖顺序**：按 Layer 0-14 从底层到顶层插入（基础数据→项目→投标→合同→预算→产值→材料→机械→劳务→分包→现场→财务→询价→消息→评价），覆盖 55+ 张业务表
- **数据闭环**：3 个不同生命周期项目——`90001 滨江花园一期`（施工中，全模块）、`90002 城南市政道路改造`（已竣工，结算+质保金）、`90003 高新区产业园二期`（已报备，投标）；金额按「合同→产值→开票→收款→预算→各支出合同→结算→付款」逻辑自洽

### 导入与验证

```bash
# 导入种子并统计行数（幂等）
bash keys/verify-seed.sh import

# 仅 DB 行数统计 / 仅 API 抽查 / 完整验证
bash keys/verify-seed.sh db
bash keys/verify-seed.sh api
bash keys/verify-seed.sh
```

验证脚本 `keys/verify-seed.sh` 复用 `verify-base.sh` 的真实登录能力，抽查 `project/page`、`contract/page`、`finance/payment-apply/page` 等分页接口，并直连 MySQL 校验固定 ID 段行数。

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

### 审计输出

- 报告自动生成到 `audit-reports/` 目录（JSON + Markdown 双格式）
- 包含模块级别的不一致项分类：`FEATURE_MISSING`、`HTTP_METHOD_MISMATCH`、`FRONTEND_EXTRA_API`、`BACKEND_ORPHAN_API` 等
- 严重级别：Critical > Major > Minor

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

### 8. 测试开发规则

以下规则在开发新功能或修复 Bug 时必须遵守：

#### 新模块必须包含单元测试

- 每个新建 Service 类的 public 方法至少编写 1 个正常路径 + 1 个异常路径测试
- 使用 `@ExtendWith(MockitoExtension.class)` + Mockito Mock 所有外部依赖
- 核心业务模块（project, contract, budget, finance, material, machine, labor, subcontract）JaCoCo 行覆盖率必须 ≥ 80%

#### 集成测试使用 tenant_id=9999

- 所有集成测试数据必须使用 `tenant_id=9999`（自动化测试租户）
- 严禁在测试中使用真实租户 ID 或操作生产数据
- `@AfterAll` 必须调用 `TestDataCleaner.cleanByTenantId(9999L)` 清理测试数据
- Redis 测试键使用 `test:t9999:` 前缀，测试后清除

#### PR 前运行 L1 单元测试

- 提交 PR 前必须在本地运行 `mvn test` 确认单元测试通过
- CI 中 `mvn -B clean package` 会自动执行单元测试 + JaCoCo 覆盖率检查
- 覆盖率不达标将导致 CI 构建失败

#### 测试体系文档

- 详细的测试架构、执行方式、添加新测试指南见 `tests/README.md`
- 测试常量定义见 `zw-common/src/test/java/com/zwinsight/common/base/TestConstants.java`
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
- **构建产物按需保留**：`dist/`、`deploy/zw-insight-app.jar` 等可再生产物可保留，但不应主动创建冗余副本

#### 禁止事项

- 禁止在项目根目录或 `src/` 下创建任何非源码临时文件
- 禁止将调试截图、OCR 训练数据、Playwright trace 等大体积二进制文件留在工作区
- 禁止创建用于“跟踪进度”的 `.md` 文档（如 `TODO.md`、`PROGRESS.md`）

#### 清理检查清单

任务完成后自查：

```
✅ 根目录无 _*.png / _*.log 残留
✅ keys/ 无新增 _* 诊断产物
✅ zw-insight-web/ 无 test-results/ 或 eng.traineddata
✅ 无新增未纳入 .gitignore 的临时文件
```
