-- ============================================================
-- V2026_83__budget_cbs_deep_opt.sql
-- 预算与 CBS 模块深度优化（P2-M4，蓝图 docs/deep-opt/04-budget.md 门A确认版）
--
-- 内容：
--   1) biz_budget_change 加列：change_code 变更单编号（BudgetChangeService 生成）
--   2) biz_budget 项目唯一原始预算守卫（BI-2）：采用 V2026_76 unique_active_guard
--      生成列范式——活动行 guard=0 参与唯一约束，删除行 guard=NULL 不占位，
--      支持反复删除后重建（严禁把 deleted 直接入唯一键：第二次逻辑删除即撞键）
--   3) serial_number_rule 种子：BUDGET_CHANGE 编号规则（租户 1）。
--      缺此规则 SerialNumberService.generate 直接抛「未配置编号规则」，
--      预算变更创建必失败。测试租户 9999 由 init-test-tenant.sh 动态复制租户 1
--      全部规则（id+95000 偏移），无需单独种子。
--
-- 幂等：information_schema 守卫 + 业务键守卫 INSERT。
-- 注意：biz_budget 存量活动行若已有 (project_id, budget_type, tenant_id) 重复，
--       ADD UNIQUE KEY 将以 1062 中止——上线前须先核查清理（对齐 V2026_76 惯例）。
-- 双轨：deploy/db-init/85_V2026_83__budget_cbs_deep_opt.sql 同内容。
-- ============================================================

SET @schema = DATABASE();

-- 1) biz_budget_change 加列 change_code
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_budget_change' AND COLUMN_NAME='change_code';
SET @s = IF(@c=0, 'ALTER TABLE biz_budget_change ADD COLUMN change_code VARCHAR(64) NULL COMMENT ''变更单编号'' AFTER id', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) biz_budget 唯一守卫：生成列 + 复合唯一键（防并发重复创建 ORIGINAL 预算）
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_budget' AND COLUMN_NAME='unique_active_guard';
SET @s = IF(@c=0, 'ALTER TABLE biz_budget ADD COLUMN unique_active_guard BIGINT GENERATED ALWAYS AS (IF(deleted = 0, 0, NULL)) STORED', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_budget' AND INDEX_NAME='uk_budget_project_type';
SET @s = IF(@c=0, 'ALTER TABLE biz_budget ADD UNIQUE KEY uk_budget_project_type (project_id, budget_type, tenant_id, unique_active_guard)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) BUDGET_CHANGE 编号规则种子（业务键守卫，防 INSERT IGNORE 撞号静默跳过）
INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900007, 'BUDGET_CHANGE', 'BG', 'yyyyMMdd', 4, 'DAY', '预算变更单编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'BUDGET_CHANGE' AND tenant_id = 1);
