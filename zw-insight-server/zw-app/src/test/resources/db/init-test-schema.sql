-- 集成测试初始化 SQL（由 Testcontainers MySQL 容器启动时执行）
-- 包含 P1 系统完整度增强功能所需的核心表结构

-- 系统角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    role_name VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    data_scope VARCHAR(32) DEFAULT 'SELF',
    status INT DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(256),
    real_name VARCHAR(64),
    phone VARCHAR(32),
    dept_id BIGINT,
    status INT DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    tenant_id BIGINT
);

-- 用户项目关联表
CREATE TABLE IF NOT EXISTS sys_user_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    tenant_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_project (user_id, project_id)
);

-- 项目成员表
CREATE TABLE IF NOT EXISTS biz_project_member (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(64),
    project_roles JSON,
    join_date DATE,
    status INT DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0,
    UNIQUE KEY uk_project_user (project_id, user_id)
);

-- 系统配置表
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    config_key VARCHAR(128) NOT NULL,
    config_value VARCHAR(512),
    config_name VARCHAR(128),
    config_group VARCHAR(64),
    value_type VARCHAR(32) DEFAULT 'STRING',
    default_value VARCHAR(512),
    value_range VARCHAR(256),
    remark VARCHAR(512),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0,
    UNIQUE KEY uk_config_key (config_key, tenant_id)
);

-- 系统配置变更日志表
CREATE TABLE IF NOT EXISTS sys_config_change_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT,
    config_key VARCHAR(128) NOT NULL,
    old_value VARCHAR(512),
    new_value VARCHAR(512),
    operator_id BIGINT,
    operator_name VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
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
    status INT DEFAULT 0 COMMENT '0-草稿 1-审批中 2-已审批 3-已驳回',
    workflow_instance_id VARCHAR(128),
    remark VARCHAR(512),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0,
    UNIQUE KEY uk_settlement_code (settlement_code)
);

-- 机械结算明细表
CREATE TABLE IF NOT EXISTS biz_machine_work_settlement_detail (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    settlement_id BIGINT NOT NULL,
    ledger_id BIGINT NOT NULL,
    machine_name VARCHAR(128),
    spec_model VARCHAR(128),
    shift_count DECIMAL(10, 2) DEFAULT 0.00,
    work_volume DECIMAL(14, 4) DEFAULT 0.00,
    unit_price DECIMAL(10, 2) DEFAULT 0.00,
    subtotal DECIMAL(14, 2) DEFAULT 0.00,
    price_type VARCHAR(32) DEFAULT 'SHIFT' COMMENT 'SHIFT-台班计价 VOLUME-工作量计价',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 机械合同表（简化版，用于集成测试）
CREATE TABLE IF NOT EXISTS biz_machine_contract (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    project_id BIGINT,
    contract_code VARCHAR(64),
    machine_name VARCHAR(128),
    rental_type VARCHAR(32) DEFAULT 'SHIFT',
    contract_amount DECIMAL(14, 2) DEFAULT 0.00,
    settled_amount DECIMAL(14, 2) DEFAULT 0.00,
    cumulative_settlement DECIMAL(14, 2) DEFAULT 0.00,
    cumulative_paid DECIMAL(14, 2) DEFAULT 0.00,
    total_amount DECIMAL(14, 2) DEFAULT 0.00,
    status VARCHAR(32) DEFAULT 'EFFECTIVE',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 审批快照表
CREATE TABLE IF NOT EXISTS biz_approval_snapshot (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    workflow_instance_id VARCHAR(128) NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    biz_id BIGINT NOT NULL,
    field_name VARCHAR(64) NOT NULL,
    original_value TEXT,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 审批回滚日志表
CREATE TABLE IF NOT EXISTS biz_approval_rollback_log (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    workflow_instance_id VARCHAR(128) NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    biz_id BIGINT NOT NULL,
    rollback_fields TEXT,
    rollback_status INT DEFAULT 0 COMMENT '1-成功 2-失败 3-冲突待确认',
    retry_count INT DEFAULT 0,
    error_msg VARCHAR(1024),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 租户表
CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT PRIMARY KEY,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    address VARCHAR(256),
    user_type VARCHAR(32) DEFAULT 'STANDARD',
    start_date DATE,
    end_date DATE,
    expire_date DATE,
    max_users INT DEFAULT 50,
    modules JSON,
    secret_key VARCHAR(128),
    status INT DEFAULT 1 COMMENT '1-正常 2-已停用 3-已过期',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 租户菜单关联表
CREATE TABLE IF NOT EXISTS sys_tenant_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL
);

-- 部门表（简化版）
CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    dept_name VARCHAR(64),
    parent_id BIGINT DEFAULT 0,
    ancestors VARCHAR(512),
    status INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
);

-- 班组表（用于引用校验测试）
CREATE TABLE IF NOT EXISTS biz_labor_team (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    team_name VARCHAR(64),
    project_id BIGINT,
    status INT DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 劳务花名册（引用班组，用于引用校验测试）
CREATE TABLE IF NOT EXISTS biz_labor_roster (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    team_id BIGINT,
    worker_name VARCHAR(64),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);

-- 工资单表（简化版，用于薪资统计测试）
CREATE TABLE IF NOT EXISTS biz_labor_payroll (
    id BIGINT PRIMARY KEY,
    tenant_id BIGINT,
    project_id BIGINT,
    team_id BIGINT,
    month VARCHAR(10) COMMENT '格式：YYYY-MM',
    worker_name VARCHAR(64),
    id_card_suffix VARCHAR(4),
    attendance_days DECIMAL(5, 1) DEFAULT 0,
    overtime_hours DECIMAL(6, 1) DEFAULT 0,
    gross_salary DECIMAL(10, 2) DEFAULT 0.00,
    deduction DECIMAL(10, 2) DEFAULT 0.00,
    net_salary DECIMAL(10, 2) DEFAULT 0.00,
    labor_type VARCHAR(32) DEFAULT 'OWN' COMMENT 'OWN-自有劳务 TEMP-零星用工',
    approval_status INT DEFAULT 0 COMMENT '0-待审批 1-已审批',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0,
    version INT DEFAULT 0
);
