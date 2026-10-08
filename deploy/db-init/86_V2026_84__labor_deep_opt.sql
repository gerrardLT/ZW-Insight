-- ============================================================
-- V2026_84__labor_deep_opt.sql
-- 劳务管理模块深度优化（P3-M6，蓝图 docs/deep-opt/06-labor.md 门A已确认版）
--
-- 内容：
--   1) serial_number_rule 种子：补齐 LABOR_CONTRACT 编号规则（id 900008，前缀 LW）
--   2) 表结构增强与工作流字段补齐：
--      - biz_labor_contract 增加 team_id（支持合同与班组关联穿透）
--      - biz_labor_output_report 增加 workflow_instance_id
--      - biz_labor_settlement 增加 workflow_instance_id
--      - biz_labor_payroll 增加 workflow_instance_id
--      - biz_labor_reward_punish 增加 status, workflow_instance_id（仅审批通过后进入可付额度）
--   3) 新表 biz_labor_payroll_detail —— 工资单包含的工单明细快照表（LI-3 审计追溯与防重复计薪）
--   4) biz_labor_roster 身份证唯一活动守卫（LI-2，采用 V2026_76 unique_active_guard 范式）
--   5) 生产数据修补与勾稽对齐（门A用户已确认，线上只读探针核验于 2026-10-08）：
--      - 91601 合同累计产值对齐单据实际汇总 4000000.00（现存 96041+96042=2.5M+1.5M）
--      - 为 91602、99532、99556 补齐 APPROVED 产值支撑单据（id 96043-96045，均已探针确认空闲）
--      - 校正演示工资单 96031（现值 SETTLED/已付70000/未付15000 无支付流水支撑）
--        → APPROVED、total_paid=0.00、unpaid=total_settlement(85000)
--   6) 菜单与授权：劳务产值上报/劳务结算/劳务奖惩三个新页面菜单（id 806-808）
--      及角色授权（沿用 801-805 的 role 1 + 90061 授权模式，role_menu id 90090-90095）
--
-- 幂等：information_schema 守卫 + NOT EXISTS + UPDATE 条件守卫
-- 双轨：deploy/db-init/86_V2026_84__labor_deep_opt.sql 同内容。
-- ============================================================

SET @schema = DATABASE();

-- 1) serial_number_rule 补充 LABOR_CONTRACT 规则（线上探针：900008/900009 空闲，tenant 1 无该业务类型）
INSERT INTO serial_number_rule (id, business_type, rule_prefix, date_format, seq_length, reset_period, description, tenant_id, created_at, updated_at, deleted, version)
SELECT 900008, 'LABOR_CONTRACT', 'LW', 'yyyyMMdd', 4, 'MONTH', '劳务合同编号', 1, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM serial_number_rule WHERE business_type = 'LABOR_CONTRACT' AND tenant_id = 1);

