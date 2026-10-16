-- C3 税务事实链（2026-10-16）
-- 1) 四类主支出合同补税额三件套（可空；历史税率未知保持 NULL 不猜填）
-- 2) 销项发票台账 biz_invoice_issued（真实票据事实，区别于开票申请）
-- 3) 进项发票 biz_invoice_received 补查验/用途/入账状态（纯登记不改金额链路）
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_purchase_contract' AND column_name='tax_rate')=0,
 'ALTER TABLE biz_purchase_contract ADD COLUMN tax_rate DECIMAL(7,4) NULL, ADD COLUMN amount_without_tax DECIMAL(18,2) NULL, ADD COLUMN tax_amount DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_labor_contract' AND column_name='tax_rate')=0,
 'ALTER TABLE biz_labor_contract ADD COLUMN tax_rate DECIMAL(7,4) NULL, ADD COLUMN amount_without_tax DECIMAL(18,2) NULL, ADD COLUMN tax_amount DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_machine_contract' AND column_name='tax_rate')=0,
 'ALTER TABLE biz_machine_contract ADD COLUMN tax_rate DECIMAL(7,4) NULL, ADD COLUMN amount_without_tax DECIMAL(18,2) NULL, ADD COLUMN tax_amount DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_subcontract' AND column_name='tax_rate')=0,
 'ALTER TABLE biz_subcontract ADD COLUMN tax_rate DECIMAL(7,4) NULL, ADD COLUMN amount_without_tax DECIMAL(18,2) NULL, ADD COLUMN tax_amount DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 销项发票台账：身份 guard 沿用 V2026_103 模式（数电20位 或 传统8位号+10/12位代码）
CREATE TABLE IF NOT EXISTS biz_invoice_issued (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NULL,
 contract_id BIGINT NULL, apply_id BIGINT NULL,
 invoice_number VARCHAR(20) NOT NULL, invoice_code VARCHAR(12) NULL,
 issue_date DATE NOT NULL, invoice_type VARCHAR(30) NULL,
 direction VARCHAR(6) NOT NULL DEFAULT 'BLUE', original_id BIGINT NULL,
 amount DECIMAL(18,2) NOT NULL, tax_rate DECIMAL(7,4) NULL,
 tax_amount DECIMAL(18,2) NULL, amount_without_tax DECIMAL(18,2) NULL,
 verify_status VARCHAR(12) NOT NULL DEFAULT 'UNVERIFIED', verify_time DATETIME NULL, verified_by BIGINT NULL,
 bookkeep_status VARCHAR(12) NOT NULL DEFAULT 'UNBOOKED',
 active_issued_invoice_identity VARCHAR(33) GENERATED ALWAYS AS (CASE WHEN deleted=0 AND NULLIF(TRIM(invoice_number),'') IS NOT NULL THEN CASE WHEN CHAR_LENGTH(TRIM(invoice_number))=20 THEN CONCAT('D:',TRIM(invoice_number)) ELSE CONCAT(COALESCE(TRIM(invoice_code),''),':',TRIM(invoice_number)) END ELSE NULL END) STORED,
 deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE INDEX uk_issued_invoice_identity (tenant_id, active_issued_invoice_identity),
 INDEX idx_project (tenant_id,project_id,deleted), INDEX idx_contract (tenant_id,contract_id,deleted),
 INDEX idx_apply (tenant_id,apply_id,deleted),
 CHECK (amount != 0), CHECK (direction IN ('BLUE','RED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 进项发票状态列（默认未查验/未确认/未入账，存量不受影响）
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_invoice_received' AND column_name='verify_status')=0,
 "ALTER TABLE biz_invoice_received ADD COLUMN verify_status VARCHAR(12) NOT NULL DEFAULT 'UNVERIFIED', ADD COLUMN verify_time DATETIME NULL, ADD COLUMN verified_by BIGINT NULL, ADD COLUMN use_status VARCHAR(12) NOT NULL DEFAULT 'UNCONFIRMED', ADD COLUMN bookkeep_status VARCHAR(12) NOT NULL DEFAULT 'UNBOOKED'", 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 合同累计开票语义不变（开票申请审批口径）；台账是记录层，双口径并列展示由前端标注。
-- Rollback: 代码回退保留可空列与新表；唯一索引/生成列移除须备份评估。

-- 销项发票台账菜单（幂等：已存在则跳过；id=508 已核未占用）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden)
SELECT 508, '销项发票台账', 'MENU', 5, 'invoice-issued', 'views/finance/invoice-issued', 'Ticket', 5, 1, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 508 OR (parent_id = 5 AND path = 'invoice-issued'));
