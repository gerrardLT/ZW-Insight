-- ============================================
-- P0 核心功能 - 数据库迁移脚本
-- 新增表：biz_boq_item, biz_budget_change, biz_budget_change_detail,
--         biz_project_settlement, biz_settlement_contract_detail,
--         sys_budget_control_config, biz_retention_warning_log, biz_inspection_detail
-- 修改表：biz_inspection (增加 scheme_snapshot 字段)
-- ============================================

-- ============ 工程量清单条目表 ============

CREATE TABLE IF NOT EXISTS biz_boq_item (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    contract_id BIGINT NOT NULL COMMENT '施工合同ID',
    parent_id BIGINT DEFAULT 0 COMMENT '父级条目ID（0为顶层）',
    item_code VARCHAR(50) NOT NULL COMMENT '项目编码（如1.1.2）',
    item_name VARCHAR(300) NOT NULL COMMENT '项目名称',
    unit VARCHAR(20) COMMENT '计量单位',
    quantity DECIMAL(18,4) DEFAULT 0 COMMENT '工程数量',
    unit_price DECIMAL(18,4) DEFAULT 0 COMMENT '综合单价',
    total_price DECIMAL(18,2) DEFAULT 0 COMMENT '合价',
    completed_quantity DECIMAL(18,4) DEFAULT 0 COMMENT '已完成工程量',
    level TINYINT DEFAULT 1 COMMENT '层级（1-4）',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_contract_id (contract_id),
    KEY idx_parent_id (parent_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工程量清单条目表';

-- ============ 目标成本变更主表 ============

CREATE TABLE IF NOT EXISTS biz_budget_change (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    budget_id BIGINT NOT NULL COMMENT '原预算编制ID',
    change_code VARCHAR(50) COMMENT '变更单编号',
    change_reason TEXT NOT NULL COMMENT '变更原因',
    total_adjust_amount DECIMAL(18,2) DEFAULT 0 COMMENT '调整总额',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/SUBMITTED/APPROVED/REJECTED/WITHDRAWN）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_budget_id (budget_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标成本变更主表';

-- ============ 目标成本变更明细表 ============

CREATE TABLE IF NOT EXISTS biz_budget_change_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    change_id BIGINT NOT NULL COMMENT '变更单ID',
    budget_detail_id BIGINT NOT NULL COMMENT '原预算明细ID',
    cost_category VARCHAR(50) NOT NULL COMMENT '成本大类',
    cost_subcategory VARCHAR(50) COMMENT '二级科目',
    item_name VARCHAR(200) NOT NULL COMMENT '科目名称',
    original_amount DECIMAL(18,2) NOT NULL COMMENT '原金额',
    adjust_amount DECIMAL(18,2) NOT NULL COMMENT '调整金额（正追加/负调减）',
    adjusted_amount DECIMAL(18,2) NOT NULL COMMENT '调整后金额',
    remark VARCHAR(500) COMMENT '备注',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_change_id (change_id),
    KEY idx_budget_detail_id (budget_detail_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='目标成本变更明细表';

-- ============ 项目最终结算主表 ============

CREATE TABLE IF NOT EXISTS biz_project_settlement (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    settlement_code VARCHAR(50) COMMENT '结算单编号',
    -- 收入汇总
    construction_contract_amount DECIMAL(18,2) DEFAULT 0 COMMENT '施工合同总额',
    cumulative_output DECIMAL(18,2) DEFAULT 0 COMMENT '累计产值',
    cumulative_received DECIMAL(18,2) DEFAULT 0 COMMENT '累计收款',
    cumulative_invoiced DECIMAL(18,2) DEFAULT 0 COMMENT '累计开票',
    total_income DECIMAL(18,2) DEFAULT 0 COMMENT '总收入',
    -- 支出汇总
    subcontract_settled DECIMAL(18,2) DEFAULT 0 COMMENT '分包结算总额',
    labor_settled DECIMAL(18,2) DEFAULT 0 COMMENT '劳务结算总额',
    material_settled DECIMAL(18,2) DEFAULT 0 COMMENT '材料结算总额',
    machine_settled DECIMAL(18,2) DEFAULT 0 COMMENT '机械结算总额',
    other_expense DECIMAL(18,2) DEFAULT 0 COMMENT '其他支出',
    cumulative_paid DECIMAL(18,2) DEFAULT 0 COMMENT '累计付款',
    total_expenditure DECIMAL(18,2) DEFAULT 0 COMMENT '总支出',
    -- 利润
    profit DECIMAL(18,2) DEFAULT 0 COMMENT '最终利润',
    profit_rate DECIMAL(5,2) DEFAULT 0 COMMENT '利润率（%）',
    -- 状态
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/SUBMITTED/APPROVED/REJECTED）',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目最终结算主表';

-- ============ 结算关联合同明细表 ============

CREATE TABLE IF NOT EXISTS biz_settlement_contract_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    settlement_id BIGINT NOT NULL COMMENT '结算单ID',
    contract_type VARCHAR(20) NOT NULL COMMENT '合同类型（SUBCONTRACT/LABOR/MATERIAL/MACHINE/OTHER）',
    contract_id BIGINT NOT NULL COMMENT '合同ID',
    contract_code VARCHAR(50) COMMENT '合同编号',
    contract_name VARCHAR(200) COMMENT '合同名称',
    contract_amount DECIMAL(18,2) DEFAULT 0 COMMENT '合同金额',
    settled_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已结算金额',
    paid_amount DECIMAL(18,2) DEFAULT 0 COMMENT '已付款金额',
    unsettled_amount DECIMAL(18,2) DEFAULT 0 COMMENT '未结金额',
    settlement_status VARCHAR(20) COMMENT '合同结算状态',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_settlement_id (settlement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='结算关联合同明细表';

-- ============ 预算控制配置表 ============

CREATE TABLE IF NOT EXISTS sys_budget_control_config (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    project_id BIGINT COMMENT '项目ID（NULL表示系统默认规则）',
    control_mode VARCHAR(20) NOT NULL COMMENT '控制模式（WARN_ONLY/BLOCK/EXEMPT）',
    warning_threshold INT DEFAULT 80 COMMENT '预警阈值（50-99，百分比整数）',
    is_default TINYINT DEFAULT 0 COMMENT '是否系统默认（1-是 0-否）',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_project (tenant_id, project_id),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='预算控制配置表';

-- ============ 质保金预警通知日志表 ============

CREATE TABLE IF NOT EXISTS biz_retention_warning_log (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    retention_id BIGINT NOT NULL COMMENT '质保金记录ID',
    warning_level VARCHAR(20) NOT NULL COMMENT '预警级别（UPCOMING/URGENT/OVERDUE）',
    notify_status VARCHAR(20) DEFAULT 'PENDING' COMMENT '通知状态（PENDING/SENT/FAILED/PERMANENTLY_FAILED）',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    sent_at DATETIME COMMENT '发送时间',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_retention_id (retention_id),
    KEY idx_warning_level (warning_level),
    KEY idx_notify_status (notify_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='质保金预警通知日志表';

-- ============ 检查明细表 ============

CREATE TABLE IF NOT EXISTS biz_inspection_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT COMMENT '租户ID',
    inspection_id BIGINT NOT NULL COMMENT '检查记录ID',
    item_name VARCHAR(200) NOT NULL COMMENT '检查项目名称',
    check_standard VARCHAR(500) COMMENT '检查标准',
    check_method VARCHAR(300) COMMENT '检查方法',
    check_result VARCHAR(20) COMMENT '检查结果（PASS/FAIL/NOT_CHECKED）',
    remark VARCHAR(500) COMMENT '备注',
    sort_order INT DEFAULT 0 COMMENT '排序号',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted INT DEFAULT 0 COMMENT '逻辑删除（0-未删除 1-已删除）',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_inspection_id (inspection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='检查明细表';

-- ============ 修改 biz_inspection 表 ============

ALTER TABLE biz_inspection
    ADD COLUMN scheme_snapshot JSON COMMENT '检查方案快照（JSON格式）' AFTER scheme_id;

-- ============ 插入预算控制配置系统默认记录 ============

INSERT INTO sys_budget_control_config (id, tenant_id, project_id, control_mode, warning_threshold, is_default, created_by, created_at, updated_at, deleted, version)
VALUES (1, 0, NULL, 'BLOCK', 80, 1, NULL, NOW(), NOW(), 0, 0);
