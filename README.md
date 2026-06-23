# 中维智营 ZW Insight

工程项目管理 SaaS 平台，覆盖从项目报备、投标、合同签订、施工过程、财务结算到项目归档的完整生命周期。

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2 + MyBatis-Plus + MySQL 8 + Redis + Flowable 7 + MinIO + RabbitMQ |
| PC前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts |
| 移动端 | uni-app + Vue 3 + TypeScript（支持 H5/微信小程序/iOS/Android） |

## 项目结构

```
ZW-Insight/
├── zw-insight-server/      # 后端（22个Maven子模块）
├── zw-insight-web/         # PC前端
├── zw-insight-app/         # 移动端（uni-app）
├── docs/                   # 文档
├── docker-compose.yml      # 开发环境依赖
└── .kiro/specs/            # 需求/设计/任务规划
```

## 功能模块

- **系统管理**：多租户、机构、岗位、人员、角色、菜单、字典、日志
- **项目管理**：项目报备、项目成员、项目状态机
- **投标管理**：报名、任务分配、费用缴纳、保证金、开标、证件
- **合同管理**：施工合同、变更签证、其它收支合同
- **预算管理**：目标成本编制/变更、预算控制、二级科目
- **采购管理**：材料合同、结算、三方比价（询价/报价/定标）
- **劳务管理**：分包合同、班组、花名册、用工单、结算、薪资统计
- **材料库存**：入库、出库、退货、调拨、盘点、库存查询
- **机械管理**：合同、台账、进出场、台班、加油、故障维修
- **分包管理**：专业分包合同、产值上报、结算、奖惩
- **现场管理**：进度计划/反馈、施工日志、质量/安全检查、竣工验收
- **财务管理**：开票、回款、收票、付款、报销、备用金
- **行政人事**：入离职、转正、调转、用章、办公用品、车辆
- **档案管理**：项目/投标/合同/供应商/人员等15类档案聚合查看
- **数据看板**：经营分析、预算执行、应收/应付监控、投标/库存分析、甘特图
- **流程引擎**：Flowable BPMN 可视化设计、多种审批操作、回滚机制
- **消息通知**：公告、通知、站内信、WebSocket推送、快捷入口

## 快速启动

### 1. 启动依赖服务

```bash
docker-compose up -d
```

启动 MySQL(3306)、Redis(6379)、MinIO(9000/9001)、RabbitMQ(5672/15672)。

### 2. 初始化数据库

将 `zw-insight-server/zw-app/src/main/resources/schema.sql` 导入 MySQL 的 `zw_insight` 数据库。

### 3. 启动后端

```bash
cd zw-insight-server
mvn spring-boot:run -pl zw-app
```

后端运行在 http://localhost:8080，API文档：http://localhost:8080/doc.html

### 4. 启动PC前端

```bash
cd zw-insight-web
npm install
npm run dev
```

PC端运行在 http://localhost:3000

### 5. 启动移动端（H5模式）

```bash
cd zw-insight-app
npm install
npm run dev:h5
```

## 后端模块说明

| 模块 | 说明 |
|------|------|
| zw-common | 公共工具、基础类、异常处理、MyBatis-Plus配置 |
| zw-security | JWT认证、验证码、登录锁定、权限拦截 |
| zw-system | 机构、岗位、人员、角色、菜单、字典、日志 |
| zw-workflow | Flowable流程引擎、审批服务、回滚机制 |
| zw-message | 公告、通知、站内信、模板、快捷入口 |
| zw-file | 文件上传(MinIO)、编号规则、附件管理 |
| zw-project | 项目报备、项目成员、状态机 |
| zw-tender | 投标报名、任务、费用、保证金、证件 |
| zw-contract | 施工合同、变更签证、产值上报、结算 |
| zw-budget | 预算编制/变更、预算控制、科目管理 |
| zw-purchase | 采购合同、结算、三方比价 |
| zw-labor | 劳务合同、班组、花名册、用工、结算 |
| zw-material | 材料入库/出库/退货/调拨/盘点/库存 |
| zw-machine | 机械合同、台账、进出场、工作量、维修 |
| zw-subcontract | 专业分包合同、产值、结算 |
| zw-site | 进度计划、施工日志、质量/安全检查、验收 |
| zw-finance | 开票、回款、付款、报销、备用金、质保金、银行账户 |
| zw-hr | 入离职、办公用品、车辆管理 |
| zw-archive | 档案聚合查询（只读视图） |
| zw-dashboard | 数据看板、统计分析 |
| zw-basedata | 材料字典、供应商、甲方、检查方案、评价 |
| zw-app | Spring Boot 启动模块 |

## 开发规范

- 金额字段使用 `BigDecimal` / `DECIMAL(18,2)`
- 逻辑删除（`deleted` 字段）
- 乐观锁（`version` 字段）
- 多租户隔离（`tenant_id` 字段，MyBatis-Plus拦截器自动注入）
- 统一响应格式 `R<T>` (code/message/data/timestamp)
- RESTful 接口规范，路径前缀 `/api/v1/{module}`

## 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose

## License

Private - 中维智营内部使用
