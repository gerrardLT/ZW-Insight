# 中维智营（ZW Insight）- 技术设计文档

## 1. 技术选型

| 层次 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 后端框架 | Spring Boot | 3.2+ | 单体应用，模块化分层 |
| 持久层 | MyBatis-Plus | 3.5+ | ORM + 代码生成 |
| 数据库 | MySQL | 8.0+ | 主数据库 |
| 缓存 | Redis | 7.0+ | 会话、Token、数据缓存 |
| 搜索 | Elasticsearch | 8.x | 日志检索、全文搜索（后期按需引入，初期不使用） |
| 流程引擎 | Flowable | 7.0+ | BPMN 2.0 流程设计与执行 |
| 文件存储 | MinIO | 最新 | 对象存储，兼容 S3 协议 |
| 消息队列 | RabbitMQ | 3.12+ | 异步消息、事件驱动 |
| 前端框架 | Vue 3 | 3.4+ | Composition API |
| UI组件库 | Element Plus | 2.5+ | PC端组件 |
| 移动端 | uni-app | 最新 | 跨平台移动端 (iOS/Android/H5) |
| 状态管理 | Pinia | 2.x | 前端状态管理 |
| 构建工具 | Vite | 5.x | 前端构建 |
| 图表 | ECharts | 5.x | 看板可视化 |
| 甘特图 | dhtmlx-gantt | 最新 | 进度甘特图 |
| 流程设计器 | bpmn.js | 最新 | 前端BPMN流程设计 |
| 报表打印 | JasperReports | 最新 | 单据打印模板 |
| API文档 | Knife4j | 最新 | Swagger增强 |

---

## 2. 系统架构

### 2.1 整体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                         客户端层                                   │
│                                                                  │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────┐             │
│  │ PC Web   │  │ 移动端(uni-app)│  │ 供应商报价门户 │             │
│  │ (Vue3)   │  │ iOS/Android/H5│  │ (Vue3 SPA)   │             │
│  └────┬─────┘  └──────┬───────┘  └──────┬────────┘             │
└───────┼────────────────┼─────────────────┼──────────────────────┘
        │                │                 │
        └────────────────┼─────────────────┘
                         │ HTTPS / WebSocket
┌────────────────────────┼────────────────────────────────────────┐
│                    Nginx 反向代理                                  │
└────────────────────────┼────────────────────────────────────────┘
                         │
┌────────────────────────┼────────────────────────────────────────┐
│              Spring Boot 单体应用                                  │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    API Gateway Layer                      │    │
│  │  认证过滤器 │ 租户解析 │ 权限校验 │ 日志记录 │ 限流       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   Controller Layer                        │    │
│  │  各业务模块 REST API                                      │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Service Layer                          │    │
│  │  业务逻辑 │ 事务管理 │ 数据回写 │ 预算控制 │ 状态机       │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Domain Layer                           │    │
│  │  实体模型 │ 值对象 │ 领域事件 │ 业务规则                   │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                 Infrastructure Layer                      │    │
│  │  MyBatis-Plus │ Redis │ MinIO │ RabbitMQ │ Flowable      │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
        │              │            │           │
   ┌────┴────┐   ┌────┴────┐  ┌───┴───┐  ┌───┴────┐
   │  MySQL  │   │  Redis  │  │ MinIO │  │RabbitMQ│
   │(主从复制)│   │(哨兵模式)│  │       │  │        │
   └─────────┘   └─────────┘  └───────┘  └────────┘
