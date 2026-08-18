-- ============================================================================
-- V2026_40__e2e_guard_remark_columns.sql
-- 为 biz_material_inbound / biz_subcontract_settlement 补充 remark 备注列。
--
-- 背景：E2E 写路径测试（e2e-real）会把单据推进到非草稿状态，删除守卫
-- 「仅草稿状态可删除」经 E2eTestGuard 对携带 E2E_TEST_ 前缀标记的测试数据放行；
-- 该两张表此前无任何可承载标记的文本列，补 remark 作为统一标记载体
-- （与其他 biz_ 单据表 remark 列对齐，幂等可重复执行）。
-- ============================================================================

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_material_inbound' AND COLUMN_NAME = 'remark') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_material_inbound` ADD COLUMN `remark` VARCHAR(500) NULL COMMENT ''备注'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_subcontract_settlement' AND COLUMN_NAME = 'remark') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_subcontract_settlement` ADD COLUMN `remark` VARCHAR(500) NULL COMMENT ''备注'''
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
