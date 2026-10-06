-- ============================================================
-- V2026_81__project_deep_opt.sql
-- 项目管理模块深度优化（P1-M1，蓝图 docs/deep-opt/01-project.md 门A确认版）
--
-- 内容：
--   1) biz_project 补周期与状态语义列：计划/实际开竣工日期、暂停/终止/落标原因
--      （状态值 LOST/PAUSED/TERMINATED 走既有 VARCHAR status 列，无枚举变更）
--   2) 新表 biz_project_status_log —— 流转大事记（审计 + 详情页时间线共用）
--   3) 新表 biz_project_change_log —— 立项后关键字段变更台账（I4 不变量）
--   4) sys_config 种子：project_filing_approval_enabled（B1 立项审批开关，默认 false
--      = 直置位，兼容 L4 lifecycle-sim 的既有断言）
--
-- 存量数据回填（actual_*_date 由合同生效/竣工验收推导）不在本迁移内——
-- 按确认流程：只读报告脚本先行，用户确认后单独执行（见 keys/_backfill_project_dates_report.sql）。
-- 幂等：全部 information_schema 守卫 + INSERT IGNORE。
-- 双轨：deploy/db-init/83_V2026_81__project_deep_opt.sql 同内容。
-- ============================================================

-- 1) biz_project 加列（逐列守卫）
SET @schema = DATABASE();
SET @tbl = 'biz_project';

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME=@tbl AND COLUMN_NAME='planned_start_date';
SET @s = IF(@c=0, 'ALTER TABLE biz_project ADD COLUMN planned_start_date DATE NULL COMMENT ''计划开工日期'' AFTER need_tender', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME=@tbl AND COLUMN_NAME='planned_end_date';
SET @s = IF(@c=0, 'ALTER TABLE biz_project ADD COLUMN planned_end_date DATE NULL COMMENT ''计划竣工日期'' AFTER planned_start_date', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME=@tbl AND COLUMN_NAME='actual_start_date';
SET @s = IF(@c=0, 'ALTER TABLE biz_project ADD COLUMN actual_start_date DATE NULL COMMENT ''实际开工日期（开工事件回写）'' AFTER planned_end_date', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME=@tbl AND COLUMN_NAME='actual_end_date';
SET @s = IF(@c=0, 'ALTER TABLE biz_project ADD COLUMN actual_end_date DATE NULL COMMENT ''实际竣工日期（竣工验收回写）'' AFTER actual_start_date', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME=@tbl AND COLUMN_NAME='pause_reason';
SET @s = IF(@c=0, 'ALTER TABLE biz_project ADD COLUMN pause_reason VARCHAR(300) NULL COMMENT ''暂停原因（PAUSED 态）'' AFTER actual_end_date', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME=@tbl AND COLUMN_NAME='terminate_reason';
SET @s = IF(@c=0, 'ALTER TABLE biz_project ADD COLUMN terminate_reason VARCHAR(500) NULL COMMENT ''终止原因（TERMINATED 态，走审批）'' AFTER pause_reason', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME=@tbl AND COLUMN_NAME='lost_reason';
SET @s = IF(@c=0, 'ALTER TABLE biz_project ADD COLUMN lost_reason VARCHAR(300) NULL COMMENT ''落标原因（LOST 态，投标落标回写）'' AFTER terminate_reason', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) 流转大事记表
CREATE TABLE IF NOT EXISTS biz_project_status_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  from_status VARCHAR(32) NULL COMMENT '流转前状态',
  to_status VARCHAR(32) NOT NULL COMMENT '流转后状态',
  event VARCHAR(64) NOT NULL COMMENT '触发事件（SUBMIT/WIN_BID/LOSE_BID/START_CONSTRUCTION/PAUSE/RESUME/COMPLETE/APPLY_CLOSE/APPROVE_CLOSE/REJECT_CLOSE/TERMINATE_APPROVED/WITHDRAW）',
  remark VARCHAR(500) NULL COMMENT '备注（落标/暂停/终止原因等）',
  operator_id BIGINT NULL COMMENT '操作人（null=系统回调）',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_psl_project (project_id, created_at),
  KEY idx_psl_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目状态流转大事记';

-- 3) 变更台账表（立项后关键字段修改留痕）
CREATE TABLE IF NOT EXISTS biz_project_change_log (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  field_name VARCHAR(64) NOT NULL COMMENT '字段名（ownerCompanyName/budgetAmount/plannedStartDate…）',
  old_value VARCHAR(1000) NULL COMMENT '旧值',
  new_value VARCHAR(1000) NULL COMMENT '新值',
  operator_id BIGINT NULL COMMENT '操作人',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  PRIMARY KEY (id),
  KEY idx_pcl_project (project_id, created_at),
  KEY idx_pcl_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目关键字段变更台账';

-- 4) 立项审批开关（默认关：submit 直置位，兼容既有 L4 断言）
INSERT IGNORE INTO sys_config (id, config_key, config_value, config_name, config_group, value_type, default_value, value_range, remark, created_at, updated_at) VALUES
(1049, 'project_filing_approval_enabled', 'false', '项目立项需审批', 'project', 'BOOLEAN', 'false', NULL,
 '开启后项目提交立项走审批流（复用 project_close_approval 审批链，businessType=PROJECT_FILING）；默认关闭=提交即立项', NOW(), NOW());