```

### 2.2 模块划分

```
zw-insight/
├── zw-common/           # 公共模块（工具类、常量、异常、基类）
├── zw-security/         # 安全模块（认证、授权、Token、租户）
├── zw-system/           # 系统管理（机构、岗位、人员、角色、菜单、字典、日志）
├── zw-workflow/         # 流程引擎（Flowable封装、审批操作、流程定义）
├── zw-message/          # 消息模块（通知、公告、推送、模板）
├── zw-file/             # 文件模块（上传、存储、预览、打印模板）
├── zw-project/          # 项目管理（项目报备、成员、状态机）
├── zw-tender/           # 投标管理（报名、任务、开标、保证金、证件）
├── zw-contract/         # 合同管理（施工合同、其它收支合同、变更签证）
├── zw-budget/           # 预算管理（目标成本编制/变更、预算控制）
├── zw-purchase/         # 采购管理（材料合同、结算、三方比价）
├── zw-labor/            # 劳务管理（分包合同、班组、花名册、用工、结算）
├── zw-material/         # 材料库存（入库、出库、退货、调拨、盘点）
├── zw-machine/          # 机械管理（合同、台账、进出场、台班、加油、维修）
├── zw-subcontract/      # 分包管理（专业分包合同、产值、结算）
├── zw-site/             # 现场管理（进度、日志、质量、安全、验收）
├── zw-finance/          # 财务管理（开票、回款、收票、付款、报销、备用金）
├── zw-hr/               # 行政人事（入离职、办公用品、车辆）
├── zw-archive/          # 档案管理（各类档案只读视图）
├── zw-dashboard/        # 数据看板（经营分析、预算执行、账款监控）
├── zw-basedata/         # 基础数据（材料字典、供应商、甲方、自持公司）
└── zw-app/              # 启动模块（Spring Boot Application）
```

---

## 3. 数据库设计

### 3.1 多租户方案

采用**共享数据库 + 租户字段隔离**方案：

- 每张业务表增加 `tenant_id` 字段
- MyBatis-Plus 通过 `TenantLineInterceptor` 自动注入租户条件
- 登录时从 Token 解析租户ID，贯穿整个请求链路

```sql
-- 租户基础表
CREATE TABLE sys_tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_code VARCHAR(32) NOT NULL COMMENT '组织码',
    tenant_name VARCHAR(100) NOT NULL COMMENT '租户名称',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系方式',
    address VARCHAR(200) COMMENT '地址',
    tenant_type_id BIGINT COMMENT '用户类型ID',
    status TINYINT DEFAULT 1 COMMENT '状态(1启用/0停用)',
    expire_date DATE COMMENT '到期日期',
    secret_key VARCHAR(64) COMMENT '密钥',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tenant_code (tenant_code)
);
```

### 3.2 核心实体模型

#### 项目相关

```sql
-- 项目信息表
CREATE TABLE biz_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_code VARCHAR(32) NOT NULL COMMENT '项目编号',
    project_name VARCHAR(200) NOT NULL COMMENT '项目名称',
    project_nature VARCHAR(50) COMMENT '项目性质',
    project_type VARCHAR(50) COMMENT '项目类型',
    owner_company_id BIGINT COMMENT '建设单位ID(甲方)',
    signing_company_id BIGINT COMMENT '签约公司ID(自持公司)',
    project_overview TEXT COMMENT '项目概况',
    project_address VARCHAR(300) COMMENT '项目地址',
    longitude DECIMAL(10,7) COMMENT '经度',
    latitude DECIMAL(10,7) COMMENT '纬度',
    contact_name VARCHAR(50) COMMENT '项目联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    need_tender TINYINT DEFAULT 1 COMMENT '是否需要投标(1是/0否)',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '项目状态(DRAFT/FILED/TENDERING/WON/CONSTRUCTION/COMPLETED/CLOSED)',
    budget_amount DECIMAL(18,2) DEFAULT 0 COMMENT '项目预算金额',
    contract_amount DECIMAL(18,2) DEFAULT 0 COMMENT '合同金额',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    settlement_amount DECIMAL(18,2) DEFAULT 0 COMMENT '最终结算金额',
    total_income DECIMAL(18,2) DEFAULT 0 COMMENT '总收款',
    total_expense DECIMAL(18,2) DEFAULT 0 COMMENT '总支出',
    total_other_payment DECIMAL(18,2) DEFAULT 0 COMMENT '其他总付款',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
);

-- 项目成员表
CREATE TABLE biz_project_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_type VARCHAR(50) NOT NULL COMMENT '项目角色(PROJECT_MANAGER/TECH_LEAD/SAFETY_OFFICER等)',
    join_date DATE,
    leave_date DATE,
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    INDEX idx_user (user_id)
);
```

#### 合同相关

```sql
-- 施工合同表
CREATE TABLE biz_construction_contract (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_code VARCHAR(50) NOT NULL COMMENT '合同编号',
    contract_type VARCHAR(20) NOT NULL COMMENT '合同类型(REGISTER/CHANGE/SUPPLEMENT)',
    parent_contract_id BIGINT COMMENT '原合同ID(变更/补充时)',
    party_a_name VARCHAR(200) COMMENT '甲方名称',
    party_a_id BIGINT COMMENT '甲方单位ID',
    signing_date DATE COMMENT '签订日期',
    start_date DATE COMMENT '开工日期',
    end_date DATE COMMENT '竣工日期',
    contract_amount DECIMAL(18,2) NOT NULL COMMENT '合同金额',
    tax_rate DECIMAL(5,2) COMMENT '税率(%)',
    amount_without_tax DECIMAL(18,2) COMMENT '不含税金额',
    tax_amount DECIMAL(18,2) COMMENT '税金',
    cumulative_change_amount DECIMAL(18,2) DEFAULT 0 COMMENT '累计变更金额',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计完成产值',
    cumulative_invoice_amount DECIMAL(18,2) DEFAULT 0 COMMENT '累计开票金额',
    cumulative_received_amount DECIMAL(18,2) DEFAULT 0 COMMENT '累计收款金额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/EFFECTIVE/CHANGING/SETTLED/CLOSED)',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id),
    INDEX idx_tenant (tenant_id)
);

