-- ============================================================
-- V2026_100__lateral_link_schema_completion.sql
-- 核心业务链路 6 条横向联动断裂修复 —— 表结构补齐（幂等）
--
-- 包含：
--   1) X4：biz_change_event 新增可空 contract_id（关联合同ID）及索引
--   2) X6：biz_labor_contract / biz_machine_contract / biz_subcontract
--          补齐 cumulative_invoice_received（累计收票金额）列
--   3) X1：biz_material_outbound_detail 新增可空 boq_item_id（关联BOQ清单项ID）及索引
--   4) X2：新建 biz_wage_payment_detail（农民工工资专户代发明细表，承接劳务工资单审批→待代发→确认代发闭环）
-- ============================================================

-- 1) X4: biz_change_event.contract_id
SET @col_exists := (SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema = DATABASE() AND table_name = 'biz_change_event' AND column_name = 'contract_id');
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE biz_change_event ADD COLUMN contract_id BIGINT NULL COMMENT ''关联合同ID（可空；用于回写施工合同累计变更额）'' AFTER project_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = DATABASE() AND table_name = 'biz_change_event' AND index_name = 'idx_biz_change_event_contract');
SET @ddl_idx := IF(@idx_exists = 0,
    'ALTER TABLE biz_change_event ADD INDEX idx_biz_change_event_contract (contract_id)',
    'SELECT 1');
PREPARE stmt_idx FROM @ddl_idx; EXECUTE stmt_idx; DEALLOCATE PREPARE stmt_idx;

-- 2) X6: 劳务/机械/分包合同补齐 cumulative_invoice_received
SET @col_labor := (SELECT COUNT(*) FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'biz_labor_contract' AND column_name = 'cumulative_invoice_received');
SET @ddl_labor := IF(@col_labor = 0,
    'ALTER TABLE biz_labor_contract ADD COLUMN cumulative_invoice_received DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT ''累计收票金额''',
    'SELECT 1');
PREPARE stmt_labor FROM @ddl_labor; EXECUTE stmt_labor; DEALLOCATE PREPARE stmt_labor;

SET @col_machine := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'biz_machine_contract' AND column_name = 'cumulative_invoice_received');
SET @ddl_machine := IF(@col_machine = 0,
    'ALTER TABLE biz_machine_contract ADD COLUMN cumulative_invoice_received DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT ''累计收票金额''',
    'SELECT 1');
PREPARE stmt_machine FROM @ddl_machine; EXECUTE stmt_machine; DEALLOCATE PREPARE stmt_machine;

SET @col_sub := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'biz_subcontract' AND column_name = 'cumulative_invoice_received');
SET @ddl_sub := IF(@col_sub = 0,
    'ALTER TABLE biz_subcontract ADD COLUMN cumulative_invoice_received DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT ''累计收票金额''',
    'SELECT 1');
PREPARE stmt_sub FROM @ddl_sub; EXECUTE stmt_sub; DEALLOCATE PREPARE stmt_sub;

-- 3) X1: biz_material_outbound_detail.boq_item_id
SET @col_boq := (SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'biz_material_outbound_detail' AND column_name = 'boq_item_id');
SET @ddl_boq := IF(@col_boq = 0,
    'ALTER TABLE biz_material_outbound_detail ADD COLUMN boq_item_id BIGINT NULL COMMENT ''关联BOQ工程量清单项ID（可空；用于工程量-材料限额对账）'' AFTER outbound_id',
    'SELECT 1');
PREPARE stmt_boq FROM @ddl_boq; EXECUTE stmt_boq; DEALLOCATE PREPARE stmt_boq;

SET @idx_boq := (SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'biz_material_outbound_detail' AND index_name = 'idx_outbound_detail_boq_item');
SET @ddl_idx_boq := IF(@idx_boq = 0,
    'ALTER TABLE biz_material_outbound_detail ADD INDEX idx_outbound_detail_boq_item (boq_item_id)',
    'SELECT 1');
PREPARE stmt_idx_boq FROM @ddl_idx_boq; EXECUTE stmt_idx_boq; DEALLOCATE PREPARE stmt_idx_boq;

-- 4) X2: biz_wage_payment_detail（农民工工资专户代发明细表）
CREATE TABLE IF NOT EXISTS biz_wage_payment_detail (
    id BIGINT NOT NULL COMMENT '主键ID',
    tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    account_id BIGINT NULL COMMENT '工资专户ID（确认代发时绑定）',
    payroll_id BIGINT NOT NULL COMMENT '来源劳务工资单ID',
    team_id BIGINT NULL COMMENT '班组ID',
    period_start DATE NULL COMMENT '计薪周期开始',
    period_end DATE NULL COMMENT '计薪周期结束',
    amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '应代发金额',
    pay_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '代发状态（PENDING-待代发/PAID-已代发）',
    pay_date DATE NULL COMMENT '实际代发日期',
    remark VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT NULL COMMENT '创建人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by BIGINT NULL COMMENT '更新人',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    INDEX idx_wage_pay_detail_project (tenant_id, project_id),
    INDEX idx_wage_pay_detail_payroll (payroll_id),
    INDEX idx_wage_pay_detail_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='农民工工资专户代发明细表';
