-- ============================================================
-- V2026_82__tender_deep_opt.sql
-- 投标管理模块深度优化（P1-M2，蓝图 docs/deep-opt/02-tender.md 门A确认版）
--
-- 内容：
--   1) biz_tender_register 加列：落标原因分类（lost_reason_category）
--   2) biz_open_bid_record 加列：开标金额（bid_amount）、落标原因分类
--   3) 新表 biz_tender_person_binding —— 投标押证绑定（TI-2 排他锁定）
--      绑定 person_certificate_id + register_id，在 SUBMITTED/WON 状态锁定防一证多投
--
-- 幂等：information_schema 守卫 + CREATE TABLE IF NOT EXISTS。
-- 双轨：deploy/db-init/84_V2026_82__tender_deep_opt.sql 同内容。
-- ============================================================

SET @schema = DATABASE();

-- 1) biz_tender_register 加落标原因分类
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_tender_register' AND COLUMN_NAME='lost_reason_category';
SET @s = IF(@c=0, 'ALTER TABLE biz_tender_register ADD COLUMN lost_reason_category VARCHAR(32) NULL COMMENT ''落标原因分类（PRICE_OVER/TECH_WEAK/BIZ_DEVIATION/CREDIT_LACK/OTHER）'' AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) biz_open_bid_record 加开标金额与落标原因
SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_open_bid_record' AND COLUMN_NAME='bid_amount';
SET @s = IF(@c=0, 'ALTER TABLE biz_open_bid_record ADD COLUMN bid_amount DECIMAL(18,2) NULL COMMENT ''开标金额（中标价或最低报价）'' AFTER is_won', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SELECT COUNT(*) INTO @c FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA=@schema AND TABLE_NAME='biz_open_bid_record' AND COLUMN_NAME='lost_reason_category';
SET @s = IF(@c=0, 'ALTER TABLE biz_open_bid_record ADD COLUMN lost_reason_category VARCHAR(32) NULL COMMENT ''落标原因分类'' AFTER win_info', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) 投标押证绑定表（TI-2 排他锁定）
CREATE TABLE IF NOT EXISTS biz_tender_person_binding (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  register_id BIGINT NOT NULL COMMENT '投标报名ID',
  project_id BIGINT NOT NULL COMMENT '项目ID（冗余，排他校验查项目名称用）',
  person_certificate_id BIGINT NOT NULL COMMENT '人员证件ID',
  person_name VARCHAR(64) NOT NULL COMMENT '人员姓名（冗余）',
  certificate_type VARCHAR(32) NOT NULL COMMENT '证件类型（BUILDER=建造师/SAFETY=安全员/QUALITY=质量员/TECH=技术负责人等）',
  binding_role VARCHAR(32) NULL COMMENT '投标拟派角色（PM=项目经理/TECH_LEAD=技术负责人/SAFETY_OFFICER=安全员等）',
  status VARCHAR(16) NOT NULL DEFAULT 'LOCKED' COMMENT '绑定状态（LOCKED=锁定/RELEASED=已解锁）',
  released_at DATETIME NULL COMMENT '解锁时间',
  released_reason VARCHAR(200) NULL COMMENT '解锁原因（开标完毕/中标释放/落标释放）',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户',
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  deleted INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tpb_active (person_certificate_id, status, deleted, tenant_id),
  KEY idx_tpb_register (register_id),
  KEY idx_tpb_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='投标押证绑定（TI-2 一证多投排他锁定）';