-- 通用合同表(采购/劳务/机械/分包/其它收支)
CREATE TABLE biz_expense_contract (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_code VARCHAR(50) NOT NULL COMMENT '合同编号',
    contract_category VARCHAR(20) NOT NULL COMMENT '合同分类(MATERIAL/LABOR/MACHINE/SUBCONTRACT/OTHER_INCOME/OTHER_EXPENSE)',
    party_a_id BIGINT COMMENT '甲方ID',
    party_a_name VARCHAR(200) COMMENT '甲方名称',
    party_b_id BIGINT COMMENT '乙方ID(供应商)',
    party_b_name VARCHAR(200) COMMENT '乙方名称',
    signing_date DATE COMMENT '签订日期',
    budget_id BIGINT COMMENT '关联预算ID',
    contract_amount DECIMAL(18,2) NOT NULL COMMENT '合同金额',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    amount_without_tax DECIMAL(18,2) COMMENT '不含税金额',
    tax_amount DECIMAL(18,2) COMMENT '税金',
    payment_terms TEXT COMMENT '付款条件',
    cooperation_content TEXT COMMENT '合作内容',
    cumulative_settlement DECIMAL(18,2) DEFAULT 0 COMMENT '累计结算金额',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款金额',
    cumulative_invoice_received DECIMAL(18,2) DEFAULT 0 COMMENT '累计收票金额',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id),
    INDEX idx_category (contract_category),
    INDEX idx_tenant (tenant_id)
);

-- 合同明细表(通用)
CREATE TABLE biz_contract_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    contract_table VARCHAR(50) NOT NULL COMMENT '合同表名',
    item_name VARCHAR(200) NOT NULL COMMENT '名称/工程内容',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    quantity DECIMAL(18,4) COMMENT '数量/工程量',
    unit_price DECIMAL(18,4) COMMENT '单价',
    total_price DECIMAL(18,2) COMMENT '总价/合价',
    tax_rate DECIMAL(5,2) COMMENT '税率',
    remark VARCHAR(500) COMMENT '备注',
    sort_order INT DEFAULT 0,
    INDEX idx_contract (contract_id, contract_table)
);
```

#### 预算相关

```sql
-- 目标成本编制表
CREATE TABLE biz_budget (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    budget_type VARCHAR(20) NOT NULL COMMENT '类型(ORIGINAL/CHANGE)',
    change_seq INT DEFAULT 0 COMMENT '变更序号(编制为0)',
    total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '预算总额',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id)
);

-- 预算明细表
CREATE TABLE biz_budget_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    budget_id BIGINT NOT NULL,
    cost_category VARCHAR(50) NOT NULL COMMENT '成本大类(MATERIAL/LABOR/MACHINE/SUBCONTRACT/INDIRECT/OTHER)',
    cost_subcategory VARCHAR(50) COMMENT '二级科目',
    item_name VARCHAR(200) NOT NULL COMMENT '名称',
    specification VARCHAR(100) COMMENT '规格',
    unit VARCHAR(20) COMMENT '单位',
    budget_quantity DECIMAL(18,4) COMMENT '预算量',
    budget_unit_price DECIMAL(18,4) COMMENT '预算单价',
    budget_total_price DECIMAL(18,2) COMMENT '预算合价',
    adjusted_quantity DECIMAL(18,4) COMMENT '调整后量(变更时)',
    adjusted_unit_price DECIMAL(18,4) COMMENT '调整后单价(变更时)',
    adjusted_total_price DECIMAL(18,2) COMMENT '调整后合价(变更时)',
    remark VARCHAR(500),
    INDEX idx_budget (budget_id),
    INDEX idx_category (cost_category)
);
```

---

#### 财务相关

```sql
-- 产值上报表
CREATE TABLE biz_output_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL COMMENT '施工合同ID',
    report_period VARCHAR(20) COMMENT '上报期次',
    current_output DECIMAL(18,2) NOT NULL COMMENT '本期甲方计量产值',
    cumulative_output DECIMAL(18,2) COMMENT '累计产值',
    confirm_date DATE COMMENT '产值确认日期',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id),
    INDEX idx_contract (contract_id)
);

