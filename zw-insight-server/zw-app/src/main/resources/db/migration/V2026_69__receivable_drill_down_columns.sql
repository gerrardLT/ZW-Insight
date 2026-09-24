-- ============================================================
-- V2026_69__receivable_drill_down_columns.sql
-- 应收台账下钻 8 级链所需列（驾驶舱 UI 原型 §10「点击项目继续下钻」）
--
-- §10 要求的 8 级链：项目 → 应收款 → 对应工程节点 → 应收日期 → 实际申请日期
--                    → 甲方审核状态 → 负责人 → 下一步动作
-- 现有 biz_receivable 已覆盖：项目(project_id/project_name)、应收款(receivable_amount)、
-- 应收日期(due_date)。本次补齐其余 5 级所需的 6 列（负责人拆 id + name 两列，
-- 与 biz_risk_register 的 owner_id/owner_name 做法一致，便于列表直接展示不再联表）。
--
-- 【为什么全部可空且不做默认值/自动回填——2026-09-24 数据源普查结论】
--   ① 「对应工程节点」：结算单 biz_project_settlement **无任何节点/期次字段**
--      （仅 projectId/settlementCode/各金额列）；产值报告 biz_output_report 虽有
--      report_period，但与结算单之间**没有外键或单号关联**，按 project_id + 时间
--      近似匹配会制造假关联（把不相干的期次当成该笔应收的工程节点），属伪造数据，不采用。
--   ② 「甲方审核状态 / 实际申请日期 / 负责人 / 下一步动作」：属甲方侧与内部管理信息，
--      系统内无来源单据，只能人工登记。
--   → 六列全部 NULL 可空、无 DEFAULT；前端对 NULL 显示「未登记」，
--     **不得用「待审核」「—」「未知」等默认值填充**（那会把「没登记」伪装成「已登记为某状态」）。
--
-- 【幂等】information_schema 检查 + PREPARE/EXECUTE 条件 ALTER（裸 ALTER ADD COLUMN 重复执行会报错）
-- ============================================================

SET @dbname = DATABASE();
SET @tablename = 'biz_receivable';

-- 1) milestone_node —— 对应工程节点（§10 第 3 级）
SET @col = 'milestone_node';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col,
         ' VARCHAR(200) NULL COMMENT ''对应工程节点（§10 第3级；人工维护，结算单无节点字段故不自动回填）''')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) apply_date —— 实际申请日期（§10 第 5 级，向甲方提交结算/付款申请的日期）
SET @col = 'apply_date';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col,
         ' DATE NULL COMMENT ''实际申请日期（§10 第5级；人工登记，系统内无来源单据）''')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) owner_review_status —— 甲方审核状态（§10 第 6 级）
SET @col = 'owner_review_status';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col,
         ' VARCHAR(30) NULL COMMENT ''甲方审核状态（§10 第6级；SUBMITTED/UNDER_REVIEW/CONFIRMED/DISPUTED，NULL=未登记）''')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) owner_review_date —— 甲方审核日期（与申请日期配对，判断甲方停留时长）
SET @col = 'owner_review_date';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col,
         ' DATE NULL COMMENT ''甲方审核日期（人工登记；与 apply_date 配对可算甲方停留天数）''')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) owner_id / owner_name —— 负责人（§10 第 7 级）
SET @col = 'owner_id';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col,
         ' BIGINT NULL COMMENT ''催收负责人ID（§10 第7级；人工指定，NULL=未登记）''')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col = 'owner_name';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col,
         ' VARCHAR(50) NULL COMMENT ''催收负责人姓名（冗余存储便于列表展示，与 biz_risk_register 同做法）''')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) next_action —— 下一步动作（§10 第 8 级）
SET @col = 'next_action';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND COLUMN_NAME = @col) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @col,
         ' VARCHAR(500) NULL COMMENT ''下一步动作（§10 第8级；人工维护，如「本周内找甲方财务对账」）''')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 查询索引：按甲方审核状态筛选待催收（台账页「甲方未确认」快捷视图用）
SET @idx = 'idx_owner_review_status';
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @dbname AND TABLE_NAME = @tablename AND INDEX_NAME = @idx) > 0,
  'SELECT 1',
  CONCAT('CREATE INDEX ', @idx, ' ON ', @tablename, ' (owner_review_status)')));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
