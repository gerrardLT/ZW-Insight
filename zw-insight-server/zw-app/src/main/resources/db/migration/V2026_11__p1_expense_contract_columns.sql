-- ============================================
-- P1 业务完善 - biz_expense_contract 表结构变更
-- 版本: V2026_11
-- 功能: 合同到期提醒(功能5)和档案展示(功能8)的前置依赖
-- Requirements: 5.2, 5.4, 8.4
-- ============================================

-- 创建 biz_expense_contract 表（如不存在）
-- 通用合同表(采购/劳务/机械/分包/其它收支)
CREATE TABLE IF NOT EXISTS biz_expense_contract (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    contract_code VARCHAR(50) NOT NULL COMMENT '合同编号',
    contract_name VARCHAR(200) COMMENT '合同名称',
    contract_category VARCHAR(20) NOT NULL COMMENT '合同分类(MATERIAL/LABOR/MACHINE/SUBCONTRACT/OTHER_INCOME/OTHER_EXPENSE)',
    party_a_id BIGINT COMMENT '甲方ID',
    party_a_name VARCHAR(200) COMMENT '甲方名称',
    party_b_id BIGINT COMMENT '乙方ID(供应商)',
    party_b_name VARCHAR(200) COMMENT '乙方名称',
    signing_date DATE COMMENT '签订日期',
    end_date DATE COMMENT '合同到期日期',
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
    responsible_user_id BIGINT COMMENT '合同负责人ID',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT '状态(DRAFT/EFFECTIVE/SETTLED/CLOSED/TERMINATED)',
    workflow_instance_id VARCHAR(64) COMMENT '流程实例ID',
    created_by BIGINT COMMENT '创建人ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT DEFAULT 0 COMMENT '逻辑删除(0-未删除 1-已删除)',
    version INT DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    INDEX idx_project (project_id),
    INDEX idx_category (contract_category),
    INDEX idx_tenant (tenant_id),
    INDEX idx_end_date (end_date),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通用支出合同表';

-- 如果表已存在，确保 end_date 和 contract_name 字段存在
-- 添加合同到期日期字段（合同到期扫描功能5的前置依赖）
ALTER TABLE biz_expense_contract ADD COLUMN IF NOT EXISTS end_date DATE COMMENT '合同到期日期';

-- 添加合同名称字段（到期提醒消息和档案展示功能8的前置依赖）
ALTER TABLE biz_expense_contract ADD COLUMN IF NOT EXISTS contract_name VARCHAR(200) COMMENT '合同名称';

-- 添加合同负责人字段（到期提醒通知目标）
ALTER TABLE biz_expense_contract ADD COLUMN IF NOT EXISTS responsible_user_id BIGINT COMMENT '合同负责人ID';

-- 为 end_date 添加索引以支持到期扫描查询
-- 使用 IF NOT EXISTS 避免重复创建索引
CREATE INDEX IF NOT EXISTS idx_end_date ON biz_expense_contract (end_date);