-- 开票申请表
CREATE TABLE biz_invoice_apply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL COMMENT '施工合同ID',
    invoice_type VARCHAR(20) COMMENT '发票类型(SPECIAL/NORMAL)',
    invoice_amount DECIMAL(18,2) NOT NULL COMMENT '本次开票金额',
    invoice_title VARCHAR(200) COMMENT '发票抬头',
    taxpayer_id VARCHAR(50) COMMENT '纳税人识别号',
    bank_account VARCHAR(50) COMMENT '银行账号',
    bank_name VARCHAR(100) COMMENT '开户银行',
    contract_amount DECIMAL(18,2) COMMENT '合同金额(快照)',
    settlement_amount DECIMAL(18,2) COMMENT '当前结算金额(快照)',
    historical_invoiced DECIMAL(18,2) COMMENT '历史已开票金额(快照)',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id)
);

-- 回款登记表
CREATE TABLE biz_payment_received (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL COMMENT '施工合同ID',
    receive_date DATE NOT NULL COMMENT '收款日期',
    receive_amount DECIMAL(18,2) NOT NULL COMMENT '本次收款金额',
    receiver VARCHAR(50) COMMENT '收款人',
    receive_type VARCHAR(20) COMMENT '收款类型',
    bank_account VARCHAR(50) COMMENT '收款账号',
    settlement_amount DECIMAL(18,2) COMMENT '当前结算金额(快照)',
    unreceived_amount DECIMAL(18,2) COMMENT '未收金额(快照)',
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id)
);

-- 付款申请表
CREATE TABLE biz_payment_apply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT COMMENT '请款合同ID',
    contract_category VARCHAR(20) COMMENT '合同类型',
    supplier_id BIGINT COMMENT '供应商ID',
    supplier_name VARCHAR(200) COMMENT '供应商名称',
    payment_amount DECIMAL(18,2) NOT NULL COMMENT '本次付款金额',
    payment_date DATE COMMENT '付款日期',
    cumulative_settlement DECIMAL(18,2) COMMENT '累计结算金额(快照)',
    unpaid_amount DECIMAL(18,2) COMMENT '未付金额(快照)',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id)
);

-- 项目报销表
CREATE TABLE biz_project_reimbursement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL COMMENT '总金额',
    offset_reserve TINYINT DEFAULT 0 COMMENT '是否冲抵备用金(1是/0否)',
    reserve_apply_id BIGINT COMMENT '冲抵的备用金申请ID',
    offset_amount DECIMAL(18,2) COMMENT '冲抵金额',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id)
);

-- 报销明细表
CREATE TABLE biz_reimbursement_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reimbursement_id BIGINT NOT NULL,
    expense_type VARCHAR(50) NOT NULL COMMENT '费用类型',
    amount DECIMAL(18,2) NOT NULL COMMENT '金额',
    remark VARCHAR(500),
    INDEX idx_reimbursement (reimbursement_id)
);

-- 备用金申请表
CREATE TABLE biz_reserve_fund_apply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    applicant VARCHAR(50) COMMENT '申请人',
    apply_date DATE COMMENT '申请日期',
    apply_amount DECIMAL(18,2) NOT NULL COMMENT '备用金额',
    returned_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已归还金额',
    offset_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已冲抵金额',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id)
);
```

#### 材料库存相关

```sql
-- 材料入库表
CREATE TABLE biz_material_inbound (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL COMMENT '采购合同ID',
    inbound_code VARCHAR(50) COMMENT '入库单号',
    inbound_date DATE COMMENT '入库日期',
    total_amount DECIMAL(18,2) COMMENT '本期入库总金额',
    direct_outbound TINYINT DEFAULT 0 COMMENT '是否直接出库(1是/0否)',
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id)
);

-- 材料入库明细
CREATE TABLE biz_material_inbound_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    inbound_id BIGINT NOT NULL,
    material_id BIGINT COMMENT '材料字典ID',
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(100),
    unit VARCHAR(20),
    unit_price DECIMAL(18,4),
    quantity DECIMAL(18,4) NOT NULL COMMENT '入库数量',
    total_price DECIMAL(18,2),
    INDEX idx_inbound (inbound_id)
);

