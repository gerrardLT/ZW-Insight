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
