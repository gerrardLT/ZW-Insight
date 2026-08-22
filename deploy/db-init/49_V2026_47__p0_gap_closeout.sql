-- ============================================================================
-- 49_V2026_47__p0_gap_closeout.sql
-- P0 差距收口：回款认领/核销字段 + 整改附件字段（幂等可重复执行）。
--
-- 背景（spec: .kiro/specs/p0-gap-closeout）：
--   1) biz_payment_received 增加认领状态机字段（UNCLAIMED→CLAIMED→WRITTEN_OFF），
--      支撑回款认领/核销闭环（Requirement 4）。
--   2) biz_rectification 增加 attachment_ids，承载整改佐证照片附件ID列表
--      （逗号分隔，复用 zw-file 文件ID；Requirement 2）。
--   3) biz_labor_roster 进退场字段（entry_date/exit_date/status）已由迁移
--      34_V2026_32 补齐，本脚本不再重复添加（Requirement 5 复用现有字段）。
--   4) bd_material 增加 material_code，支撑移动端扫码出入库按编码查材料
--      （Requirement 6）。
-- ============================================================================

-- 1. biz_payment_received.claim_status（认领状态：UNCLAIMED/CLAIMED/WRITTEN_OFF）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_received' AND COLUMN_NAME = 'claim_status') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_received` ADD COLUMN `claim_status` VARCHAR(20) NOT NULL DEFAULT ''UNCLAIMED'' COMMENT ''认领状态（UNCLAIMED/CLAIMED/WRITTEN_OFF）'' AFTER status'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 2. biz_payment_received.claimed_by（认领人ID）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_received' AND COLUMN_NAME = 'claimed_by') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_received` ADD COLUMN `claimed_by` BIGINT NULL COMMENT ''认领人ID'' AFTER claim_status'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 3. biz_payment_received.claimed_at（认领时间）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_received' AND COLUMN_NAME = 'claimed_at') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_received` ADD COLUMN `claimed_at` DATETIME NULL COMMENT ''认领时间'' AFTER claimed_by'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 4. biz_payment_received.claim_status 索引（列表按认领状态筛选高频）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_payment_received' AND INDEX_NAME = 'idx_claim_status') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_payment_received` ADD INDEX `idx_claim_status` (`claim_status`)'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 5. biz_rectification.attachment_ids（整改佐证附件ID列表，逗号分隔）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_rectification' AND COLUMN_NAME = 'attachment_ids') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_rectification` ADD COLUMN `attachment_ids` VARCHAR(500) NULL COMMENT ''附件ID列表（逗号分隔）'' AFTER rectification_content'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 6. 回款认领/核销按钮级权限目录（沿用 36_V2026_34 的固定 ID 段 20001-20099，
--    INSERT IGNORE 可重复执行；SUPER_ADMIN 在拦截器中豁免，绑定仅补全目录供授权参考）
INSERT IGNORE INTO sys_menu (id, menu_name, menu_type, parent_id, permission, status, hidden, weight) VALUES
(20082, '回款认领', 'BUTTON', 5, 'finance:paymentreceived:claim',    1, 1, 'NORMAL'),
(20083, '回款核销', 'BUTTON', 5, 'finance:paymentreceived:writeoff', 1, 1, 'NORMAL'),
(20084, '库存预警配置维护', 'BUTTON', 18, 'material:stockwarningconfig:save',   1, 1, 'NORMAL'),
(20085, '库存预警配置删除', 'BUTTON', 18, 'material:stockwarningconfig:delete', 1, 1, 'NORMAL');

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id) VALUES
(20082, 1, 20082),
(20083, 1, 20083),
(20084, 1, 20084),
(20085, 1, 20085);

-- 7. bd_material.material_code（材料编码，扫码出入库按编码定位材料；Requirement 6）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bd_material' AND COLUMN_NAME = 'material_code') > 0,
    'SELECT 1',
    'ALTER TABLE `bd_material` ADD COLUMN `material_code` VARCHAR(100) NULL COMMENT ''材料编码（条码）'' AFTER material_name'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

-- 8. bd_material.material_code 索引（扫码查询高频）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bd_material' AND INDEX_NAME = 'idx_material_code') > 0,
    'SELECT 1',
    'ALTER TABLE `bd_material` ADD INDEX `idx_material_code` (`material_code`)'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