-- 项目材料库存表
CREATE TABLE biz_project_material_stock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL COMMENT '材料字典ID',
    material_name VARCHAR(200) NOT NULL,
    specification VARCHAR(100),
    unit VARCHAR(20),
    stock_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '当前库存数量',
    avg_unit_price DECIMAL(18,4) COMMENT '加权平均单价',
    total_inbound DECIMAL(18,4) DEFAULT 0 COMMENT '累计入库',
    total_outbound DECIMAL(18,4) DEFAULT 0 COMMENT '累计出库',
    total_return DECIMAL(18,4) DEFAULT 0 COMMENT '累计退货',
    total_transfer_in DECIMAL(18,4) DEFAULT 0 COMMENT '累计调入',
    total_transfer_out DECIMAL(18,4) DEFAULT 0 COMMENT '累计调出',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_material (project_id, material_id),
    INDEX idx_project (project_id)
);
```

#### 现场管理相关

```sql
-- 进度计划表
CREATE TABLE biz_schedule_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    task_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    parent_id BIGINT DEFAULT 0 COMMENT '上级任务ID',
    plan_start_date DATE COMMENT '计划开工日期',
    plan_end_date DATE COMMENT '计划完工日期',
    actual_start_date DATE COMMENT '实际开始日期',
    actual_end_date DATE COMMENT '实际结束日期',
    progress DECIMAL(5,2) DEFAULT 0 COMMENT '进度比例(%)',
    task_status VARCHAR(20) DEFAULT 'NOT_STARTED' COMMENT '任务状态',
    task_detail TEXT COMMENT '任务详情',
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_project (project_id),
    INDEX idx_parent (parent_id)
);

-- 施工日志表
CREATE TABLE biz_construction_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    log_date DATE NOT NULL COMMENT '日志日期',
    weather VARCHAR(20) COMMENT '天气',
    temperature VARCHAR(20) COMMENT '温度',
    wind VARCHAR(20) COMMENT '风力',
    worker_count INT COMMENT '施工人数',
    production_record TEXT COMMENT '生产情况记录',
    technical_record TEXT COMMENT '技术质量安全工作记录',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project_date (project_id, log_date)
);

-- 质量/安全检查表
CREATE TABLE biz_inspection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    inspection_type VARCHAR(20) NOT NULL COMMENT '检查类型(QUALITY/SAFETY)',
    scheme_id BIGINT COMMENT '检查方案ID',
    inspection_content TEXT COMMENT '检查情况',
    has_problem TINYINT DEFAULT 0 COMMENT '是否有问题(1是/0否)',
    problem_description TEXT COMMENT '问题描述',
    responsible_person_id BIGINT COMMENT '整改责任人ID',
    rectification_deadline DATE COMMENT '整改截止日期',
    rectification_date DATE COMMENT '实际整改日期',
    rectification_status VARCHAR(20) COMMENT '整改状态(PENDING/SUBMITTED/APPROVED/REJECTED)',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_project (project_id),
    INDEX idx_type (inspection_type)
);
```

---

## 4. API 设计规范

### 4.1 RESTful 接口规范

```
基础路径: /api/v1/{module}

GET    /api/v1/projects              # 列表查询(分页)
GET    /api/v1/projects/{id}         # 详情
POST   /api/v1/projects              # 新增
PUT    /api/v1/projects/{id}         # 编辑
DELETE /api/v1/projects/{id}         # 删除
DELETE /api/v1/projects/batch        # 批量删除
POST   /api/v1/projects/{id}/submit  # 提交(发起审批)
POST   /api/v1/projects/export       # 导出
POST   /api/v1/projects/import       # 导入
```

### 4.2 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1719000000000
}
```

### 4.3 分页查询

```json
// Request
GET /api/v1/projects?page=1&size=20&projectName=xxx&status=CONSTRUCTION

// Response
{
  "code": 200,
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "size": 20,
    "pages": 5
  }
}
```

### 4.4 认证方式

```
Header: Authorization: Bearer {token}
Header: X-Tenant-Id: {tenantId}  // 由网关自动注入
```

---

## 5. 核心设计模式

### 5.1 数据回写机制

各业务操作完成后需要自动更新关联数据。采用**领域事件 + 异步处理**模式：

```java
// 事件定义
public class OutputReportApprovedEvent {
    private Long projectId;
    private Long contractId;
    private BigDecimal currentOutput;
}

// 产值上报审批通过后发布事件
@Service
public class OutputReportService {
    @Transactional
    public void onApproved(Long reportId) {
        OutputReport report = getById(reportId);
        // 发布领域事件
        eventPublisher.publish(new OutputReportApprovedEvent(
            report.getProjectId(),
            report.getContractId(),
            report.getCurrentOutput()
        ));
    }
}

// 事件监听器处理数据回写
@Component
public class OutputReportEventListener {
    @EventListener
    @Transactional
    public void handle(OutputReportApprovedEvent event) {
        // 更新施工合同累计产值
        contractService.addCumulativeOutput(event.getContractId(), event.getCurrentOutput());
        // 更新项目信息累计产值
        projectService.addCumulativeOutput(event.getProjectId(), event.getCurrentOutput());
    }
}
```

### 5.2 预算控制拦截器

