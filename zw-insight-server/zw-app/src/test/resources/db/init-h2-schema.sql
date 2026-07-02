-- H2 Mapper 层测试初始化 SQL
-- 仅包含 H2 兼容的简单注解 SQL 所需表结构
-- H2 MODE=MySQL 下不支持 ON UPDATE CURRENT_TIMESTAMP / ENGINE=InnoDB / COMMENT

-- 预算表
CREATE TABLE IF NOT EXISTS biz_budget (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    budget_type VARCHAR(20) DEFAULT 'ORIGINAL',
    change_seq INT DEFAULT 0,
    total_amount DECIMAL(18,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    workflow_instance_id VARCHAR(64),
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 预算明细表
CREATE TABLE IF NOT EXISTS biz_budget_detail (
    id BIGINT PRIMARY KEY,
    budget_id BIGINT NOT NULL,
    cost_category VARCHAR(30),
    cost_subcategory VARCHAR(100),
    item_name VARCHAR(200),
    specification VARCHAR(100),
    unit VARCHAR(20),
    budget_quantity DECIMAL(18,4),
    budget_unit_price DECIMAL(18,4),
    budget_total_price DECIMAL(18,2),
    remark VARCHAR(500),
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 菜单表
CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY,
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(20) NOT NULL,
    parent_id BIGINT DEFAULT 0,
    path VARCHAR(200),
    component VARCHAR(200),
    icon VARCHAR(100),
    sort_order INT DEFAULT 0,
    permission VARCHAR(100),
    status INT DEFAULT 1,
    hidden INT DEFAULT 0,
    weight VARCHAR(20) DEFAULT 'NORMAL',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 角色菜单关联表
CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL
);

-- 采购合同表（BudgetOccupiedMapper / SettlementDataMapper 需要）
CREATE TABLE IF NOT EXISTS biz_purchase_contract (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    contract_code VARCHAR(50) NOT NULL,
    contract_amount DECIMAL(18,2),
    cumulative_settlement DECIMAL(18,2) DEFAULT 0,
    cumulative_paid DECIMAL(18,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 劳务合同表
CREATE TABLE IF NOT EXISTS biz_labor_contract (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    contract_code VARCHAR(50),
    contract_amount DECIMAL(18,2),
    cumulative_settlement DECIMAL(18,2) DEFAULT 0,
    cumulative_paid DECIMAL(18,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 机械合同表
CREATE TABLE IF NOT EXISTS biz_machine_contract (
    id BIGINT PRIMARY KEY,
    project_id BIGINT,
    contract_code VARCHAR(64),
    contract_amount DECIMAL(14,2) DEFAULT 0.00,
    cumulative_settlement DECIMAL(14,2) DEFAULT 0.00,
    status VARCHAR(32) DEFAULT 'EFFECTIVE',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 分包合同表
CREATE TABLE IF NOT EXISTS biz_subcontract (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    contract_code VARCHAR(50),
    contract_amount DECIMAL(18,2),
    cumulative_settlement DECIMAL(18,2) DEFAULT 0,
    cumulative_paid DECIMAL(18,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 付款申请表
CREATE TABLE IF NOT EXISTS biz_payment_apply (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    contract_category VARCHAR(50),
    payment_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 收款登记表
CREATE TABLE IF NOT EXISTS biz_payment_received (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    contract_id BIGINT,
    receive_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 开票申请表
CREATE TABLE IF NOT EXISTS biz_invoice_apply (
    id BIGINT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    contract_id BIGINT NOT NULL,
    invoice_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 系统用户表（HrStatisticsMapper 需要）
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(256),
    real_name VARCHAR(64),
    phone VARCHAR(32),
    org_id BIGINT,
    post_id BIGINT,
    status INT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 机构表（HrStatisticsMapper statByDept 需要）
CREATE TABLE IF NOT EXISTS sys_org (
    id BIGINT PRIMARY KEY,
    org_name VARCHAR(100) NOT NULL,
    org_code VARCHAR(50),
    org_type VARCHAR(20) DEFAULT 'DEPARTMENT',
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    status INT DEFAULT 1,
    ancestors VARCHAR(500) DEFAULT '0',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 岗位表（HrStatisticsMapper statByPost 需要）
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT PRIMARY KEY,
    post_name VARCHAR(100) NOT NULL,
    post_code VARCHAR(50),
    status INT DEFAULT 1,
    sort_order INT DEFAULT 0,
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 入职申请表（HrStatisticsMapper 趋势统计需要）
CREATE TABLE IF NOT EXISTS biz_entry_apply (
    id BIGINT PRIMARY KEY,
    entry_date DATE,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 离职申请表
CREATE TABLE IF NOT EXISTS biz_resign_apply (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    resign_date DATE,
    status VARCHAR(20) DEFAULT 'DRAFT',
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 项目成员表（BizProjectMemberMapper 需要）
CREATE TABLE IF NOT EXISTS biz_project_member (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    project_roles VARCHAR(1024),
    join_date DATE,
    status INT DEFAULT 1,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 机械工作量结算单表
CREATE TABLE IF NOT EXISTS biz_machine_work_settlement (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    project_id BIGINT NOT NULL,
    settlement_code VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount DECIMAL(14, 2) DEFAULT 0.00,
    status INT DEFAULT 0,
    workflow_instance_id VARCHAR(128),
    remark VARCHAR(512),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 检查方案表（InspectionSchemeQueryMapper 需要）
CREATE TABLE IF NOT EXISTS bd_inspection_scheme (
    id BIGINT PRIMARY KEY,
    scheme_name VARCHAR(128),
    scheme_type VARCHAR(32),
    content TEXT,
    status INT DEFAULT 1,
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 检查明细表（BizInspectionDetailMapper 需要）
CREATE TABLE IF NOT EXISTS biz_inspection_detail (
    id BIGINT PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    tenant_id BIGINT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);
