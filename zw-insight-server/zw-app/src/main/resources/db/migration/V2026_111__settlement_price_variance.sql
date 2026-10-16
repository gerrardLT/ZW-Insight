-- C4 材料结算价差与容差（2026-10-16）
-- 结算单价可偏离入库单价（调价/折扣/扣款/运费/税率变化等真实场景），
-- 超容差必须填写修订原因；保存/提交/审批生效三处服务端重算。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='biz_purchase_settlement_detail' AND column_name='source_unit_price')=0,
 'ALTER TABLE biz_purchase_settlement_detail ADD COLUMN source_unit_price DECIMAL(18,2) NULL, ADD COLUMN settlement_unit_price DECIMAL(18,2) NULL, ADD COLUMN price_difference DECIMAL(18,2) NULL, ADD COLUMN difference_reason VARCHAR(500) NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
-- 租户级容差（默认 0：任何偏离都须填原因；一行一租户）
CREATE TABLE IF NOT EXISTS biz_settlement_tolerance (
 id BIGINT NOT NULL PRIMARY KEY, tenant_id BIGINT NOT NULL,
 pct_tolerance DECIMAL(7,4) NOT NULL DEFAULT 0,
 abs_tolerance DECIMAL(18,2) NOT NULL DEFAULT 0,
 deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
 created_by BIGINT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE INDEX uk_tolerance_tenant (tenant_id),
 CHECK (pct_tolerance >= 0), CHECK (abs_tolerance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Rollback: 代码回退保留可空列与新表；历史明细 source/settlement 单价 NULL 表示按原价口径。