-- 2) 表结构加列
-- biz_labor_contract 加 team_id（线上探针：现无该列）
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_contract' AND COLUMN_NAME='team_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_contract ADD COLUMN team_id BIGINT NULL COMMENT ''关联班组ID'' AFTER project_id', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- biz_labor_output_report 加 workflow_instance_id
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_output_report' AND COLUMN_NAME='workflow_instance_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_output_report ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''流程实例ID'' AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- biz_labor_settlement 加 workflow_instance_id
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_settlement' AND COLUMN_NAME='workflow_instance_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_settlement ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''流程实例ID'' AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- biz_labor_payroll 加 workflow_instance_id
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_payroll' AND COLUMN_NAME='workflow_instance_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_payroll ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''流程实例ID'' AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- biz_labor_reward_punish 加 status 与 workflow_instance_id
-- 既有存量行经 DEFAULT 'APPROVED' 保持原口径（继续计入付款可付额度净奖惩）
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_reward_punish' AND COLUMN_NAME='status';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_reward_punish ADD COLUMN status VARCHAR(20) DEFAULT ''APPROVED'' COMMENT ''状态（DRAFT/SUBMITTED/APPROVED）'' AFTER reason', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_reward_punish' AND COLUMN_NAME='workflow_instance_id';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_reward_punish ADD COLUMN workflow_instance_id VARCHAR(64) NULL COMMENT ''流程实例ID'' AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) 新表 biz_labor_payroll_detail（主键为雪花 ASSIGN_ID，禁用 AUTO_INCREMENT 与实体契约对齐）
CREATE TABLE IF NOT EXISTS biz_labor_payroll_detail (
  id BIGINT NOT NULL COMMENT '主键（雪花ID）',
  payroll_id BIGINT NOT NULL COMMENT '工资单ID',
  work_order_id BIGINT NOT NULL COMMENT '用工单ID',
  worker_id BIGINT COMMENT '工人ID',
  worker_name VARCHAR(50) NOT NULL COMMENT '工人姓名（快照）',
  order_type VARCHAR(20) COMMENT '用工类型',
  work_date DATE COMMENT '工作日期',
  amount DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '工单应发金额（快照）',
  tenant_id BIGINT NULL COMMENT '租户ID',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payroll_order (payroll_id, work_order_id, deleted, tenant_id),
  KEY idx_lpd_worker (worker_id),
  KEY idx_lpd_payroll (payroll_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='劳务工资单明细快照表（LI-3）';

-- 4) biz_labor_roster 身份证唯一活动守卫（线上探针：现存数据无 (project_id,id_card) 活动重复，ALTER 不会失败）
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_roster' AND COLUMN_NAME='unique_active_guard';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_roster ADD COLUMN unique_active_guard BIGINT GENERATED ALWAYS AS (IF(deleted = 0, 0, NULL)) STORED', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_labor_roster' AND INDEX_NAME='uk_roster_idcard';
SET @s = IF(@c=0, 'ALTER TABLE biz_labor_roster ADD UNIQUE KEY uk_roster_idcard (project_id, id_card, tenant_id, unique_active_guard)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 5) 生产数据修补与单据勾稽补齐
-- 5.1 91601 合同累计产值校正为实际单据之和（96041 2.5M + 96042 1.5M = 4M）
UPDATE biz_labor_contract
SET cumulative_output = 4000000.00
WHERE id = 91601 AND (cumulative_output IS NULL OR cumulative_output <> 4000000.00);

-- 5.2 为 91602、99532、99556 补齐 APPROVED 产值单据（id 96043-96045 线上探针空闲）
INSERT IGNORE INTO biz_labor_output_report (id, project_id, contract_id, current_output, cumulative_output, status, tenant_id, created_by, created_at, updated_at, deleted, version)
VALUES
  (96043, 90002, 91602, 1800000.00, 1800000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
  (96044, 90001, 99532, 8000000.00, 8000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0),
  (96045, 90002, 99556, 3000000.00, 3000000.00, 'APPROVED', 1, 1, NOW(), NOW(), 0, 0);

-- 5.3 演示工资单 96031 状态与金额校正（消除无凭证实付假象；SETTLED/totalPaid 写入仅允许 M9 工资支付链驱动）
UPDATE biz_labor_payroll
SET status = 'APPROVED', total_paid = 0.00, unpaid = total_settlement
WHERE id = 96031 AND status = 'SETTLED';

-- 6) 菜单与角色授权（B1 三断头页面上线；线上探针：sys_menu 806-808 与 sys_role_menu 90090-90095 均空闲）
INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, created_at, updated_at, deleted, version)
SELECT 806, '产值上报', 'MENU', 8, 'output', 'views/labor/output', 'TrendCharts', 6, 1, 0, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 806 OR component = 'views/labor/output');

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, created_at, updated_at, deleted, version)
SELECT 807, '劳务结算', 'MENU', 8, 'settlement', 'views/labor/settlement', 'Files', 7, 1, 0, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 807 OR component = 'views/labor/settlement');

INSERT INTO sys_menu (id, menu_name, menu_type, parent_id, path, component, icon, sort_order, status, hidden, created_at, updated_at, deleted, version)
SELECT 808, '劳务奖惩', 'MENU', 8, 'reward-punish', 'views/labor/reward-punish', 'Scale', 8, 1, 0, NOW(), NOW(), 0, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 808 OR component = 'views/labor/reward-punish');

-- 角色授权：沿用 801-805 的授权模式（role 1 管理员 + 90061 项目经理）
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 90090, 1, 806 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 806);
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 90091, 1, 807 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 807);
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 90092, 1, 808 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 1 AND menu_id = 808);
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 90093, 90061, 806 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 90061 AND menu_id = 806);
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 90094, 90061, 807 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 90061 AND menu_id = 807);
INSERT INTO sys_role_menu (id, role_id, menu_id)
SELECT 90095, 90061, 808 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = 90061 AND menu_id = 808);