```java
@Aspect
@Component
public class BudgetControlAspect {
    
    @Before("@annotation(budgetCheck)")
    public void checkBudget(JoinPoint joinPoint, BudgetCheck budgetCheck) {
        // 从方法参数中提取项目ID、成本类型、金额
        Long projectId = extractProjectId(joinPoint);
        String costCategory = budgetCheck.category();
        BigDecimal amount = extractAmount(joinPoint);
        
        // 查询预算余额
        BigDecimal remaining = budgetService.getRemainingBudget(projectId, costCategory);
        
        // 查询该项目的预算控制配置
        BudgetControlConfig config = configService.getConfig(projectId);
        
        if (amount.compareTo(remaining) > 0) {
            if (config.getControlMode() == ControlMode.FORBID) {
                throw new BudgetExceededException("预算不足，禁止提交");
            } else {
                // 记录预警，允许继续
                budgetWarningService.record(projectId, costCategory, amount, remaining);
            }
        }
    }
}
```

### 5.3 审批回滚机制

```java
// 审批回滚注册表
@Service
public class ApprovalRollbackService {
    
    // 注册回写操作（审批通过时调用）
    public void registerWriteBack(String processInstanceId, RollbackAction action) {
        // action 包含：目标表、目标字段、目标ID、操作类型(ADD/SET)、操作值
        rollbackActionMapper.insert(processInstanceId, action);
    }
    
    // 执行回滚（审批退回/终止时调用）
    @Transactional
    public void rollback(String processInstanceId) {
        List<RollbackAction> actions = rollbackActionMapper.findByProcessId(processInstanceId);
        // 逆序执行
        Collections.reverse(actions);
        for (RollbackAction action : actions) {
            executeRollback(action);
        }
        // 记录回滚日志
        auditLogService.logRollback(processInstanceId, actions);
    }
}
```

### 5.4 自动编号服务

```java
@Service
public class SerialNumberService {
    
    @Transactional
    public String generate(String businessType, Long tenantId) {
        // 获取编号规则配置
        NumberRule rule = ruleMapper.findByType(businessType, tenantId);
        // 例: HT-{yyyy}{MM}-{####}
        
        String prefix = rule.getPrefix(); // HT-
        String datePart = formatDate(rule.getDateFormat()); // 202606
        
        // Redis原子递增获取流水号
        String key = "serial:" + tenantId + ":" + businessType + ":" + datePart;
        Long seq = redisTemplate.opsForValue().increment(key);
        
        // 设置过期（月底过期用于按月重置）
        if (seq == 1L) {
            redisTemplate.expire(key, getMonthRemainingSeconds(), TimeUnit.SECONDS);
        }
        
        String seqStr = String.format("%0" + rule.getSeqLength() + "d", seq);
        return prefix + datePart + "-" + seqStr;
    }
}
```

---

## 6. 前端架构设计

### 6.1 项目结构

```
zw-insight-web/
├── public/
├── src/
│   ├── api/                    # API 接口层
│   │   ├── project.ts
│   │   ├── contract.ts
│   │   ├── finance.ts
│   │   └── ...
│   ├── assets/                 # 静态资源
│   ├── components/             # 公共组件
│   │   ├── BizForm/           # 通用业务表单
│   │   ├── BizTable/          # 通用业务列表
│   │   ├── ApprovalPanel/     # 审批面板
│   │   ├── FileUpload/        # 文件上传
│   │   ├── FlowViewer/        # 流程图查看
│   │   ├── GanttChart/        # 甘特图
│   │   └── DashboardCard/     # 看板卡片
│   ├── composables/            # 组合式函数
│   │   ├── useApproval.ts     # 审批通用逻辑
│   │   ├── useBudgetCheck.ts  # 预算校验
│   │   ├── useDict.ts         # 字典数据
│   │   └── usePermission.ts   # 权限判断
│   ├── layouts/                # 布局
│   │   ├── DefaultLayout.vue
│   │   └── BlankLayout.vue
│   ├── router/                 # 路由（动态菜单）
│   ├── stores/                 # Pinia状态管理
│   │   ├── user.ts
│   │   ├── permission.ts
│   │   └── tenant.ts
│   ├── views/                  # 页面视图
│   │   ├── project/           # 项目管理
│   │   ├── tender/            # 投标管理
│   │   ├── contract/          # 合同管理
│   │   ├── budget/            # 预算管理
│   │   ├── purchase/          # 采购管理
│   │   ├── labor/             # 劳务管理
│   │   ├── material/          # 材料库存
│   │   ├── machine/           # 机械管理
│   │   ├── subcontract/       # 分包管理
│   │   ├── site/              # 现场管理
│   │   ├── finance/           # 财务管理
│   │   ├── hr/                # 行政人事
│   │   ├── archive/           # 档案管理
│   │   ├── dashboard/         # 数据看板
│   │   ├── system/            # 系统管理
│   │   └── workflow/          # 流程管理
│   ├── utils/                  # 工具函数
│   └── App.vue
├── package.json
└── vite.config.ts
```

