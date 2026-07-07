-- V5: 跨项目资金调度 + 合同模板管理 + 数据导出定时配置

-- ======================== 资金调度 ========================
CREATE TABLE IF NOT EXISTS biz_fund_transfer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    transfer_code VARCHAR(50) NOT NULL COMMENT '调拨编号',
    from_project_id BIGINT COMMENT '调出项目ID（NULL=公司资金池）',
    to_project_id BIGINT COMMENT '调入项目ID（NULL=公司资金池）',
    transfer_amount DECIMAL(18,2) NOT NULL COMMENT '调拨金额',
    transfer_date DATE COMMENT '调拨日期',
    transfer_reason VARCHAR(500) COMMENT '调拨原因',
    from_account_id BIGINT COMMENT '调出账户ID',
    to_account_id BIGINT COMMENT '调入账户ID',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态（DRAFT/APPROVED/REJECTED）',
    workflow_instance_id VARCHAR(64) COMMENT '审批流程实例ID',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    INDEX idx_tenant (tenant_id),
    INDEX idx_from_project (from_project_id),
    INDEX idx_to_project (to_project_id)
) COMMENT='跨项目资金调度';

-- ======================== 合同模板 ========================
CREATE TABLE IF NOT EXISTS biz_contract_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    template_name VARCHAR(200) NOT NULL COMMENT '模板名称',
    template_code VARCHAR(50) NOT NULL COMMENT '模板编码',
    contract_category VARCHAR(20) NOT NULL COMMENT '适用合同类型（CONSTRUCTION/MATERIAL/LABOR/MACHINE/SUBCONTRACT/OTHER）',
    template_content TEXT COMMENT '模板内容（含变量占位符 #{xxx}）',
    template_fields TEXT COMMENT '可替换字段列表（JSON数组）',
    description VARCHAR(500) COMMENT '模板说明',
    status TINYINT DEFAULT 1 COMMENT '状态（1启用/0停用）',
    usage_count INT DEFAULT 0 COMMENT '使用次数',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0,
    UNIQUE KEY uk_template_code (tenant_id, template_code),
    INDEX idx_category (contract_category)
) COMMENT='合同模板管理';

-- ======================== 导出定时配置 ========================
CREATE TABLE IF NOT EXISTS biz_export_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    schedule_name VARCHAR(200) NOT NULL COMMENT '定时导出名称',
    module_code VARCHAR(50) NOT NULL COMMENT '模块编码（与BatchImportExport一致）',
    cron_expression VARCHAR(50) NOT NULL COMMENT 'Cron表达式',
    export_params TEXT COMMENT '导出参数（JSON）',
    recipients VARCHAR(500) COMMENT '接收人邮箱（逗号分隔）',
    enabled TINYINT DEFAULT 1 COMMENT '是否启用',
    last_execute_time DATETIME COMMENT '上次执行时间',
    next_execute_time DATETIME COMMENT '下次执行时间',
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_tenant (tenant_id)
) COMMENT='定时导出配置';
