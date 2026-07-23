-- ============================================
-- P1 修复 - 供应商评价表新增"评价类型"字段
-- 版本: V2026_28
-- 背景:
--   实体 BizSupplierEvaluation 含 evaluationType 字段（AUTO-系统自动 / MANUAL-人工评价），
--   但 biz_supplier_evaluation 建表脚本(00_schema.sql L2550)与任何迁移均未创建该列，
--   导致 basedata/supplier-evaluation 查询报
--   "Unknown column 'evaluation_type' in 'field list'" 返回 500。
-- 影响:
--   biz_supplier_evaluation 增加 evaluation_type 列（幂等，位于 remark 之后）。
-- ============================================

SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'biz_supplier_evaluation' AND COLUMN_NAME = 'evaluation_type') > 0,
    'SELECT 1',
    'ALTER TABLE `biz_supplier_evaluation` ADD COLUMN `evaluation_type` VARCHAR(20) COMMENT ''评价类型（AUTO-系统自动/MANUAL-人工评价）'' AFTER `remark`'
));
PREPARE __stmt FROM @sql;
EXECUTE __stmt;
DEALLOCATE PREPARE __stmt;
