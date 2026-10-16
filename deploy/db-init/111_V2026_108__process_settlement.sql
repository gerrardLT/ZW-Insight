-- C1 收入侧过程结算（2026-10-16）
-- 模型：biz_final_settlement 扩展为 PROCESS（过程）/FINAL（竣工，存量默认）两型；
--       来源明细 biz_settlement_source 记录产值/签证/手工行的申报、审定、争议与核减；
--       施工合同补累计结算列，PROCESS/FINAL 审批通过均原子回写。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='settlement_type')=0,
 "ALTER TABLE biz_final_settlement ADD COLUMN settlement_type VARCHAR(20) NOT NULL DEFAULT 'FINAL'", 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='period_start')=0,
 'ALTER TABLE biz_final_settlement ADD COLUMN period_start DATE NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='period_end')=0,
 'ALTER TABLE biz_final_settlement ADD COLUMN period_end DATE NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='prepay_deduct')=0,
 'ALTER TABLE biz_final_settlement ADD COLUMN prepay_deduct DECIMAL(18,2) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='retention_deduct')=0,
 'ALTER TABLE biz_final_settlement ADD COLUMN retention_deduct DECIMAL(18,2) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='other_deduct')=0,
 'ALTER TABLE biz_final_settlement ADD COLUMN other_deduct DECIMAL(18,2) NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='declared_amount')=0,
 'ALTER TABLE biz_final_settlement ADD COLUMN declared_amount DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_final_settlement' AND column_name='disputed_amount')=0,
 'ALTER TABLE biz_final_settlement ADD COLUMN disputed_amount DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 施工合同累计结算列（NULL 容忍，SQL 侧 COALESCE；存量行不回填，历史无过程结算不虚构）
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_construction_contract' AND column_name='cumulative_settlement')=0,
 'ALTER TABLE biz_construction_contract ADD COLUMN cumulative_settlement DECIMAL(18,2) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS biz_settlement_source (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 settlement_id BIGINT NOT NULL,
 source_type VARCHAR(30) NOT NULL, source_id BIGINT NULL,
 description VARCHAR(200) NULL,
 declared_amount DECIMAL(18,2) NOT NULL DEFAULT 0, confirmed_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
 disputed_amount DECIMAL(18,2) NOT NULL DEFAULT 0, reduce_reason VARCHAR(500) NULL,
 deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 INDEX idx_settlement (tenant_id,settlement_id,deleted),
 INDEX idx_source (tenant_id,source_type,source_id,deleted),
 INDEX idx_project (tenant_id,project_id,deleted),
 CHECK (declared_amount >= 0), CHECK (confirmed_amount >= 0), CHECK (disputed_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 来源防重复占用由应用层在提交/审批生效前校验（JOIN 结算状态 SUBMITTED/APPROVED），
-- 跨表复合唯一无法用生成列表达，登记为已知实现边界。
-- Rollback: 代码回退保留可空列与新表；已生成的过程结算应收经 ReceivableService 反冲路径处理，不手改台账。
