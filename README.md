# 中维智营 ZW-Insight

工程项目管理 SaaS 平台，覆盖项目报备、投标、合同签订、施工过程、财务结算到项目归档的完整生命周期。面向建筑/工程企业，支持多租户隔离、PC + 移动端双端协同。

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 · JDK 21 · MyBatis-Plus · MySQL 8 · Redis 7 · MinIO · RabbitMQ · Flowable 7 |
| PC 前端 | Vue 3 · Vite · TypeScript · Element Plus · ECharts |
| 移动端 | uni-app · Vue 3 · TypeScript（H5 / 微信小程序 / iOS / Android） |
| 部署 | Docker Compose · Nginx · kkFileView（在线预览） |

## 项目结构

```
ZW-Insight/                     # Monorepo 根目录
├── zw-insight-server/          # 后端（22 个 Maven 子模块，90+ Controller）
├── zw-insight-web/             # PC 前端（Vue 3 + Element Plus）
├── zw-insight-app/             # 移动端（uni-app）
├── tools/                      # 开发工具
│   └── consistency-audit/      # 三端一致性审计 CLI（Node.js + TypeScript）
├── deploy/                     # 部署配置（Dockerfile · docker-compose · DB 迁移脚本）
├── docs/                       # 产品文档 & 功能表
├── audit-reports/              # 审计报告存档
├── keys/                       # 联调脚本与凭证（已 gitignore）
├── sql/                        # 增量 SQL 迁移
├── docker-compose.yml          # 本地开发依赖（MySQL/Redis/MinIO/RabbitMQ/kkFileView）
└── .kiro/specs/                # Spec 驱动开发文档（需求 → 设计 → 任务）
```

## 开发环境要求

- JDK 21+
- Maven 3.9+
- Node.js 18+（推荐 20 LTS）
- Docker & Docker Compose
- pnpm（前端包管理，可选 npm）

## 快速开始

### 1. 启动基础设施

```bash
docker-compose up -d
```

将启动 MySQL(3306)、Redis(6379)、MinIO(9000/9001)、RabbitMQ(5672/15672)、kkFileView(8012)。

### 2. 初始化数据库

按顺序执行 `deploy/db-init/` 目录下的 SQL 文件，或使用联调环境已有的远程数据库。

### 3. 启动后端

```bash
cd zw-insight-server
mvn spring-boot:run -pl zw-app
```

API 地址：http://localhost:8080  
接口文档：http://localhost:8080/doc.html

### 4. 启动 PC 前端

```bash
cd zw-insight-web
npm install
npm run dev
```

开发地址：http://localhost:3000

### 5. 启动移动端（H5）

```bash
cd zw-insight-app
npm install
npm run dev:h5
```

### 联调环境

项目提供远程联调服务器，联调脚本位于 `keys/` 目录：

- `keys/verify.ps1` — PowerShell 联调验证基座（Windows）
- `keys/verify-base.sh` — Bash 联调验证基座（Linux/SSH）

具体连接凭证见 `keys/zwinsight.pem`，该目录已纳入 `.gitignore`。

## 部署

生产部署使用 `deploy/` 目录下的配置：

```bash
cd deploy
docker-compose -f docker-compose.deploy.yml up -d
```

包含：
- `Dockerfile` — 后端 JAR 构建镜像
- `docker-compose.deploy.yml` — 全栈编排
- `db-init/` — 数据库初始化 & 增量迁移脚本（00 ~ 21）

## 联调状态

基于 `tools/consistency-audit` 自动化审计工具持续验证前后端一致性：

- **63 项核心错位**（HTTP 方法不匹配 38 + 前端多余 API 25）已全部对齐
- 归类 A（改前端）31 项 / 归类 B（改后端）30 项 / 归类 C（噪音）2 项
- 审计核心项（Critical 级别的方法/路径/字段错位）归零
- 完整台账：`audit-reports/alignment-ledger.md`

当前残留项均为 Minor 级别（后端孤儿 API、前端超范围实现），属于功能预留，不影响业务运行。

## 开发规范

- RESTful 接口：`/api/v1/{module}/{resource}`
- 统一响应：`R<T>` (code / message / data / timestamp)
- 金额字段：`BigDecimal` / `DECIMAL(18,2)`
- 多租户隔离：`tenant_id` 字段，MyBatis-Plus 拦截器自动注入
- 逻辑删除 (`deleted`) + 乐观锁 (`version`)
- 后端 Controller 注解为接口 Source of Truth

## 相关文档

| 文档 | 位置 |
|------|------|
| Spec 驱动开发文档 | `.kiro/specs/` |
| 一致性审计报告 | `audit-reports/` |
| 功能表 & 模块全景图 | `docs/` |
| REST 约定规范 | `audit-reports/rest-convention.md` |
| AI 代理使用说明 | `AGENTS.md` |

## License

Private — 中维智营内部使用