### 6.2 动态路由与权限

```typescript
// 登录后获取菜单权限，动态注册路由
const permissionStore = usePermissionStore()

async function initRoutes() {
  const menus = await api.getMenus()
  const routes = generateRoutes(menus)
  routes.forEach(route => router.addRoute(route))
  permissionStore.setMenus(menus)
  permissionStore.setButtons(extractButtons(menus))
}

// 按钮级权限指令
// v-permission="'project:create'"
app.directive('permission', {
  mounted(el, binding) {
    const { hasPermission } = usePermission()
    if (!hasPermission(binding.value)) {
      el.parentNode?.removeChild(el)
    }
  }
})
```

### 6.3 通用业务表单模式

```vue
<!-- 通用业务表单页面模式 -->
<template>
  <BizForm
    :schema="formSchema"
    :rules="formRules"
    :data="formData"
    :mode="mode"
    :loading="loading"
    @save="handleSave"
    @submit="handleSubmit"
  >
    <!-- 合同选择联动 -->
    <template #contractSelect>
      <ContractSelector 
        :project-id="formData.projectId"
        :category="contractCategory"
        @change="onContractChange"
      />
    </template>
    
    <!-- 明细清单 -->
    <template #detailTable>
      <EditableTable 
        :columns="detailColumns"
        :data="formData.details"
        :editable="mode !== 'view'"
      />
    </template>
    
    <!-- 审批面板(详情页) -->
    <template #approval v-if="mode === 'view'">
      <ApprovalPanel 
        :process-instance-id="formData.workflowInstanceId"
        @approved="onApproved"
        @rejected="onRejected"
      />
    </template>
  </BizForm>
</template>
```

---

## 7. 流程引擎集成

### 7.1 Flowable 集成方案

```java
@Configuration
public class FlowableConfig {
    
    @Bean
    public ProcessEngineConfiguration processEngineConfiguration(DataSource dataSource) {
        SpringProcessEngineConfiguration config = new SpringProcessEngineConfiguration();
        config.setDataSource(dataSource);
        config.setDatabaseSchemaUpdate("true");
        config.setAsyncExecutorActivate(true);
        // 多租户支持
        config.setTenantIdInterceptor(new MultiTenantInterceptor());
        return config;
    }
}

// 通用审批服务
@Service
public class ApprovalService {
    
    // 发起流程
    public String startProcess(String businessType, Long businessId, Long tenantId, Map<String, Object> variables) {
        // 查找该租户该业务类型的流程定义
        ProcessDefinition definition = findDefinition(businessType, tenantId);
        variables.put("businessId", businessId);
        variables.put("tenantId", tenantId);
        
        ProcessInstance instance = runtimeService.startProcessInstanceById(
            definition.getId(), 
            businessType + ":" + businessId, 
            variables
        );
        return instance.getId();
    }
    
    // 办理(通过)
    public void complete(String taskId, String comment, Map<String, Object> variables) { ... }
    
    // 退回至上一节点
    public void rejectToPrevious(String taskId, String comment) { ... }
    
    // 退回至发起人
    public void rejectToInitiator(String taskId, String comment) { ... }
    
    // 任意退回
    public void rejectToNode(String taskId, String targetNodeId, String comment) { ... }
    
    // 终止
    public void terminate(String taskId, String comment) { ... }
    
    // 转办
    public void transfer(String taskId, String targetUserId, String comment) { ... }
    
    // 委托
    public void delegate(String taskId, String delegateUserId, String comment) { ... }
}
```

### 7.2 审批回调机制

```java
// 流程监听器：审批通过后执行业务回写
public class ApprovalCompletedListener implements ExecutionListener {
    
    @Override
    public void notify(DelegateExecution execution) {
        String businessType = (String) execution.getVariable("businessType");
        Long businessId = (Long) execution.getVariable("businessId");
        
        // 通过策略模式找到对应的业务回调
        ApprovalCallback callback = callbackRegistry.getCallback(businessType);
        callback.onApproved(businessId, execution.getProcessInstanceId());
    }
}

// 各业务模块实现回调接口
@Component("payment_apply_callback")
public class PaymentApplyCallback implements ApprovalCallback {
    
    @Override
    @Transactional
    public void onApproved(Long businessId, String processInstanceId) {
        PaymentApply apply = paymentApplyService.getById(businessId);
        apply.setStatus("APPROVED");
        paymentApplyService.updateById(apply);
        
        // 回写项目支出
        projectService.addExpense(apply.getProjectId(), apply.getPaymentAmount());
        
        // 回写合同已付金额
        contractService.addPaidAmount(apply.getContractId(), apply.getPaymentAmount());
        
        // 注册回滚操作
        rollbackService.registerWriteBack(processInstanceId, ...);
    }
}
```

---

## 8. 安全设计

### 8.1 认证流程

```
1. 用户提交 组织码+账号+密码+验证码
2. 验证验证码(Redis)
3. 根据组织码查找租户 → 验证租户状态和有效期
4. 验证账号密码(BCrypt)
5. 检查登录失败锁定
6. 生成 JWT Token(含 userId, tenantId, roles)
7. Token 存入 Redis(支持主动失效)
8. 返回 Token + 用户信息 + 菜单权限
```

### 8.2 接口权限校验

```java
@Component
public class PermissionInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        // 1. 从Token解析用户和租户信息
        TokenInfo tokenInfo = tokenService.parse(request.getHeader("Authorization"));
        
        // 2. 设置上下文
        SecurityContext.setCurrentUser(tokenInfo.getUserId());
        SecurityContext.setCurrentTenant(tokenInfo.getTenantId());
        
        // 3. 检查接口权限
        String permission = getRequiredPermission(request);
        if (permission != null && !hasPermission(tokenInfo.getRoles(), permission)) {
            throw new ForbiddenException("无权限访问");
        }
        
        return true;
    }
}
```

### 8.3 数据权限 SQL 注入

```java
// MyBatis-Plus 数据权限拦截器
@Component
public class DataPermissionInterceptor implements InnerInterceptor {
    
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, ...) {
        // 获取当前用户的数据权限配置
        DataPermissionRule rule = getRule(ms.getId());
        if (rule == null) return;
        
        String condition = buildCondition(rule);
        // 追加 SQL 条件
        // 例: AND project_id IN (SELECT project_id FROM biz_project_member WHERE user_id = #{currentUserId})
    }
}
```

---

## 9. 部署架构

### 9.1 开发环境

```yaml
# docker-compose.yml
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    volumes: ["./data/mysql:/var/lib/mysql"]
    
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    
  minio:
    image: minio/minio
    ports: ["9000:9000", "9001:9001"]
    command: server /data --console-address ":9001"
    
  rabbitmq:
    image: rabbitmq:3-management
    ports: ["5672:5672", "15672:15672"]
```

### 9.2 生产环境

```
┌─────────────────────────────────────────────┐
│              负载均衡(Nginx)                   │
│         SSL证书 + 静态资源 + 反向代理          │
└──────────────────┬──────────────────────────┘
                   │
        ┌──────────┼──────────┐
        │          │          │
   ┌────┴────┐ ┌──┴────┐ ┌──┴────┐
   │ App-1   │ │ App-2 │ │ App-3 │  (Spring Boot实例)
   └────┬────┘ └──┬────┘ └──┬────┘
        │         │         │
        └─────────┼─────────┘
                  │
   ┌──────────────┼──────────────┐
   │              │              │
┌──┴───┐   ┌─────┴────┐   ┌────┴───┐
│MySQL │   │Redis集群   │   │ MinIO  │
│主从   │   │(哨兵模式)  │   │        │
└──────┘   └──────────┘   └────────┘
```

---

## 10. 开发规范

### 10.1 后端代码规范

- 包命名：`com.zwinsight.{module}.{layer}`
- Controller 层只做参数校验和转发，不含业务逻辑
- Service 层处理业务逻辑，使用接口+实现类模式
- 所有金额使用 `BigDecimal`，禁止使用 `float/double`
- 数据库字段使用下划线命名，Java 属性使用驼峰
- 所有列表查询必须分页
- 删除操作使用逻辑删除（`deleted` 字段）
- 敏感操作记录审计日志

### 10.2 前端代码规范

- 组件命名：PascalCase（`ProjectForm.vue`）
- Composition API 优先
- 接口调用统一走 `api/` 层，不在组件中直接调用 axios
- 表单验证使用 Element Plus 的 `FormRules`
- 列表页统一使用 `BizTable` 组件封装
- 详情页统一使用 `BizForm` 组件封装

### 10.3 数据库规范

- 主键使用 `BIGINT` 自增
- 每张表必须有 `created_at`、`updated_at` 字段
- 业务表必须有 `tenant_id`、`deleted`、`created_by` 字段
- 金额字段使用 `DECIMAL(18,2)`
- 数量字段使用 `DECIMAL(18,4)`
- 索引命名：`idx_{表名简写}_{字段}`
- 唯一索引命名：`uk_{表名简写}_{字段}`

---
